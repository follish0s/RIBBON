/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin.memory;

import com.example.ribbon.memory.MemoryOptimizer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 碰撞箱 / 渲染形状全局去重。
 *
 * <p><b>优化原理：</b>每个 {@code BlockStateBase} 持有一个 {@code Cache}，
 * 其中 {@code shapes}（6 个方向的碰撞形状）和 {@code occlusionShapes}
 * （6 个方向的遮挡形状）各自独立分配。对于楼梯、台阶、栅栏、玻璃板等
 * 由 {@code Shapes.or(...)} 动态合成的方块，大量 BlockState 会算出
 * 内容完全相同但实例不同的 VoxelShape —— 每份含 DiscreteVoxelShape
 * 位图 + AABB 列表，可达数百字节。</p>
 *
 * <p><b>诚实的收益边界：</b>原版 {@code Shapes.block()} 已经返回<b>静态单例</b>，
 * 所以"完整立方体"类方块（占大多数）本就已经共享，本 Mixin 对它们无额外收益。
 * 真正受益的是动态合成形状的方块。因此这是一项<b>增量优化</b>，
 * 不是数量级的内存下降。</p>
 *
 * <p><b>为什么安全：</b></p>
 * <ul>
 *   <li>VoxelShape 是<b>不可变</b>对象，把数组槽位替换为 equals 等价的实例，
 *       后续所有 {@code isEmpty()} / {@code bounds()} / 碰撞检测行为完全一致；</li>
 *   <li>池依赖 {@code VoxelShape#equals}。若某版本未基于内容重写 equals，
 *       {@code putIfAbsent} 退化为按身份命中 —— 结果是"没有去重收益"，
 *       <b>绝不会返回内容不同的形状</b>，自动降级为无害空操作；</li>
 *   <li>数组槽位为 {@code null} 时跳过（动态形状方块的合法状态）；</li>
 *   <li>任何异常 → 捕获、记录、保留原数组内容。</li>
 * </ul>
 *
 * <p>已核对的真实字段（来自 1.21.1 NeoForm raw.jar 常量池，
 * 类 {@code BlockBehaviour$BlockStateBase$Cache}）：
 * {@code shapes : VoxelShape[]}、{@code occlusionShapes : VoxelShape[]}。</p>
 */
@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase$Cache")
public abstract class MixinBlockStateCache {

    /** 6 个方向的碰撞形状数组。 */
    @Shadow
    private VoxelShape[] shapes;

    /** 6 个方向的遮挡形状数组。 */
    @Shadow
    private VoxelShape[] occlusionShapes;

    /**
     * 在 Cache 构造末尾对两个形状数组做原地规范化。
     *
     * <p>Cache 构造器签名为 {@code (BlockStateBase)}，其中会调用
     * {@code getCollisionShape} / {@code getOcclusionShape} 填满数组，
     * 因此 TAIL 是数组内容已完备的最早时机。</p>
     */
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void ribbon$deduplicateShapes(CallbackInfo ci) {
        try {
            if (!MemoryOptimizer.isEnabled()) return;
            ribbon$canonicalizeArray(this.shapes);
            ribbon$canonicalizeArray(this.occlusionShapes);
        } catch (Throwable t) {
            MemoryOptimizer.reportFallback("BlockStateBase.Cache.shapes", t);
        }
    }

    /**
     * 逐槽位替换为池中的等价实例。原地写入，不改变数组引用，
     * 因此任何已持有该数组引用的代码看到的仍是同一个数组。
     */
    private void ribbon$canonicalizeArray(VoxelShape[] array) {
        if (array == null) return;
        for (int i = 0; i < array.length; i++) {
            VoxelShape original = array[i];
            if (original == null) continue;

            VoxelShape canonical = MemoryOptimizer.canonicalizeShape(original);
            if (canonical != null && canonical != original) {
                array[i] = canonical;
            }
        }
    }
}
