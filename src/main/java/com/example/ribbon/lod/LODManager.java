/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod;

import com.example.ribbon.lod.data.LODChunk;
import com.example.ribbon.lod.io.McaRegionReader;
import com.example.ribbon.lod.mesh.GreedyMesher;
import com.example.ribbon.lod.mesh.LODMesh;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.ribbon.compat.ChunkPosCompat;

/**
 * 远景 LOD 主调度器：后台线程池分发、生命周期管理、主线程消费。
 *
 * <h2>数据流</h2>
 * <pre>
 *   [后台池] McaRegionReader → LODChunk → GreedyMesher → LODMesh(READY)
 *        │  ConcurrentLinkedQueue.offer(mesh 引用)   ← 多生产者安全
 *        ▼
 *   [渲染主线程] poll → 校验版本 → 交给 LODRenderEventHandler 提交
 * </pre>
 *
 * <h2>红线遵守</h2>
 * <ul>
 *   <li>后台线程<b>绝不</b> new {@code LevelChunk}，只读 .mca 的纯 NBT 高度图；</li>
 *   <li>后台线程<b>绝不</b>碰 OpenGL —— 只写 {@code DirectByteBuffer}；</li>
 *   <li>后台线程<b>绝不</b>参与原版 tick / 光照 / 实体逻辑。</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>{@code LODChunk} 不可变、{@code LODMesh} 通过状态机 + 版本戳控制读写时序：
 * 后台线程写完后 {@code markReady}，主线程只在 {@code isReady(version)} 时才消费，
 * 消费后 {@code recycle} 归还。</p>
 *
 * <p><b>重要</b>：后台线程池有多个 worker 线程并发产出 mesh，因此「后台 → 主线程」
 * 的传递队列<b>必须是多生产者安全的</b>（{@link ConcurrentLinkedQueue}），
 * <b>绝不能</b>用 SPSC 的 {@code LockFreeQueue}——它只允许单一生产者，多线程
 * 并发 {@code enqueue} 会竞态损坏游标/槽位，导致 mesh 引用错乱或丢失，
 * 表现为渲染线程读到已 {@code recycle()} 的清零 buffer（顶点坐标变成
 * 2.75E-43 之类的垃圾浮点），LOD 永远画不出来。</p>
 */
