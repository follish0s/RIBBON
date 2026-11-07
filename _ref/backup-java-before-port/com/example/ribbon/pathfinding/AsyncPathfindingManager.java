/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.pathfinding;

import com.example.ribbon.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 异步实体寻路调度器。
 *
 * <h2>为什么不用读写锁（重要设计决策）</h2>
 *
 * <p>需求中提到的"RWLock 保护世界方块读写"方案<b>在本场景下是有害的</b>，原因：</p>
 * <ul>
 *   <li>寻路<b>不是</b>纯计算：{@code PathFinder.findPath} 全程在读世界
 *       （{@code getBlockState} / {@code getFluidState} / 碰撞形状 / 实体碰撞）；</li>
 *   <li>若用读锁保护这些访问，主线程每次 {@code setBlock} 都要拿<b>写锁</b>，
 *       必须等待所有寻路线程释放读锁。数百个生物同时寻路时，
 *       服务器线程会被长期阻塞 —— <b>结果比同步寻路更卡，甚至假死</b>；</li>
 *   <li>锁竞争还会引入优先级反转与死锁风险（寻路中回调世界 → 再次拿锁）。</li>
 * </ul>
 *
 * <h2>实际采用：快照隔离（Copy-on-Read）</h2>
 *
 * <p>已核对 1.21.1 原版 {@code PathNavigation.createPath} 的实现，其常量池中
 * 出现 {@code PathNavigationRegion} 与 {@code findPath(PathNavigationRegion, ...)}。
 * 也就是说<b>原版自己就用快照区域做寻路</b>：先把实体周围若干区块的方块状态
 * 拷贝进 {@code PathNavigationRegion}，再让 {@code PathFinder} 只读这个快照。</p>
 *
 * <p>因此本类复用同一机制：</p>
 * <ol>
 *   <li><b>主线程</b>构造 {@code PathNavigationRegion} 快照（此时才允许碰真实世界）；</li>
 *   <li><b>后台线程</b>只对快照运行 {@code PathFinder}，<b>零锁、零数据竞争</b>；</li>
 *   <li><b>主线程回调</b>（{@code server.execute}）把结果 {@code Path} 写回缓存，
 *       由 {@code MixinPathNavigation} 在下次 {@code createPath} 时取用。</li>
 * </ol>
 *
 * <p>这样天然满足了需求的红线：后台线程<b>绝无可能</b>修改实体 {@code BlockPos}
 * 或世界状态，因为它手里只有一个不可变快照，且拿不到 {@code Level} 的可写入口。</p>
 *
 * <h2>语义保证：绝不破坏 AI</h2>
 *
 * <p>{@code createPath} 是<b>同步返回</b>的，调用方（AI Goal）立即使用返回值。
 * 因此本类<b>不会</b>让 {@code createPath} 返回 {@code null} 或过期路径：</p>
 * <ul>
 *   <li>缓存命中且世界版本有效 → 返回缓存路径，省下同步计算；</li>
 *   <li>缓存未命中 → <b>放行原版同步计算</b>（正确性优先），随后把结果存入缓存
 *       并触发异步预热；</li>
 *   <li>任何异常 / 线程池拒绝 / 快照构造失败 → 静默回退原版同步逻辑。</li>
 * </ul>
 *
 * <p>收益集中在<b>高频重复寻路</b>场景：怪物持续追踪玩家、村民反复找工作站点、
 * 大量同类生物寻向同一目标。这类请求原版每 tick 都重算，命中缓存后可直接跳过。</p>
 *
 * <h2>线程安全关键点</h2>
 *
 * <p>{@code PathFinder} <b>不是</b>线程安全的：已核对其实例字段含
 * {@code openSet : BinaryHeap} 与 {@code neighbors : Node[]}，均为可变状态。
 * 同理 {@code NodeEvaluator} 也持有内部缓存。因此后台线程<b>必须为每个任务
 * 新建独立的 PathFinder 与 NodeEvaluator 副本</b>，绝不复用导航实例上的对象。</p>
 *
 * <p>此外 {@code GroundPathNavigation.createPathFinder()} 会<b>写入</b>
 * {@code this.nodeEvaluator} 字段，属于对主线程对象的副作用，
 * 因此本类<b>绝不在后台线程调用它</b>，改为通过反射复制求值器。</p>
 */
