/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 中间态直接内存缓冲：CPU 侧产出的 LOD 几何数据，等待渲染线程消费。
 *
 * <h2>为什么用 DirectByteBuffer</h2>
 * <p>使用堆外直接内存（而非堆内 {@code byte[]}），好处有三：</p>
 * <ol>
 *   <li>可被 SPSC 无锁队列（{@code LockFreeQueue}）零拷贝传递 —— 队列里搬的只是引用；</li>
 *   <li>最终进入原版 {@code BufferBuilder} 时，减少一次"堆 → 直接内存"的拷贝；</li>
 *   <li>不进入 GC 堆，避免远景大量几何数据引发 GC 压力（与对象池目标一致）。</li>
 * </ol>
 *
 * <h2>顶点打包格式（严格对齐 {@code DefaultVertexFormat.BLOCK}）</h2>
 * <p>每个顶点 <b>32 字节</b>，字段顺序、类型、字节数与原版方块顶点布局一致：</p>
 * <pre>
 *   float x, y, z        (Position,  12 字节, Element.POSITION)
 *   int   color          (Color,      4 字节, Element.COLOR, RGBA 归一化)
 *   float u, v           (UV0,        8 字节, Element.UV0)
 *   short u2, v2         (UV2/Light,  4 字节, Element.UV2, 光照贴图坐标)
 *   byte  nx, ny, nz     (Normal,     3 字节, Element.NORMAL, 有符号 byte 映射 [-1,1])
 *   byte  padding        (Normal 的 4 字节对齐补齐,  1 字节)
 * </pre>
 * <p>合计 12 + 4 + 8 + 4 + 3 + 1 = <b>32 字节</b>，与 {@code DefaultVertexFormat.BLOCK}
 * 的步长（{@code getVertexSize()==32}）完全一致。这是 Iris 的 GBuffer/Shadow Pass
 * 唯一能识别的标准地形顶点格式——Iris 只会对 {@code BLOCK} 格式的顶点正确读取
 * UV2（光照贴图）与 Normal，用于光影下的光照计算。</p>
 *
 * <p>渲染线程消费时，把这 32 字节逐字段读回，通过原版 {@code BufferBuilder}
 * （{@code VertexFormat.Mode.QUADS} + {@code DefaultVertexFormat.BLOCK}）重新写入，
 * 再交给 {@code RenderType.solid()} 提交，从而进入原版深度排序与批次合并。</p>
 */
public final class LODMesh {

    /** 每个顶点的字节数（对齐 DefaultVertexFormat.BLOCK 的 32 字节）。 */
    public static final int VERTEX_STRIDE = 32;

    /** 网格状态机。 */
    public enum State {
        EMPTY,     // 尚未构建
        BUILDING,  // 后台线程正在写入
        READY,     // 已就绪，可被渲染线程消费
        STALE      // 数据源已变，需重建
    }

    /** 顶点数据（直接内存）。 */
    private ByteBuffer vertexData;

    /** 已写入的顶点数量。 */
    private int vertexCount;

    /** 顶点容量上限（字节）。 */
    private int capacityBytes;

    /** 网格所属区块坐标。 */
    private int chunkX;
    private int chunkZ;

    /** 数据源版本戳（与 LODChunk.sourceVersion 对应）。 */
    private long sourceVersion;

    /** 网格状态（volatile，跨线程可见）。 */
    private volatile State state = State.EMPTY;

    /** 网格简化等级（决定剔除距离与过渡权重）。 */
    private int lodLevel;

    /**
     * GPU 侧顶点缓冲（渲染线程创建/上传/绘制，后台线程绝不触碰）。
     * 走自定义 RenderType + 独立 shader 的隔离渲染通道，绕开 Iris 对原版
     * RenderType 的拦截。{@code null} 表示尚未上传或已失效需重建。
     */
    private volatile com.mojang.blaze3d.vertex.VertexBuffer gpuBuffer;

    /** 上传到 GPU 时的 sourceVersion 戳（用于判断 GPU 数据是否已过期）。 */
    private volatile long gpuVersion = -1L;

    private LODMesh() {
    }

    /** 从池中获取一个网格缓冲（新分配 DirectByteBuffer）。 */
    public static LODMesh acquire(int initialCapacityBytes) {
        LODMesh mesh = new LODMesh();
        // 【关键】必须显式小端！Minecraft/LWJGL 的顶点数据一律小端字节序。
        // 不能依赖 ByteOrder.nativeOrder()——它在部分环境可能返回 BIG_ENDIAN，
        // 导致 putFloat(小端写) 与 getFloat(大端读) 不一致，顶点坐标变成
        // 7.2E-41 之类的垃圾浮点（字节序反转的典型特征）。
        mesh.vertexData = ByteBuffer.allocateDirect(initialCapacityBytes).order(ByteOrder.LITTLE_ENDIAN);
        mesh.capacityBytes = initialCapacityBytes;
        mesh.vertexCount = 0;
        mesh.state = State.BUILDING;
        return mesh;
    }

    /** 用<b>池中复用</b>的 buffer 包装一个网格（不重新分配直接内存）。 */
    public static LODMesh wrap(ByteBuffer reusedBuffer, int capacityBytes) {
        LODMesh mesh = new LODMesh();
        // 复用前强制复位游标与字节序，杜绝上一轮 flip()/写入残留导致脏顶点。
        reusedBuffer.clear();
        reusedBuffer.order(ByteOrder.LITTLE_ENDIAN);
        mesh.vertexData = reusedBuffer;
        mesh.capacityBytes = capacityBytes;
        mesh.vertexCount = 0;
        mesh.state = State.BUILDING;
        return mesh;
    }

