/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 在窗口标题栏显示当前 FPS：{@code 原标题 | 123 FPS}。
 *
 * <p>进入世界时记住原标题并开始更新（每秒一次，避免频繁 GLFW 调用）；
 * 离开世界或关闭开关时立即恢复原标题。任何 GLFW 异常都静默降级，
 * 不影响游戏运行。</p>
 */
@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class WindowTitleFps {

    private static String originalTitle;
    private static boolean active;
    private static int tickCounter;

    private WindowTitleFps() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        boolean want = com.example.ribbon.optimization.RuntimeConfig.showFpsInTitle
                && mc.level != null
                && !mc.options.hideGui;

        if (want && !active) {
            active = true;
            tickCounter = 0;
            captureOriginal(mc);
            update(mc);
        } else if (!want && active) {
            active = false;
            restore(mc);
        } else if (active && ++tickCounter >= 20) {
            tickCounter = 0;
            update(mc);
        }
    }

    private static void captureOriginal(Minecraft mc) {
        // 1.21.1 的 LWJGL 绑定不含 glfwGetWindowTitle，直接用原版启动时的标题格式还原：
        // modded 环境下标题为 "Minecraft* <版本>"
        try {
            originalTitle = "Minecraft* " + net.minecraft.SharedConstants.getCurrentVersion().getName();
        } catch (Throwable t) {
            originalTitle = "Minecraft";
        }
    }

    private static void update(Minecraft mc) {
        if (originalTitle == null) {
            return;
        }
        try {
            mc.getWindow().setTitle(originalTitle + "  |  " + mc.getFps() + " FPS");
        } catch (Throwable ignored) {
            // 标题更新失败不影响游戏
        }
    }

    private static void restore(Minecraft mc) {
        if (originalTitle != null) {
            try {
                mc.getWindow().setTitle(originalTitle);
            } catch (Throwable ignored) {
                // 恢复失败也不再重试
            }
            originalTitle = null;
        }
    }
}