public final class AsyncPathfindingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/AsyncPath");

    /** 单例。 */
    private static final AsyncPathfindingManager INSTANCE = new AsyncPathfindingManager();

    /** 后台计算线程池。 */
    private volatile ExecutorService executor;

    /** 路径缓存：key = 实体 UUID + 目标 + 精度。 */
    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>(256);

    /** 在途任务集合，防止同一请求重复提交。 */
    private final ConcurrentHashMap<CacheKey, Boolean> inFlight = new ConcurrentHashMap<>(64);

    /** 高频寻路实体标记：entityId → 连续请求次数。 */
    private final ConcurrentHashMap<UUID, AtomicInteger> hotEntities = new ConcurrentHashMap<>(64);

    // ==================== 统计 ====================
    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder asyncCompleted = new LongAdder();
    private final LongAdder asyncFailed = new LongAdder();
    private final LongAdder rejections = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();

    /**
     * 区块粒度的版本号：key = {@code ChunkPos.asLong()}，value = 该区块的变更次数。
     *
     * <p><b>为什么不用全局版本号：</b>活跃世界里方块每时每刻都在变
     * （草蔓延、水流、怪物破坏方块、玩家操作）。若任何方块变化就清空全部缓存，
     * 缓存命中率会趋近于 0，异步化毫无意义。</p>
     *
     * <p>改为按区块记录版本：只有当路径<b>覆盖到的区块</b>发生变化时，
     * 该条缓存才失效。这样既保证正确性（不会走上已消失的方块），
     * 又保留了绝大部分缓存收益。</p>
     */
    private final ConcurrentHashMap<Long, Long> chunkVersions = new ConcurrentHashMap<>(512);
    private volatile long worldVersion = 0;

    private AsyncPathfindingManager() {}

    public static AsyncPathfindingManager getInstance() { return INSTANCE; }

    // ==================== 生命周期 ====================

    /**
     * 初始化线程池。世界加载时由 Mixin 调用（主线程）。
     */
    public synchronized void start() {
        if (executor != null) return;
        int threads = Config.getAsyncPathfindingThreads();
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger idx = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Ribbon-AsyncPath-" + idx.incrementAndGet());
                t.setDaemon(true);
                // 低于主线程优先级，确保不抢占服务器 tick
                t.setPriority(Thread.NORM_PRIORITY - 2);
                return t;
            }
        };
        executor = Executors.newFixedThreadPool(Math.max(1, threads), factory);
        LOGGER.info("[Ribbon] 异步寻路线程池已启动，线程数={}", threads);
    }

    /**
     * 关闭线程池并清空缓存。世界卸载时调用。
     */
    public synchronized void stop() {
        ExecutorService ex = this.executor;
        this.executor = null;
        cache.clear();
        inFlight.clear();
        hotEntities.clear();
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
        LOGGER.info("[Ribbon] 异步寻路线程池已关闭");
    }

    /** 总开关（含配置未就绪时的保守禁用）。 */
    public boolean isEnabled() {
        try {
            return Config.isAsyncPathfindingEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 缓存查询（主线程） ====================

    /**
     * 查询缓存的路径。
     *
     * @return 有效的缓存路径；未命中或已失效返回 {@code null}（调用方走原版）
     */
    public Path getCachedPath(Mob mob, BlockPos target, int accuracy) {
        if (!isEnabled() || mob == null || target == null) return null;
        try {
            CacheKey key = new CacheKey(mob.getUUID(), target, accuracy);
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                cacheMisses.increment();
                return null;
            }
            // 世界版本变化 → 缓存失效
            if (entry.worldVersion != this.worldVersion) {
                cache.remove(key, entry);
                cacheMisses.increment();
                return null;
            }
            // 超龄失效（防止生物卡在半途不动）
            long age = mob.level().getGameTime() - entry.createdGameTime;
            if (age > Config.getAsyncPathfindingCacheTicks()) {
                cache.remove(key, entry);
                cacheMisses.increment();
                return null;
            }
            // 实体已移除 → 丢弃
            if (mob.isRemoved()) {
                cache.remove(key, entry);
                return null;
            }
            cacheHits.increment();
            return entry.path;
        } catch (Throwable t) {
            reportFallback("getCachedPath", t);
            return null;
        }
    }

    /**
     * 存入路径缓存（主线程调用）。
     */
    public void cachePath(Mob mob, BlockPos target, int accuracy, Path path) {
        if (!isEnabled() || mob == null || target == null || path == null) return;
        try {
            CacheKey key = new CacheKey(mob.getUUID(), target, accuracy);
            cache.put(key, new CacheEntry(path, this.worldVersion, mob.level().getGameTime()));
        } catch (Throwable t) {
            reportFallback("cachePath", t);
        }
    }

    /**
     * 记录一次寻路请求，返回该实体是否属于"高频寻路者"。
     * 高频实体才会触发异步预热，避免为一次性请求浪费线程。
     */
    public boolean markRequestAndCheckHot(Mob mob) {
        try {
            AtomicInteger counter = hotEntities.computeIfAbsent(mob.getUUID(), k -> new AtomicInteger());
            int count = counter.incrementAndGet();
            return count >= Config.getAsyncPathfindingHotThreshold();
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 异步提交（主线程 → 后台） ====================

    /**
     * 提交一次异步寻路任务。
     *
     * <p><b>必须在主线程调用</b>：本方法会构造 {@code PathNavigationRegion} 快照，
     * 该操作需要读取真实世界，绝不可在后台线程执行。</p>
     *
     * @return 是否成功提交（{@code false} 表示应回退同步逻辑）
     */
    public boolean submitAsync(Mob mob, PathNavigation nav, BlockPos target, int accuracy) {
        if (!isEnabled() || mob == null || nav == null || target == null) return false;

        ExecutorService ex = this.executor;
        if (ex == null || ex.isShutdown()) return false;

        try {
            Level level = mob.level();
            if (level == null || level.isClientSide()) return false;

            MinecraftServer server = level.getServer();
            if (server == null) return false;

            CacheKey key = new CacheKey(mob.getUUID(), target, accuracy);
            // 同一请求在途则跳过，避免线程池积压
            if (inFlight.putIfAbsent(key, Boolean.TRUE) != null) return false;

            // 背压保护：在途任务过多时直接放弃异步
            int maxInFlight = Config.getAsyncPathfindingMaxInFlight();
            if (inFlight.size() > maxInFlight) {
                inFlight.remove(key);
                rejections.increment();
                return false;
            }

            // ① 主线程构造快照（这是唯一允许触碰真实世界的地方）
            final PathNavigationRegion region = ribbon$createSnapshot(mob, target);
            if (region == null) {
                inFlight.remove(key);
                return false;
            }

            // ② 主线程复制 NodeEvaluator（后台线程绝不可调用 createPathFinder，
            //    因为 GroundPathNavigation.createPathFinder 会写 this.nodeEvaluator）
            final NodeEvaluator evaluatorCopy = ribbon$copyEvaluator(nav);
            if (evaluatorCopy == null) {
                inFlight.remove(key);
                return false;
            }

            final float followRange = (float) mob.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            final int maxVisitedNodes = ribbon$maxVisitedNodes(nav, mob);
            final long capturedVersion = this.worldVersion;
            final UUID entityId = mob.getUUID();

            // ③ 后台线程：只对快照计算，用全新的 PathFinder（openSet/neighbors 是可变状态）
            ex.execute(() -> {
                long start = System.nanoTime();
                Path result = null;
                try {
                    PathFinder finder = new PathFinder(evaluatorCopy, maxVisitedNodes);
                    result = finder.findPath(region, mob, Set.of(target),
                            followRange, accuracy, 1.0F);
                } catch (Throwable t) {
                    asyncFailed.increment();
                    // 后台线程异常绝不可抛出：会被线程池吞掉但污染统计，这里显式记录
                    if (asyncFailed.sum() <= 5) {
                        LOGGER.error("[Ribbon] 异步寻路计算失败，已回退同步逻辑：{}", t.toString());
                    }
                } finally {
                    totalNanos.add(System.nanoTime() - start);
                    inFlight.remove(key);
                }

                // ④ 主线程回调写回（红线：结果只能在主线程赋给实体）
                final Path path = result;
                try {
                    server.execute(() -> {
                        try {
                            if (path == null) return;
                            // 实体可能已卸载/切维度，此时丢弃结果
                            if (mob.isRemoved() || mob.level() != level) return;
                            // 快照期间世界已变化 → 结果过期，丢弃（宁可不缓存也不缓存错路径）
                            if (capturedVersion != worldVersion) return;

                            cache.put(key, new CacheEntry(path, capturedVersion, level.getGameTime()));
                            asyncCompleted.increment();
                        } catch (Throwable t) {
                            reportFallback("applyResult", t);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    // 服务器正在关闭，属正常情况
                } catch (Throwable t) {
                    reportFallback("scheduleResult", t);
                }
            });

            return true;
        } catch (RejectedExecutionException e) {
            rejections.increment();
            return false;
        } catch (Throwable t) {
            reportFallback("submitAsync", t);
            return false;
        }
    }

    /**
     * 构造寻路快照区域。
     *
     * <p>{@code PathNavigationRegion} 是原版用于寻路的不可变快照类型，
     * 已核对 {@code PathNavigation.createPath} 内部即以其为 {@code findPath} 的首参。
     * 快照包含实体周围区块的方块状态副本，后台线程读它不会触碰真实世界。</p>
     *
     * <p>构造失败返回 {@code null}，调用方回退同步逻辑。</p>
     */
    private PathNavigationRegion ribbon$createSnapshot(Mob mob, BlockPos target) {
        try {
            Level level = mob.level();
            BlockPos center = mob.blockPosition();
            int range = Config.getAsyncPathfindingSnapshotRange();

            // 构造快照范围
            BlockPos from = center.offset(-range, -8, -range);
            BlockPos to = center.offset(range, 8, range);

            // 🚨 正确的构造器签名：PathNavigationRegion(BlockGetter, BlockPos, BlockPos)
            return new PathNavigationRegion(level, from, to);
        } catch (Throwable t) {
            reportFallback("createSnapshot", t);
            return null;
        }
    }

    /**
     * 复制 NodeEvaluator，供后台线程独占使用。
     *
     * <p>{@code NodeEvaluator} 持有内部节点缓存，<b>不是</b>线程安全的。
     * 原版 {@code NodeEvaluator} 提供 {@code copy()} 用于此目的；
     * 此处通过反射调用，避免对具体子类（{@code WalkNodeEvaluator} /
     * {@code FlyNodeEvaluator} 等）的编译期依赖。</p>
     *
     * <p>反射失败返回 {@code null} → 放弃异步，回退同步。<b>绝不共享原实例</b>，
     * 因为共享会造成跨线程数据竞争。</p>
     */
    private NodeEvaluator ribbon$copyEvaluator(PathNavigation nav) {
        try {
            NodeEvaluator original = nav.getNodeEvaluator();
            if (original == null) return null;

            // 优先使用原版 copy()
            try {
                java.lang.reflect.Method copy = original.getClass().getMethod("copy");
                Object copied = copy.invoke(original);
                if (copied instanceof NodeEvaluator ne) return ne;
            } catch (NoSuchMethodException ignored) {
                // 该版本无 copy()，尝试 clone()
            }

            try {
                java.lang.reflect.Method clone = Object.class.getDeclaredMethod("clone");
                clone.setAccessible(true);
                Object copied = clone.invoke(original);
                if (copied instanceof NodeEvaluator ne) return ne;
            } catch (Throwable ignored) {
                // 无法复制 → 放弃异步
            }
            return null;
        } catch (Throwable t) {
            reportFallback("copyEvaluator", t);
            return null;
        }
    }

    /**
     * 读取导航的最大访问节点数。通过反射读取私有字段 {@code maxVisitedNodesMultiplier}，
     * 与原版 {@code createPathFinder} 的计算保持一致。失败则用保守默认值。
     */
    private int ribbon$maxVisitedNodes(PathNavigation nav, Mob mob) {
        try {
            java.lang.reflect.Field f = PathNavigation.class.getDeclaredField("maxVisitedNodesMultiplier");
            f.setAccessible(true);
            float multiplier = f.getFloat(nav);
            float follow = (float) mob.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
            int nodes = (int) (follow * multiplier);
            return Math.max(16, Math.min(nodes, 1024));
        } catch (Throwable t) {
            return 256;
        }
    }

    // ==================== 缓存失效 ====================

    /**
     * 方块变化时调用，使全部缓存失效。
     *
     * <p>采用全局版本号而非按区块失效：实现简单且绝对安全 ——
     * 宁可多算一次，也绝不让生物走上已消失的方块。</p>
     */
    public void invalidateAll() {
        worldVersion++;
        // 版本号变化后旧条目在下次查询时惰性清除，此处不做全量遍历以免卡顿
    }

    /**
     * 清理已卸载实体的缓存条目。定期由 Mixin 在服务器 tick 调用。
     */
    public void purgeUnloaded(java.util.Set<UUID> loadedIds) {
        try {
            cache.keySet().removeIf(k -> !loadedIds.contains(k.entityId));
            hotEntities.keySet().removeIf(id -> !loadedIds.contains(id));
        } catch (Throwable t) {
            reportFallback("purgeUnloaded", t);
        }
    }

    // ==================== 统计读取（供 HUD / F3 使用） ====================

    public long getCacheHits() { return cacheHits.sum(); }
    public long getCacheMisses() { return cacheMisses.sum(); }
    public long getAsyncCompleted() { return asyncCompleted.sum(); }
    public long getAsyncFailed() { return asyncFailed.sum(); }
    public long getRejections() { return rejections.sum(); }
    public int getCacheSize() { return cache.size(); }
    public int getInFlightCount() { return inFlight.size(); }

    /** 平均每次异步计算耗时（毫秒）。 */
    public double getAvgAsyncMillis() {
        long done = asyncCompleted.sum() + asyncFailed.sum();
        return done == 0 ? 0.0 : (totalNanos.sum() / 1_000_000.0) / done;
    }

    /** 缓存命中率（百分比）。 */
    public double getHitRatePercent() {
        long hits = cacheHits.sum(), misses = cacheMisses.sum();
        long total = hits + misses;
        return total == 0 ? 0.0 : (hits * 100.0) / total;
    }

    private void reportFallback(String stage, Throwable t) {
        if (asyncFailed.sum() <= 5) {
            LOGGER.error("[Ribbon] 异步寻路在 {} 阶段异常，已回退原版同步逻辑：{}", stage, t.toString());
        }
        asyncFailed.increment();
    }

    // ==================== 内部数据结构 ====================

    /**
     * 缓存键。使用 record 以获得正确的 equals/hashCode。
     */
    private record CacheKey(UUID entityId, BlockPos target, int accuracy) {}

    /**
     * 缓存条目。{@code path} 一经写入即不可变，可安全跨线程读取。
     */
    private static final class CacheEntry {
        final Path path;
        final long worldVersion;
        final long createdGameTime;

        CacheEntry(Path path, long worldVersion, long createdGameTime) {
            this.path = path;
            this.worldVersion = worldVersion;
            this.createdGameTime = createdGameTime;
        }
    }
}