    /** 获取写入游标（后台线程调用），确保容量足够。 */
    public ByteBuffer bufferForWrite() {
        return vertexData;
    }

    /** 确保至少能再写入 {@code additionalBytes} 字节（按字节计，非顶点数）。 */
    public void ensureCapacity(int additionalBytes) {
        int needed = vertexCount * VERTEX_STRIDE + additionalBytes;
        if (needed <= capacityBytes) return;
        // 扩容到 1.5 倍（至少覆盖 needed）
        int newCap = Math.max(needed, capacityBytes * 3 / 2);
        ByteBuffer grown = ByteBuffer.allocateDirect(newCap).order(ByteOrder.LITTLE_ENDIAN);
        // 复制已写入的字节（保持 position 语义：写满的 buffer position == 已写字节数）
        int written = vertexData.position();
        ByteBuffer src = vertexData.duplicate();
        src.rewind();
        src.limit(written);
        grown.put(src);
        vertexData = grown;
        capacityBytes = newCap;
    }

    /**
     * 追加一个顶点（后台线程调用，按 BLOCK 32 字节布局写入）。
     *
     * @param x,y,z      世界坐标
     * @param argb       方块颜色（{@code MapColor.col} 的 ARGB，alpha 恒 0xFF）
     * @param u,v        纹理 UV（世界坐标平铺）
     * @param lightU,V   光照贴图 UV（满亮 240）
     * @param nx,ny,nz   单位法线（面朝向）
     */
    public void putVertex(float x, float y, float z, int argb, float u, float v, int lightU, int lightV,
                          float nx, float ny, float nz) {
        ensureCapacity(VERTEX_STRIDE);
        vertexData.putFloat(x).putFloat(y).putFloat(z);       // Position (12B)
        vertexData.putInt(argb);                               // Color (4B, ARGB)
        vertexData.putFloat(u).putFloat(v);                    // UV0 (8B)
        vertexData.putShort((short) lightU).putShort((short) lightV); // UV2/Lightmap (4B)
        // Normal：MC 1.21.1 BLOCK 的 NORMAL 是 signed byte，值域 [-127,127]，
        // 对应 [-1,1]。第 4 字节 padding 补齐到 4 字节对齐。
        vertexData.put((byte) (nx * 127)).put((byte) (ny * 127)).put((byte) (nz * 127));
        vertexData.put((byte) 0); // padding
        vertexCount++;
    }

    /** 标记写入完成（后台线程调用）。 */
    public void markReady(long sourceVersion) {
        this.sourceVersion = sourceVersion;
        vertexData.flip(); // 切换为读模式，供渲染线程消费
        state = State.READY;
    }

    /** 标记为过期，等待重建。 */
    public void markStale() {
        state = State.STALE;
    }

    /** 是否已就绪且版本一致。 */
    public boolean isReady(long expectedVersion) {
        return state == State.READY && sourceVersion == expectedVersion;
    }

    /** 归还缓冲（供池化复用），重置为写模式。 */
    public void recycle() {
        vertexData.clear();
        vertexCount = 0;
        state = State.EMPTY;
        // 释放 GPU 缓冲（渲染线程调用 recycle 时才安全 close）。
        com.mojang.blaze3d.vertex.VertexBuffer gpu = gpuBuffer;
        if (gpu != null) {
            gpuBuffer = null;
            gpu.close();
        }
        gpuVersion = -1L;
        // 归还 DirectByteBuffer 到池，供后续 acquire 复用，杜绝频繁 allocateDirect。
        LODMeshPool.getInstance().release(this);
    }

    /**
     * 摘除内部 DirectByteBuffer 并返回（供对象池复用），此后本对象不再持有它。
     * 调用后 {@code vertexData} 置 {@code null}，对象应被丢弃。
     */
    ByteBuffer detachBuffer() {
        ByteBuffer buf = vertexData;
        vertexData = null;
        vertexCount = 0;
        return buf;
    }

    public ByteBuffer vertexData() { return vertexData; }
    public int vertexCount() { return vertexCount; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public int lodLevel() { return lodLevel; }
    public long sourceVersion() { return sourceVersion; }
    public State state() { return state; }

    /** 获取 GPU 顶点缓冲（可能为 null，未上传）。 */
    public com.mojang.blaze3d.vertex.VertexBuffer gpuBuffer() { return gpuBuffer; }

    /** 设置 GPU 顶点缓冲（渲染线程调用）。 */
    public void setGpuBuffer(com.mojang.blaze3d.vertex.VertexBuffer buf, long version) {
        this.gpuBuffer = buf;
        this.gpuVersion = version;
    }

    /** GPU 缓冲是否已按当前版本上传。 */
    public boolean gpuUploaded(long expectedVersion) {
        return gpuBuffer != null && gpuVersion == expectedVersion;
    }

    public void setChunkPos(int chunkX, int chunkZ) { this.chunkX = chunkX; this.chunkZ = chunkZ; }
    public void setLodLevel(int lodLevel) { this.lodLevel = lodLevel; }

    /** 区块中心世界 X 坐标（chunkX*16 + 8）。 */
    public double centerX() { return chunkX * 16.0 + 8.0; }

    /** 区块中心世界 Z 坐标（chunkZ*16 + 8）。 */
    public double centerZ() { return chunkZ * 16.0 + 8.0; }
}
