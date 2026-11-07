/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.Config;
import com.example.ribbon.render.fsr2.Fsr2Renderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 帧生命周期通知（纯净类）。
 *
 * 【回滚说明】
 * 之前通过修改投影矩阵（getProjectionMatrix / RenderSystem.setProjectionMatrix）施加相机抖动，
 * 破坏了视锥体剔除（区块消失/闪烁）与 Iris 阴影 Pass（阴影抽动），现已彻底移除矩阵注入。
 * 本类仅保留 FSR2 帧开始的时域通知，不再触碰任何矩阵。
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererJitter {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ribbon$beginFrame(DeltaTracker tracker, CallbackInfo ci) {
        if (Config.getFsrMode() == Config.FsrMode.FSR2) {
            Fsr2Renderer.onFrameBegin();
        }
    }
}
