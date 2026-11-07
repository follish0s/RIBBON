/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.optimization.RuntimeConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 夜视增强（Fullbright）。
 *
 * <p>原版 {@code LightTexture.updateLightTexture} 生成光图时，最后一行把最终
 * 亮度向 {@code notGamma}（逐通道取 1-c）结果插值，插值因子为
 * {@code Math.max(0.0F, gamma - darknessGamma)}。本注入把该因子抬到配置强度
 * （0..1，1 = 完全夜视），不改动任何游戏逻辑，只调亮光图。</p>
 *
 * <p>ordinal=2 对应方法内第三个 {@code Math.max(FF)F} 调用（前两个属于
 * 夜视亮度取最大值的嵌套表达式）。{@code require = 0}：注入失败仅降级为
 * 功能无效，绝不阻塞启动。</p>
 */
@Mixin(LightTexture.class)
public class MixinLightTexture {

    @ModifyExpressionValue(
            method = "updateLightTexture(F)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F", ordinal = 2),
            require = 0
    )
    private float ribbon$fullbright(float original) {
        if (!RuntimeConfig.nightVisionEnabled) {
            return original;
        }
        return Math.max(original, RuntimeConfig.fullbrightStrength);
    }
}
