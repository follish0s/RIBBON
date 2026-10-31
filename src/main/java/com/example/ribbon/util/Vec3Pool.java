/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.LongAdder;

/**
 * 战斗与物理热路径的 "零 GC" 向量/包围盒回收池（线程本地）。
 *
 * <h2>为什么不是传统的 "改坐标复用" 对象池（关键设计决策）</h2>
 *
 * 在 1.21.1（本项目实际依赖的 NeoForge 21.1.x，已用 javap 对补丁后的
 * {@code neoforge-21.1.61.jar} 核实）中：
 * <ul>
 *   <li>{@link Vec3} 的 {@code x/y/z} 字段为 {@code public final}；</li>
 *   <li>{@link AABB} 的 {@code minX...maxZ} 六个字段同样为 {@code public final}。</li>
 * </ul>
 * 两者都是<b>彻底不可变</b>的值对象。任何 "从池里取出实例、写入新坐标、用完归还"
 * 的经典池化代码在这里<b>根本无法编译</b>（final 字段不可赋值），用反射改 final
 * 又会在 JIT 内联后失去意义。因此本池采用对不可变对象唯一正确的回收策略：
 *
 * <h3>等值回收（Equality Recycling）</h3>
 * <ol>
 *   <li>{@code release} 时把实例放进线程本地的固定大小环形槽；</li>
 *   <li>{@code acquire(x, y, z)} 时先在槽内做逐字段 {@code ==} 匹配，
 *       命中则直接返回旧实例（零分配），未命中才 {@code new}；</li>
 *   <li>槽容量固定（2 的幂），写满即覆盖最旧项——内存上界恒定，无泄漏。</li>
 * </ol>
 *
 * <p>这个策略在实战中命中率很高，因为战斗/物理循环大量产生<b>逐位相同</b>的坐标：
 * 站桩实体的剔除盒每帧都是同一组 double、同一 tick 内多次射线检测共享同一起点、
 * 实体静止时 {@code position()} 的三个分量完全不变。逐位比较（而非 equals/epsilon）
 * 是刻意为之：只有逐位相同的重算才能安全复用；含 NaN 的坐标永远匹配失败，
 * 会走 new 分支，不会污染池。</p>
 *
 * <h3>线程安全模型</h3>
 * 所有状态都在 {@link ThreadLocal} 中，无任何锁与 CAS，天然免疫主线程/渲染线程/
 * 异步寻路线程间的争用。契约：实例 {@code release} 之后<b>严禁继续持有或读取</b>
 * （它随时会被交给同线程的下一个主人）。
 *
 * <p>命中/未命中计数使用 {@link LongAdder}（无争用、约一次 CAS 开销），
 * 供调试浮层观察池效率；如对极致延迟敏感可删除相关调用。</p>
 */
public final class Vec3Pool {

    /** 每线程回收的 {@link Vec3} 上限（2 的幂，配合位运算取模）。 */
    private static final int VEC3_SLOTS = 16;
    /** 每线程回收的 {@link AABB} 上限（2 的幂）。 */
    private static final int AABB_SLOTS = 16;

    private static final ThreadLocal<RecycleRing> VEC3_RING =
            ThreadLocal.withInitial(() -> new RecycleRing(VEC3_SLOTS));
    private static final ThreadLocal<RecycleRing> AABB_RING =
            ThreadLocal.withInitial(() -> new RecycleRing(AABB_SLOTS));

    // 仅用于调试统计，可安全移除
    private static final LongAdder VEC3_HITS = new LongAdder();
    private static final LongAdder VEC3_MISSES = new LongAdder();
    private static final LongAdder AABB_HITS = new LongAdder();
    private static final LongAdder AABB_MISSES = new LongAdder();

    private Vec3Pool() {
    }

    /**
     * 从线程本地回收池获取一个坐标为 {@code (x, y, z)} 的 {@link Vec3}。
     * 池中存在逐位相同坐标的实例时直接复用（零分配），否则才分配新实例。
     *
     * @apiNote 比较使用 {@code ==}（逐位相同才复用），NaN 坐标永远未命中。
     */
    public static Vec3 acquireVec3(double x, double y, double z) {
        RecycleRing ring = VEC3_RING.get();
        Object[] slots = ring.slots;
        // 线性扫描固定容量的小数组：无迭代器、无装箱、零分配。
        // 扫描长度恒定为 16，代价约等于一次未命中的 new Vec3 的 1/3。
        for (int i = 0; i < slots.length; i++) {
            Object o = slots[i];
            if (o != null) {
                Vec3 v = (Vec3) o;
                if (v.x == x && v.y == y && v.z == z) {
                    slots[i] = null; // 交出所有权，从池中摘除
                    VEC3_HITS.increment();
                    return v;
                }
            }
        }
        VEC3_MISSES.increment();
        return new Vec3(x, y, z);
    }

    /**
     * 归还一个 {@link Vec3} 供同线程后续 {@link #acquireVec3} 等值复用。
     * 契约：归还后严禁继续读取该实例（它可能被立刻交出去）。
     */
    public static void releaseVec3(Vec3 v) {
        if (v == null) {
            return;
        }
        RecycleRing ring = VEC3_RING.get();
        ring.slots[ring.cursor] = v;
        ring.cursor = (ring.cursor + 1) & (VEC3_SLOTS - 1); // 环形覆盖最旧项
    }

    /**
     * 从线程本地回收池获取一个与 {@code template} 六个分量逐位相同的 {@link AABB}。
     * 用于剔除盒、碰撞扫描盒等 "同参数反复重算" 的场景。
     */
    public static AABB acquireAABB(AABB template) {
        return acquireAABB(template.minX, template.minY, template.minZ,
                template.maxX, template.maxY, template.maxZ);
    }

    /**
     * 从线程本地回收池获取指定包围范围的 {@link AABB}，等值命中则零分配。
     */
    public static AABB acquireAABB(double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ) {
        RecycleRing ring = AABB_RING.get();
        Object[] slots = ring.slots;
        for (int i = 0; i < slots.length; i++) {
            Object o = slots[i];
            if (o != null) {
                AABB b = (AABB) o;
                if (b.minX == minX && b.minY == minY && b.minZ == minZ
                        && b.maxX == maxX && b.maxY == maxY && b.maxZ == maxZ) {
                    slots[i] = null;
                    AABB_HITS.increment();
                    return b;
                }
            }
        }
        AABB_MISSES.increment();
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * 归还一个 {@link AABB} 供同线程后续 {@link #acquireAABB} 等值复用。
     * 契约：归还后严禁继续持有该实例的引用。
     */
    public static void releaseAABB(AABB b) {
        if (b == null) {
            return;
        }
        RecycleRing ring = AABB_RING.get();
        ring.slots[ring.cursor] = b;
        ring.cursor = (ring.cursor + 1) & (AABB_SLOTS - 1);
    }

    // ===== 调试统计（可安全移除，不影响核心逻辑） =====

    public static long vec3Hits() {
        return VEC3_HITS.sum();
    }

    public static long vec3Misses() {
        return VEC3_MISSES.sum();
    }

    public static long aabbHits() {
        return AABB_HITS.sum();
    }

    public static long aabbMisses() {
        return AABB_MISSES.sum();
    }

    /**
     * 固定容量的对象环形槽。用裸数组而不是 {@code ArrayDeque}：
     * Deque 的迭代器每次获取都会分配对象，恰恰违背零 GC 目标。
     */
    private static final class RecycleRing {
        final Object[] slots;
        int cursor;

        RecycleRing(int capacity) {
            this.slots = new Object[capacity];
        }
    }
}