public final class LODManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/LOD");
    private static final LODManager INSTANCE = new LODManager();

    /** 已就绪网格的硬上限：超过即停止接收新网格，防止无界累积拖垮渲染。
     *  <p>LOD 覆盖范围 = innerRadius + radius。实测原版渲染距离可达 31 区块
     *  （innerRadius=31），加 radius=8 → 外半径 39 区块，环带约 π×(39²-31²)≈1760 区块。
     *  若渲染距离更大（32 区块），外半径 40，环带约 2000+ 区块。旧值 2048 会在此
     *  场景下撞上限、导致环带边缘区块缺失（"剩余空缺"根因之一）。取 4096 保证
     *  覆盖完整（4096×32KB=128MB 直接内存上限，远景多为空气、实际常驻远低于此）。</p> */
    private static final int MAX_READY_MESHES = 4096;

    /** 在途构建任务上限：防止后台线程疯狂生成 mesh 导致队列积压。
     *  <p>环带约 2000 区块，1024 在途保证能一次性消化完整个环带而不需要多轮请求，
     *  加快首次填满速度（减少"远景逐渐出现"的等待感）。</p> */
    private static final int MAX_IN_FLIGHT = 1024;

    /** 后台工作线程池。 */
    private volatile ExecutorService executor;

    /** 后台 → 主线程 的传递队列（生产者 = 后台池多线程，消费者 = 渲染主线程）。 */
    private final Queue<LODMesh> readyQueue = new ConcurrentLinkedQueue<>();

    /** 已就绪的网格缓存：chunkPos(long) → LODMesh（主线程消费用）。 */
    private final ConcurrentHashMap<Long, LODMesh> readyMeshes = new ConcurrentHashMap<>(512);

    /** 在途任务去重：chunkPos(long) → 占位。 */
    private final ConcurrentHashMap<Long, Boolean> inFlight = new ConcurrentHashMap<>(128);

    /**
     * 读取失败区块的冷却表：chunkPos(long) → 失败时间戳(ms)。
     * 存档损坏的区块（stream truncated / external chunk 缺失 / negative size）反复
     * 重试只会刷屏且永远失败（每 5 tick 一批完全相同），既浪费后台线程又让 LOD
     * 环带在这些位置永久留洞。这里记录失败区块，冷却期内（默认 30s）不再重试，
     * 之后才允许重新尝试（存档可能已被原版系统修复）。
     */
    private final ConcurrentHashMap<Long, Long> failedCooldown = new ConcurrentHashMap<>(64);

    /** 失败区块重试冷却（毫秒）。 */
    private static final long FAILED_RETRY_COOLDOWN_MS = 30_000L;

    /** 数据读取器（持有 RegionFile 缓存）。 */
    private volatile McaRegionReader reader;

    private volatile boolean running = false;

    /** GreedyMesher 构建耗时累计（纳秒→已转毫秒累加），用于诊断后台瓶颈。 */
    private final java.util.concurrent.atomic.AtomicLong buildAccumulator = new java.util.concurrent.atomic.AtomicLong();

    /** 已构建的区块总数（诊断用）。 */
    private final java.util.concurrent.atomic.AtomicLong builtCount = new java.util.concurrent.atomic.AtomicLong();

    /** 上一次心跳打印耗时统计时的 builtCount 快照。 */
    private volatile long lastBuiltSnapshot = 0;

    /** GC 时间快照（纳秒累计），用于从 wall-clock 耗时中扣除 GC 暂停。 */
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();

    private LODManager() {
    }

    public static LODManager getInstance() {
        return INSTANCE;
    }

    /**
     * 读取当前所有 GC 收集器的累计耗时（纳秒）。
     * 用于在 build 计时区间内扣除 GC 暂停，避免心跳 avgMs 被 Full GC 尖峰污染。
     */
    private static long gcTimeNanos() {
        long total = 0L;
        for (GarbageCollectorMXBean gc : GC_BEANS) {
            long c = gc.getCollectionTime(); // 毫秒
            if (c > 0) total += c * 1_000_000L;
        }
        return total;
    }

    // ==================== 生命周期 ====================

    /**
     * 初始化后台线程池与数据读取器。
     * 应在客户端进入世界时调用（主线程）。
     *
     * @param regionDir   世界保存目录下的 "region" 目录
     * @param storageInfo 区域存储元信息
     * @param minBuildY   世界最小建筑高度
     */
    public synchronized void start(java.nio.file.Path regionDir,
                                   net.minecraft.world.level.chunk.storage.RegionStorageInfo storageInfo,
                                   int minBuildY) {
        start(regionDir, storageInfo, minBuildY, null);
    }

    /**
     * 带 {@code RegistryAccess} 的启动入口：供主线程传入世界 registry 只读快照，
     * 用于后台解码 .mca 的 biomes 时解析群系染色值（{@code RegistryAccess} 的 Frozen
     * 快照线程安全，后台线程只读不写）。传 {@code null} 则跳过群系染色（中性兜底）。
     */
    public synchronized void start(java.nio.file.Path regionDir,
                                   net.minecraft.world.level.chunk.storage.RegionStorageInfo storageInfo,
                                   int minBuildY,
                                   net.minecraft.core.RegistryAccess registryAccess) {
        if (running) return;

        if (reader != null) {
            reader.close();
        }
        reader = new McaRegionReader(regionDir, storageInfo, minBuildY, registryAccess);

        int configured = com.example.ribbon.Config.getLodWorkerThreads();
        int threads;
        if (configured > 0) {
            threads = configured;
        } else {
            threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        }
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "Ribbon-LOD-Worker-" + workerIdx.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 2);
            return t;
        };
        executor = Executors.newFixedThreadPool(threads, factory);
        running = true;
        LOGGER.info("[Ribbon/LOD] LOD 调度器已启动，线程数={}", threads);
    }

    /** 关闭线程池、清空缓存、释放 RegionFile 句柄。世界卸载时调用。 */
    public synchronized void stop() {
        running = false;
        ExecutorService ex = this.executor;
        this.executor = null;
        readyMeshes.clear();
        inFlight.clear();
        failedCooldown.clear();
        // 排空队列
        LODMesh m;
        while ((m = readyQueue.poll()) != null) {
            m.recycle();
        }
        if (ex != null) {
            ex.shutdown();
            try {
                if (!ex.awaitTermination(2, TimeUnit.SECONDS)) {
                    ex.shutdownNow();
                }
            } catch (InterruptedException e) {
                ex.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        com.example.ribbon.lod.mesh.LODMeshPool.getInstance().clear();
        LOGGER.info("[Ribbon/LOD] LOD 调度器已关闭");
    }

    /**
     * 返回本次调用周期内 GreedyMesher 的平均单区块构建耗时（毫秒），并重置累计。
     * 渲染端心跳日志调用，用于判断后台算法是否超 10ms（超则应简化）。
     *
     * @return 平均耗时 ms；本周期无构建则返回 -1
     */
    public double drainBuildAvgMs() {
        long total = builtCount.get();
        if (total == lastBuiltSnapshot) return -1;
        long delta = total - lastBuiltSnapshot;
        lastBuiltSnapshot = total;
        long accMs = buildAccumulator.getAndSet(0);
        return (delta > 0) ? (double) accMs / delta : 0.0;
    }

    private static final AtomicInteger workerIdx = new AtomicInteger();

    // ==================== 异步提交（主线程 → 后台） ====================

    /**
     * 提交一组区块的 LOD 构建任务（主线程调用，通常由渲染事件/玩家移动触发）。
     *
     * <p>只对"尚未在途、尚未就绪"的区块提交，避免重复构建。受 {@link #MAX_IN_FLIGHT}
     * 与 {@link #MAX_READY_MESHES} 双重硬上限约束，杜绝后台线程疯狂生成 mesh。</p>
     */
    public void requestLOD(Set<ChunkPos> wanted) {
        if (!running || wanted == null) return;
        ExecutorService ex = this.executor;
        McaRegionReader rd = this.reader;
        if (ex == null || rd == null || ex.isShutdown()) return;

        for (ChunkPos pos : wanted) {
            // 硬上限：在途任务已满则本轮不再新增，避免积压。
            if (inFlight.size() >= MAX_IN_FLIGHT) break;

            long key = pos.toLong();
            if (readyMeshes.containsKey(key)) continue;          // 已就绪
            if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) continue; // 在途
            // 读取失败冷却：损坏区块不反复重试（见 failedCooldown 说明）。
            Long lastFail = failedCooldown.get(key);
            if (lastFail != null && (System.currentTimeMillis() - lastFail) < FAILED_RETRY_COOLDOWN_MS) {
                continue; // 冷却期内，跳过这次请求
            }

            // 【关键修复·不再用 readyMeshes.size() 硬 break】
            // 之前「ready=4096 满后 break」导致：玩家移动后渲染线程 evictFarAway
            // 淘汰旧网格腾位，但请求端（tick 阶段，早于渲染帧）看到缓存仍满、
            // 直接 break，新区域永远进不了构建队列 → inFlight 恒 0、buildAvgMs
            // 恒 n/a（"转化罢工"的根因）。readyMeshes 的上限交给渲染线程的
            // evictFarAway（每帧）+ drainReady 的满上限丢弃来维护，请求端只受
            // inFlight 约束，保证环带能随玩家移动持续构建更新。

            final McaRegionReader readerSnapshot = rd;
            ex.execute(() -> {
                try {
                    LODChunk chunk = readerSnapshot.readLODChunk(pos);
                    if (chunk == null) {
                        // 读取失败/区块不存在：记录失败时间戳，冷却期内不再重试，
                        // 避免存档损坏区块反复读盘刷屏 + 后台线程空转。
                        failedCooldown.put(key, System.currentTimeMillis());
                        return; // 区块不存在/高度图为空/读取失败，静默跳过
                    }
                    // 读取成功：清除失败记录，允许后续正常重读。
                    failedCooldown.remove(key);
                    // 【对象池】复用回收的 DirectByteBuffer，避免每区块一次 allocateDirect。
                    LODMesh mesh = com.example.ribbon.lod.mesh.LODMeshPool.getInstance().acquire();
                    long t0 = System.nanoTime();
                    long gc0 = gcTimeNanos();
                    GreedyMesher.build(chunk, mesh, 0);
                    long gc1 = gcTimeNanos();
                    long wallMs = (System.nanoTime() - t0) / 1_000_000L;
                    // 扣除本次构建区间内发生的 GC 暂停，得到纯 CPU 构建耗时，
                    // 避免偶发的 Full GC 尖峰误导心跳日志 avgMs 调优判断。
                    long gcMs = (gc1 - gc0) / 1_000_000L;
                    long buildMs = Math.max(0L, wallMs - gcMs);
                    buildAccumulator.addAndGet(buildMs);
                    builtCount.incrementAndGet();
                    // 入队：多个后台线程并发 offer（ConcurrentLinkedQueue 多生产者安全）。
                    // 不再有"队列满丢弃"分支——无界队列，由 MAX_READY_MESHES 在消费侧兜底。
                    readyQueue.offer(mesh);
                } catch (Throwable t) {
                    LOGGER.debug("[Ribbon/LOD] 构建区块 {} 失败：{}", pos, t.toString());
                } finally {
                    inFlight.remove(key);
                }
            });
        }
    }

    // ==================== 主线程消费 ====================

    /**
     * 主线程每帧收割就绪网格（渲染主线程调用）。
     * 把队列里的 READY 网格搬到 readyMeshes 缓存。受 {@link #MAX_READY_MESHES} 上限约束。
     */
    public void drainReady() {
        int drained = 0;
        while (drained < 1024) {
            LODMesh mesh = readyQueue.poll();
            if (mesh == null) break;
            drained++;
            // 硬上限：缓存已满则丢弃新到的网格并回收，避免无界增长。
            if (readyMeshes.size() >= MAX_READY_MESHES) {
                mesh.recycle();
                continue;
            }
            readyMeshes.put(key(mesh), mesh);
        }
    }

    /**
     * 返回当前所有就绪网格的快照列表（渲染主线程调用）。
     */
    public List<LODMesh> snapshotReady() {
        return new ArrayList<>(readyMeshes.values());
    }

    /**
     * 返回最多 {@code max} 个就绪网格（渲染主线程调用）。
     * 渲染层用它限制每帧提交的 mesh 总数，避免顶点雪崩。
     */
    public List<LODMesh> snapshotReady(int max) {
        List<LODMesh> out = new ArrayList<>(Math.min(max, readyMeshes.size()));
        for (LODMesh mesh : readyMeshes.values()) {
            if (out.size() >= max) break;
            out.add(mesh);
        }
        return out;
    }

    /**
     * 标记指定区块的网格过期（数据源变化时调用），随后会被重新构建替换。
     */
    public void invalidate(ChunkPos pos) {
        LODMesh mesh = readyMeshes.remove(pos.toLong());
        if (mesh != null) {
            mesh.markStale();
            mesh.recycle();
        }
    }

    /**
     * 淘汰距离玩家中心超过 {@code radiusChunks} 区块的已就绪网格，释放内存并
     * 避免远景无限累积。玩家移动后旧环带的网格被回收，新环带重新构建。
     */
    public void evictFarAway(int centerChunkX, int centerChunkZ, int radiusChunks) {
        if (radiusChunks <= 0) return;
        // 【圆形判定对齐】与原版 Embeddium 的 RenderSectionDistanceFilter.DEFAULT
        // （dx²+dz² < maxDistance²）一致。之前用 Chebyshev 方形判定会在角落与圆形
        // 渲染范围错配，导致夹缝或重叠。
        int rSq = radiusChunks * radiusChunks;
        readyMeshes.entrySet().removeIf(e -> {
            LODMesh m = e.getValue();
            int dx = m.chunkX() - centerChunkX;
            int dz = m.chunkZ() - centerChunkZ;
            if (dx * dx + dz * dz > rSq) {
                m.recycle();
                return true;
            }
            return false;
        });
    }

    /**
     * 淘汰玩家中心内半径（原版渲染距离内）的网格。这些网格是玩家移动后遗留的
     * 「曾经在外圈、现已进入原版接管区」的 LOD，应让位给原版区块（渲染端已过滤，
     * 这里回收其缓存，避免占用 readyMeshes 上限）。与 {@link #evictFarAway} 配对，
     * 分别淘汰外圈之外与内圈之内的网格，中间环带才是合法 LOD。
     */
    public void evictInner(int centerChunkX, int centerChunkZ, int innerRadiusChunks) {
        if (innerRadiusChunks <= 0) return;
        // 【圆形判定对齐】与原版 Embeddium 的欧氏距离渲染范围一致，避免角落夹缝。
        int rSq = innerRadiusChunks * innerRadiusChunks;
        readyMeshes.entrySet().removeIf(e -> {
            LODMesh m = e.getValue();
            int dx = m.chunkX() - centerChunkX;
            int dz = m.chunkZ() - centerChunkZ;
            if (dx * dx + dz * dz <= rSq) {
                m.recycle();
                return true;
            }
            return false;
        });
    }

    public boolean isRunning() { return running; }
    public int readyCount() { return readyMeshes.size(); }
    public int inFlightCount() { return inFlight.size(); }

    private static long key(LODMesh mesh) {
        return ChunkPosCompat.asLong(mesh.chunkX(), mesh.chunkZ());
    }
}
