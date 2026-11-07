/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin.memory;

import com.example.ribbon.memory.MemoryOptimizer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ModelResourceLocation 字符串驻留（String Interning）。
 *
 * <p><b>优化原理：</b>已核对 1.21.1 的 {@code ModelResourceLocation} 是
 * 一个 <b>record</b>，组件为 {@code ResourceLocation id} + {@code String variant}，
 * 并提供静态工厂 {@code valueOf(String)} / {@code withDefaultNamespace(String)} /
 * {@code standalone(ResourceLocation)}。原版模型加载阶段会为同一路径
 * （如 {@code "minecraft:stone#inventory"}）反复构造新 record 实例，
 * 每个实例都带一份独立的 {@code variant} 字符串。</p>
 *
 * <p>本 Mixin 拦截 {@code valueOf}，在返回前查询规范化池：
 * 命中则返回池中已存在的<b>同一实例</b>，从而让数千个引用共享
 * 一个 record 对象及其内部字符串。</p>
 *
 * <p><b>为什么安全：</b></p>
 * <ul>
 *   <li>record 是<b>不可变值类型</b>，且已正确实现 {@code equals}/{@code hashCode}
 *       （由 {@code ObjectMethods.bootstrap} 生成，见常量池）。
 *       池键用规范化字符串 {@code id#variant}，键相同 ⟺ 内容相同，
 *       因此返回的实例与新建实例在 equals 意义上完全等价；</li>
 *   <li>record 实例本身被当作 Map 键/值使用时依赖 equals，替换为等价实例不改变查找结果；</li>
 *   <li>{@code @Inject} 于方法 HEAD 且 {@code cancellable}：命中时直接返回池实例，
 *       未命中或异常时<b>不 cancel</b>，原版逻辑照常执行；</li>
 *   <li>任何异常 → 捕获、记录、放行原版。</li>
 * </ul>
 *
 * <p>已核对的真实成员（来自 1.21.1 NeoForm raw.jar 常量池）：
 * 类为 {@code record}，含 {@code id : ResourceLocation}、{@code variant : String}、
 * 静态方法 {@code valueOf}、{@code withDefaultNamespace}、{@code standalone}。</p>
 */
@Mixin(ModelResourceLocation.class)
public abstract class MixinModelResourceLocation {

    /**
     * 拦截 {@code valueOf(String)}，返回池中已存在的等价实例。
     *
     * <p>入参形如 {@code "minecraft:stone#inventory"}，本身即为规范化键，
     * 无需额外加工即可作为池键使用。</p>
     */
    @Inject(method = "valueOf(Ljava/lang/String;)Lnet/minecraft/client/resources/model/ModelResourceLocation;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void ribbon$internValueOf(String descriptor, CallbackInfoReturnable<ModelResourceLocation> cir) {
        try {
            if (!MemoryOptimizer.isEnabled()) return;
            if (descriptor == null) return;

            // 先查池：命中则复用已有实例，跳过 record 构造
            Object hit = MemoryOptimizer.peekModelLocation(descriptor);
            if (hit instanceof ModelResourceLocation mrl) {
                MemoryOptimizer.recordModelLocationHit();
                cir.setReturnValue(mrl);
                return;
            }
            // 未命中：不 cancel，让原版构造出实例，随后由 ribbon$register 入池
        } catch (Throwable t) {
            MemoryOptimizer.reportFallback("ModelResourceLocation.valueOf", t);
        }
    }

    /**
     * 在 {@code valueOf} 返回前把新实例登记进池，供后续调用复用。
     */
    @Inject(method = "valueOf(Ljava/lang/String;)Lnet/minecraft/client/resources/model/ModelResourceLocation;",
            at = @At("RETURN"), require = 0)
    private static void ribbon$registerValueOf(String descriptor, CallbackInfoReturnable<ModelResourceLocation> cir) {
        try {
            if (!MemoryOptimizer.isEnabled()) return;
            if (descriptor == null) return;

            ModelResourceLocation created = cir.getReturnValue();
            if (created == null) return;

            // 用真实的 id/variant 重新拼键，避免 valueOf 内部做过规范化导致键不一致
            ResourceLocation id = created.id();
            String variant = created.variant();
            if (id == null || variant == null) return;

            String canonicalKey = id + "#" + variant;
            MemoryOptimizer.internModelLocation(canonicalKey, created);
        } catch (Throwable t) {
            MemoryOptimizer.reportFallback("ModelResourceLocation.register", t);
        }
    }
}
