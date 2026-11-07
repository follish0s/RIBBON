/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * {@link LODMesh} 对象池：复用已回收的 DirectByteBuffer，杜绝频繁 {@code allocateDirect}。
 *
 * <h2>为什么必须有池</h2>
 * <p>玩家移动时，后台线程持续构建/回收大量 LOD 区块。每个 mesh 的初始容量是
 * {@code 16*16*4*32 = 32KB} 的<b>堆外直接内存</b>。{@code ByteBuffer.allocateDirect}
 * 是 JNI 调用（走 {@code Unsafe.allocateMemory} + 系统级清零），比堆分配慢一个数量级，
 * 且频繁分配/回收直接内存会触发 Full GC 与 {@code OutOfMemoryError: Direct buffer memory}。</p>
 * <p>本池在 mesh 被 {@code recycle()} 时<b>保留其 DirectByteBuffer</b>（只 reset 游标），
 * 下次 {@code acquire()} 优先复用，从而把"每区块一次 allocateDirect"降到"启动后近乎零次"。</p>
 *
 * <h2>线程安全模型</h2>
 * <p>池本身是<b>多生产者/多消费者</b>（后台多个 worker 线程并发 acquire/release），
 * 因此用 {@code synchronized} 保护队列（临界区极小，仅一次出/入队，无锁竞争热点）。</p>
 *
 * <h2>内存上限</h2>
 * <p>空闲 buffer 不会无限累积：超出 {@link #MAX_POOLED} 的 buffer 直接交给 GC 回收。</p>
 */
public final class LODMeshPool {

    /** 池中最多保留的空闲 buffer 数（防止空闲直接内存无限占用）。 */
    private static final int MAX_POOLED = 256;

    /** 初始容量（字节）= 16×16×4 顶点 × 32 字节。 */
    private static final int INITIAL_CAPACITY = 16 * 16 * 4 * LODMesh.VERTEX_STRIDE;

    private static final LODMeshPool INSTANCE = new LODMeshPool();

    /** 空闲 DirectByteBuffer 栈（LIFO，缓存局部性好）。 */
    private final Deque<ByteBuffer> freeBuffers = new ArrayDeque<>();

    private LODMeshPool() {
    }

    public static LODMeshPool getInstance() {
        return INSTANCE;
    }

    /** 从池中取一个 LODMesh（优先复用空闲 buffer，否则新分配）。 */
    public synchronized LODMesh acquire() {
        ByteBuffer buf = freeBuffers.pollFirst();
        if (buf != null) {
            // 复用前彻底复位：position=0、limit=capacity，并强制小端字节序。
            // 上一轮 markReady 时 flip() 把 limit 压到了已写字节数，clear() 会把它
            // 恢复为 capacity；再显式设小端，防止复用后 putFloat 用错字节序产生脏顶点。
            buf.clear();
            buf.order(ByteOrder.LITTLE_ENDIAN);
            return LODMesh.wrap(buf, INITIAL_CAPACITY);
        }
        return LODMesh.acquire(INITIAL_CAPACITY);
    }

    /** 归还一个 mesh 的 DirectByteBuffer（只保留 buffer，mesh 对象本身交给 GC）。 */
    public synchronized void release(LODMesh mesh) {
        if (mesh == null) return;
        ByteBuffer buf = mesh.detachBuffer();
        if (buf == null) return;
        // 归还前强制复位 position/limit，杜绝复用后读到上一轮残留的游标状态。
        buf.clear();
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // 只有容量"标准大小"才复用，异常膨胀的 buffer 直接丢弃，避免池被超大 buffer 污染。
        if (buf.capacity() == INITIAL_CAPACITY && freeBuffers.size() < MAX_POOLED) {
            freeBuffers.addFirst(buf);
        }
        // 否则 buf 失去引用，交给 GC 回收
    }

    /** 清空池（世界卸载时调用），释放全部空闲直接内存。 */
    public synchronized void clear() {
        freeBuffers.clear();
    }

    /** 当前空闲 buffer 数（诊断用）。 */
    public synchronized int pooledCount() {
        return freeBuffers.size();
    }
}
