/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 窗口焦点状态采样。
 *
 * <p>窗口失焦（切到别的程序、最小化）时 HUD 本来就看不见，但帧图采样、
 * 文本重建、内存压力采样、特效动画仍在每帧运行。本类以 250ms 间隔采样
 * GLFW 焦点状态，供各处早退，回焦后立即恢复。</p>
 *
 * <p>GLFW 查询失败 / 客户端尚未就绪时一律按「有焦点」处理，绝不因检测失败
 * 而暂停任何功能。</p>
 */
public final class FocusState {

    private static final long SAMPLE_INTERVAL_MS = 250L;

    private static volatile boolean windowFocused = true;
    private static long lastSampleMs;

    private FocusState() {
    }

    /** @return true 表示当前应暂停「非必要的客户端后台工作」 */
    public static boolean shouldPauseBackgroundWork() {
        if (!RuntimeConfig.pauseWhenUnfocused) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastSampleMs == 0L || now - lastSampleMs >= SAMPLE_INTERVAL_MS || now < lastSampleMs) {
            lastSampleMs = now;
            try {
                Minecraft mc = Minecraft.getInstance();
                long handle = mc == null ? 0L : mc.getWindow().getWindow();
                windowFocused = handle == 0L
                        || GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_FOCUSED) != 0;
            } catch (Throwable ignored) {
                windowFocused = true;
            }
        }
        return !windowFocused;
    }

    public static void reset() {
        windowFocused = true;
        lastSampleMs = 0L;
    }
}
