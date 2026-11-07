/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin.memory;

import com.example.ribbon.memory.MemoryOptimizer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 属性表去重（对标 FerriteCore 的 {@code replacePropertyMap}）。
 *
 * <p><b>优化原理：</b>原版为每个 BlockState / FluidState 单独持有一份
 * {@code Reference2ObjectArrayMap<Property, Comparable>}。对于无属性的方块
 * （空气、石头、泥土……占注册表绝大多数）以及属性组合完全相同的方块，
 * 这些 Map 内容逐字节相同却各占一份。本 Mixin 在构造末尾把
 * {@code values} 替换为池中的共享实例。</p>
 *
 * <p><b>为什么安全：</b></p>
 * <ul>
 *   <li>池的键是<b>有序签名字符串</b>（键名+值+插入顺序），签名相同 ⟺ 内容相同，
 *       因此不存在"哈希碰撞拿到错误属性表"的可能；</li>
 *   <li>{@code StateHolder} 是不可变对象（{@code setValue} 返回新实例），
 *       {@code values} 构造后永不修改，共享不会产生写冲突；</li>
 *   <li>写入发生在 {@code <init>} 的 TAIL，仍在声明类构造器内，
 *       JVM 允许对 final 实例字段执行 PUTFIELD；</li>
 *   <li>任何异常 → 捕获、记录、<b>保留原始 Map</b>，行为与原版完全一致。</li>
 * </ul>
 *
 * <p><b>关于 {@code @Shadow} 用原始类型：</b>Mixin 按"字段名 + 擦除后描述符"匹配，
 * 原始类型擦除为 {@code Lit/unimi/dsi/fastutil/objects/Reference2ObjectArrayMap;}，
 * 与真实字段精确一致；同时避免了泛型签名不匹配导致的注入失败。
 * 故此处刻意不写 {@code @Final}——省略它永远不会引发校验冲突。</p>
 *
 * <p>已核对的真实字段（来自 1.21.1 NeoForm raw.jar 常量池）：
 * {@code StateHolder.values : Reference2ObjectArrayMap}。</p>
 */
@Mixin(StateHolder.class)
public abstract class MixinStateHolder {

    /**
     * 属性表字段。原始类型声明，运行时按擦除描述符匹配。
     */
    @Shadow
    @SuppressWarnings("rawtypes")
    private Reference2ObjectArrayMap values;

    /**
     * 在构造器末尾尝试把属性表替换为池中的共享实例。
     *
     * <p>{@code method = "<init>"} 匹配 StateHolder 的唯一构造器
     * {@code (Object owner, Reference2ObjectArrayMap values, MapCodec propertiesCodec)}。</p>
     */
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void ribbon$deduplicatePropertyMap(CallbackInfo ci) {
        try {
            if (!MemoryOptimizer.isEnabled()) return;

            Map<Property<?>, Comparable<?>> current = this.values;
            if (current == null) return;

            Map<Property<?>, Comparable<?>> canonical =
                    MemoryOptimizer.canonicalizePropertyMap(current);

            // canonical == null 表示"不干预"，保留原值即可
            if (canonical != null && canonical != current) {
                this.values = (Reference2ObjectArrayMap) canonical;
            }
        } catch (Throwable t) {
            // 兜底：任何异常都不改变原版行为，仅记录一次
            MemoryOptimizer.reportFallback("StateHolder.values", t);
        }
    }
}
