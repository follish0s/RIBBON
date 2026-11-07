/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.Config;
import com.example.ribbon.render.fsr2.Fsr2MotionVectors;
import com.example.ribbon.render.fsr2.Fsr2Renderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@link LevelRenderer#renderLevel} 末尾截获已渲染好的世界帧缓冲，
 * 交给 {@link Fsr2Renderer} 跑运动矢量→时域累积→重建锐化三段处理。
 *
 * <h2>为什么选 renderLevel 的 TAIL（而非 RenderGuiEvent）</h2>
 * <ul>
 *   <li>此时主渲染目标已包含完整世界（含天气/粒子/实体），但<b>尚未</b>渲染
 *       持拿物品与 GUI——FSR2 上采样发生在"纯 3D 帧"上，UI 后绘制保持锐利，
 *       避免把 HUD 一起时域累积导致拖影；</li>
 *   <li>{@link com.example.ribbon.render.FsrPipeline}（FSR1）走的是
 *       RenderGuiEvent.Post，会因"打开界面时跳过"产生空白；FSR2 在 TAIL 处理
 *       无此问题——打开背包时背景世界仍正常上采样。</li>
 * </ul>
 *
 * <h2>矩阵捕获</h2>
 * renderLevel 的第 6 个参数是<b>视图矩阵</b>（相机旋转的共轭，纯旋转），
 * 第 7 个参数是<b>投影矩阵</b>（已被 GameRenderer 的 bobHurt 处理）。
 * 两者构成完整 VP——直接交给 {@link Fsr2MotionVectors}，运动矢量纯由
 * 相机运动驱动（相机抖动已彻底移除，见 MixinGameRendererJitter 回滚说明）。
 *
 * <p>HEAD 捕获、TAIL 派发，保证即使 renderLevel 中途提前 return 也只跑一次。</p>
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererFsr2 {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ribbon$captureFrame(DeltaTracker tracker, boolean renderBlockOutline,
                                     Camera camera, GameRenderer gameRenderer,
                                     LightTexture lightTexture, Matrix4f frustumMatrix,
                                     Matrix4f projectionMatrix, CallbackInfo ci) {
        if (Config.getFsrMode() != Config.FsrMode.FSR2) {
            Fsr2MotionVectors.invalidate();
            return;
        }
        if (Minecraft.getInstance().level == null) {
            return;
        }
        // 捕获本帧抖动后的完整 VP 与相机世界坐标
        Fsr2MotionVectors.beginFrame(camera, projectionMatrix, frustumMatrix);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void ribbon$processFsr2(DeltaTracker tracker, boolean renderBlockOutline,
                                    Camera camera, GameRenderer gameRenderer,
                                    LightTexture lightTexture, Matrix4f frustumMatrix,
                                    Matrix4f projectionMatrix, CallbackInfo ci) {
        if (Config.getFsrMode() != Config.FsrMode.FSR2) {
            return;
        }
        // 影子渲染阶段跳过（Embeddium 影子也会走 renderLevel 的部分路径）
        try {
            if (com.example.ribbon.ShadowRendererDetector.isShadowPassActive()) {
                return;
            }
        } catch (Throwable ignored) {
            // 检测器未就绪时安全跳过检测，不影响主流程
        }
        Fsr2Renderer.processFrame();
    }
}
