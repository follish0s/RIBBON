/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 单生产者-单消费者（SPSC）无锁环形队列，基于 {@link VarHandle} 显式内存序。
 *
 * <p>设计用途：异步寻路工作线程（生产者）与客户端主线程（消费者）之间的
 * 零延迟通信。全程无锁、无 CAS 重试、无队列节点分配——入队出队都是
 * 一次普通数组写 + 一次 release/acquire 游标推进，稳定 O(1)。</p>
 *
 * <h2>算法（null 槽哨兵协议）</h2>
 * <ol>
 *   <li>环形数组容量为 2 的幂，索引用 {@code index & mask} 截断；</li>
 *   <li>生产者只写 {@code producerIndex}，消费者只写 {@code consumerIndex}
 *       ——各自对私有游标用普通读（{@code get}），省掉栅栏；</li>
 *   <li>消费者以 <b>槽位是否为 null</b> 判空，因此生产者发布元素的顺序是：
 *       先 {@code setRelease} 写槽位，再推进游标；消费后把槽位
 *       {@code set(null)} 清空，再 release 推进自己的游标；</li>
 *   <li>生产者判满：{@code producerIndex - capacity >= acquire(consumerIndex)}。
 *       消费者清槽发生在 release 游标<b>之前</b>，所以生产者 acquire 到新游标后
 *       必然能看到槽位已被清空，绝无覆盖未读元素的风险。</li>
 * </ol>
 *
 * <h2>缓存行填充（False Sharing 防护）</h2>
 *
 * <p>两个游标分属两个线程、被对方高频 acquire 读，若落在同一 64B 缓存行，
 * 每次写都会使对方核心的该行失效（伪共享），吞吐可暴跌数倍。布局：</p>
 * <ul>
 *   <li>{@code producerIndex} 前垫 7 个 long（56B）+ 对象头 ≈ 1 行；</li>
 *   <li>两个游标之间垫 15 个 long（120B ≈ 2 行），彻底隔离；</li>
 *   <li>{@code consumerIndex} 之后再垫 15 个 long，防止与后续字段/邻接对象共享；</li>
 * </ul>
 * <p>HotSpot 按声明顺序布局 long 字段（实测行为，非规范保证），这是 LMAX
 * Disruptor / JCTools 同款做法。{@link #sumPaddingToPreventOptimization()}
 * 让填充字段被"使用"，杜绝 JIT 按死代码消除它们的理论风险。</p>
 *
 * <h2>语义契约（务必遵守）</h2>
 * <ul>
 *   <li><b>严格 SPSC</b>：enqueue 只能从一个线程调用，dequeue 只能从一个线程调用，
 *       违者数据损坏。多对多请用 {@code ConcurrentLinkedQueue}；</li>
 *   <li><b>禁止入队 null</b>：null 是判空哨兵；</li>
 *   <li>{@link #dequeue()} 返回 null 仅表示"此刻无可见元素"，不代表生产者已停止
 *       ——主线程推荐用 {@link #drain(Consumer, int)} 批量收；</li>
 *   <li>队列满时 {@link #enqueue} 返回 false（不阻塞、不丢弃），由调用方决定
 *       重试或放弃，绝不偷偷自旋浪费寻路线程的配额。</li>
 * </ul>
 */
public final class LockFreeQueue<T> {

    private static final VarHandle HEAD;   // consumerIndex
    private static final VarHandle TAIL;   // producerIndex
    private static final VarHandle ARRAY;  // Object[] 元素

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            HEAD = lookup.findVarHandle(LockFreeQueue.class, "consumerIndex", long.class);
            TAIL = lookup.findVarHandle(LockFreeQueue.class, "producerIndex", long.class);
            ARRAY = MethodHandles.arrayElementVarHandle(Object[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // ===== 缓存行填充：producerIndex 前垫 7 long（56B）+ 对象头 ≈ 64B =====
    @SuppressWarnings("unused")
    public long p01, p02, p03, p04, p05, p06, p07;

    /** 生产者游标（仅生产者写）。 */
    @SuppressWarnings("unused")
    private volatile long producerIndex;

    // ===== 两个游标之间垫 15 long（120B ≈ 2 缓存行），根除伪共享 =====
    @SuppressWarnings("unused")
    public long p11, p12, p13, p14, p15, p16, p17,
            p18, p19, p1a, p1b, p1c, p1d, p1e, p1f;

    /** 消费者游标（仅消费者写）。 */
    @SuppressWarnings("unused")
    private volatile long consumerIndex;

    // ===== consumerIndex 之后垫 15 long，隔离后续字段与邻接对象 =====
    @SuppressWarnings("unused")
    public long p21, p22, p23, p24, p25, p26, p27,
            p28, p29, p2a, p2b, p2c, p2d, p2e, p2f;

    /** 环形槽位数组，容量为 2 的幂。 */
    private final Object[] buffer;
    /** 容量 - 1，用于 {@code index & mask} 快速取模。 */
    private final int mask;

    /**
     * @param requestedCapacity 期望容量，内部向上取整到最近的 2 的幂（最小 2）。
     *                           实际容量可用 {@link #capacity()} 查询。
     */
    public LockFreeQueue(int requestedCapacity) {
        int capacity = 2;
        while (capacity < requestedCapacity) {
            capacity <<= 1;
        }
        this.buffer = new Object[capacity];
        this.mask = capacity - 1;
    }

    /**
     * 入队（仅生产者线程调用）。
     *
     * @return {@code true} 入队成功；{@code false} 队列已满，元素未被写入，
     *         调用方自行决定重试或放弃。
     */
    public boolean enqueue(T value) {
        Objects.requireNonNull(value, "Ribbon: LockFreeQueue 禁止入队 null（null 是判空哨兵）");
        Object[] buf = this.buffer;
        long producerIndex = (long) TAIL.get(this); // 生产者私有游标：普通读，零栅栏
        long consumerIndex = (long) HEAD.getAcquire(this); // 与消费者的 release 推进配对

        if (producerIndex - buf.length >= consumerIndex) {
            return false; // 已满
        }

        // 先发布元素（release），后推进游标——顺序不能反，
        // 否则消费者可能看到一个"已推进但槽位还空"的假象。
        ARRAY.setRelease(buf, (int) (producerIndex & this.mask), value);
        TAIL.setRelease(this, producerIndex + 1);
        return true;
    }

    /**
     * 出队（仅消费者线程调用）。
     *
     * @return 队首元素；{@code null} 表示此刻无可见元素。
     */
    @SuppressWarnings("unchecked")
    public T dequeue() {
        Object[] buf = this.buffer;
        long consumerIndex = (long) HEAD.get(this); // 消费者私有游标：普通读，零栅栏
        int slot = (int) (consumerIndex & this.mask);

        // acquire 读槽位，与生产者的 setRelease 直接在同一变量上配对，
        // 保证元素引用及其内部字段对消费者完全可见。
        T value = (T) ARRAY.getAcquire(buf, slot);
        if (value == null) {
            return null; // 空
        }

        // 先清槽（普通写即可：本槽位此后只归生产者写），再 release 游标。
        // 生产者 acquire 到新游标后必然看到清槽，不会覆盖未读元素。
        ARRAY.set(buf, slot, null);
        HEAD.setRelease(this, consumerIndex + 1);
        return value;
    }

    /**
     * 主线程批量收割：循环出队直到为空或达到 {@code maxElements}。
     * 典型用法：客户端每 tick 调用一次，把寻路线程算好的路径全部取走，
     * 避免 tick 间队列积压导致的"延迟感知"。
     *
     * @return 实际消费的元素数量。
     */
    public int drain(Consumer<? super T> consumer, int maxElements) {
        int count = 0;
        while (count < maxElements) {
            T value = this.dequeue();
            if (value == null) {
                break;
            }
            consumer.accept(value);
            count++;
        }
        return count;
    }

    /**
     * 近似元素数（两次独立读，并发下只保证弱一致）。
     * 只用于监控/调试，严禁当同步依据。
     */
    public int size() {
        long after = (long) TAIL.getVolatile(this);
        long before = (long) HEAD.getVolatile(this);
        return (int) Math.max(0L, after - before);
    }

    /** 实际容量（2 的幂）。 */
    public int capacity() {
        return this.buffer.length;
    }

    /**
     * 引用所有填充字段，防止 JIT 把"从未被读取"的它们当作死代码消除
     * （LMAX Disruptor 同款手法）。仅在诊断时手动调用。
     */
    public long sumPaddingToPreventOptimization() {
        return this.p01 + this.p02 + this.p03 + this.p04 + this.p05 + this.p06 + this.p07
                + this.p11 + this.p12 + this.p13 + this.p14 + this.p15 + this.p16 + this.p17
                + this.p18 + this.p19 + this.p1a + this.p1b + this.p1c + this.p1d + this.p1e + this.p1f
                + this.p21 + this.p22 + this.p23 + this.p24 + this.p25 + this.p26 + this.p27
                + this.p28 + this.p29 + this.p2a + this.p2b + this.p2c + this.p2d + this.p2e + this.p2f;
    }
}
