/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin.memory;

import com.example.ribbon.memory.MemoryOptimizer;
import net.minecraft.client.renderer.block.model.multipart.Condition;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 多部件模型（Multipart Model）谓词去重。
 *
 * <p><b>优化原理：</b>已核对 1.21.1 的 {@code Selector} 含字段
 * {@code condition : Condition} 与方法 {@code getPredicate(StateDefinition)}。
 * 原版 {@code getPredicate} 每次调用都会为 {@code condition} 生成一个新的
 * {@code Predicate<BlockState>} 实例。{@code MultiPart#getStatePredicate}
 * 在模型烘焙时会对同一批 Selector 反复调用，导致大量内容相同的
 * lambda 实例被重复分配并长期持有。</p>
 *
 * <p>本 Mixin 用<b>注入到 Selector 实例上的缓存字段</b>记住首次生成的谓词，
 * 后续调用直接复用。由于同一个 {@code Condition} 实例对同一个
 * {@code StateDefinition} 产生的谓词在语义上完全等价，复用是安全的。</p>
 *
 * <p><b>诚实的收益边界：</b>原版 {@code Selector} 内部已经使用
 * {@code System.identityHashCode} 参与 equals/hashCode（见常量池），
 * 说明 Mojang 已对 Selector 身份做过处理。本 Mixin 的增量收益来自
 * <b>避免谓词 lambda 的重复分配</b>，属于中等收益。</p>
 *
 * <p><b>为什么安全：</b></p>
 * <ul>
 *   <li>缓存字段由 Mixin 通过 {@code @Unique} 注入，是 Selector 的<b>实例字段</b>，
 *       与 Selector 实例同生命周期，不存在跨模型串用；</li>
 *   <li>缓存键隐含绑定 {@code this.condition} 与传入的 {@code definition}：
 *       只有当 definition 与首次调用相同时才复用，否则重新生成并覆盖缓存；</li>
 *   <li>HEAD 注入 + {@code cancellable}：命中直接返回缓存，未命中不 cancel；</li>
 *   <li>任何异常 → 捕获、记录、放行原版。</li>
 * </ul>
 *
 * <p>已核对的真实成员（来自 1.21.1 NeoForm raw.jar 常量池，
 * 类 {@code net/minecraft/client/renderer/block/model/multipart/Selector}）：
 * {@code condition : Condition}、{@code variant : MultiVariant}、
 * {@code getPredicate(StateDefinition)}、内部使用 {@code identityHashCode}。</p>
 *
 * <p><b>注意包路径：</b>Selector 位于
 * {@code net.minecraft.client.renderer.block.model.multipart}，
 * <b>不是</b> {@code client.resources.model} —— 这是 1.21.1 的实际位置，
 * 已通过在 raw.jar 中枚举 class 条目确认。</p>
 */
@Mixin(Selector.class)
public abstract class MixinMultipartSelector {

    /** 该 Selector 的判定条件。 */
    @Shadow
    private Condition condition;

    /** 缓存的谓词实例（首次 getPredicate 后填充）。 */
    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.world.level.block.state.StateDefinition<?, ?> ribbon$cachedDefinition;

    /** 缓存的谓词实例。用 Object 声明以规避泛型擦除后的签名匹配问题。 */
    @org.spongepowered.asm.mixin.Unique
    private java.util.function.Predicate<?> ribbon$cachedPredicate;

    /**
     * 拦截 {@code getPredicate(StateDefinition)}，命中缓存则直接复用。
     *
     * <p>{@code require = 0}：若因混淆名/签名变动注入失败，
     * Mixin 不会报错中断启动，原版逻辑照常工作。</p>
     */
    @Inject(method = "getPredicate", at = @At("HEAD"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ribbon$reusePredicate(
            net.minecraft.world.level.block.state.StateDefinition definition,
            CallbackInfoReturnable cir) {
        try {
            if (!MemoryOptimizer.isPredicateDedupEnabled()) return;

            // 只有 definition 与缓存时一致才复用，避免跨 StateDefinition 串用
            if (this.ribbon$cachedPredicate != null && this.ribbon$cachedDefinition == definition) {
                MemoryOptimizer.recordPredicateHit();
                cir.setReturnValue(this.ribbon$cachedPredicate);
                return;
            }
            MemoryOptimizer.recordPredicateMiss();
            // 不 cancel，交给原版生成，随后在 RETURN 处入缓存
        } catch (Throwable t) {
            MemoryOptimizer.reportFallback("Selector.getPredicate.head", t);
        }
    }

    /**
     * 在原版生成谓词后写入缓存，供后续调用复用。
     */
    @Inject(method = "getPredicate", at = @At("RETURN"), require = 0)
    @SuppressWarnings("rawtypes")
    private void ribbon$cachePredicate(
            net.minecraft.world.level.block.state.StateDefinition definition,
            CallbackInfoReturnable cir) {
        try {
            if (!MemoryOptimizer.isPredicateDedupEnabled()) return;

            Object predicate = cir.getReturnValue();
            if (predicate == null) return;

            this.ribbon$cachedDefinition = definition;
            this.ribbon$cachedPredicate = (java.util.function.Predicate<?>) predicate;
        } catch (Throwable t) {
            MemoryOptimizer.reportFallback("Selector.getPredicate.return", t);
        }
    }
}
