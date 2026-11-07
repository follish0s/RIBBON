/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.example.ribbon.compat.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 药水效果计时 HUD：屏幕角落显示当前效果名称与剩余时间。
 *
 * <p>时长文案复用原版 {@link MobEffectUtil#formatDuration}（自带本地化）；
 * 颜色按效果分类：有益绿 / 有害红 / 中性黄。F3 调试屏打开时自动隐藏。
 * 背景跟随液态玻璃主题（未启用时退化为半透明黑底）。</p>
 */
public final class PotionTimerHud {

    private record Line(String text, int color) {
    }

    // 文本缓存：效果增删立即刷新，剩余时间按节流间隔刷新
    private static long lastBuildMs;
    private static List<Line> cachedLines = List.of();
    private static int cachedBlockW;
    private static int cachedBlockH;
    private static int cachedEffectCount;

    private PotionTimerHud() {
    }

    public static void onRenderOverlay(com.example.ribbon.compat.GuiGraphics g) {
        if (!com.example.ribbon.optimization.RuntimeConfig.potionHudEnabled) return;
        if (com.example.ribbon.optimization.FocusState.shouldPauseBackgroundWork()) return;

        Minecraft mc = Minecraft.getInstance();
        if (com.example.ribbon.compat.LegacyRenderState.isGuiHidden(mc) || mc.level == null || mc.player == null) return;
        if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) return;

        Collection<MobEffectInstance> effects = mc.player.getActiveEffects();
        if (effects.isEmpty()) {
            cachedLines = List.of();
            cachedEffectCount = 0;
            return;
        }

        Font font = mc.font;
        int effectCount = effects.size();
        long nowMs = System.currentTimeMillis();
        if (effectCount != cachedEffectCount
                || com.example.ribbon.optimization.HudRefreshThrottle.shouldRefresh(lastBuildMs, nowMs)) {
            List<Line> fresh = new ArrayList<>(effectCount);
            int width = 0;
            for (MobEffectInstance effect : effects) {
                String name = effect.getEffect().value().getDisplayName().getString();
                String duration = MobEffectUtil.formatDuration(effect, 1.0F, 0.0F).getString();
                String text = name + " " + duration;
                width = Math.max(width, font.width(text));
                int color = switch (effect.getEffect().value().getCategory()) {
                    case HARMFUL -> 0xFF9C9C;
                    case NEUTRAL -> 0xFFE08A;
                    default -> 0x9CFF9C;
                };
                fresh.add(new Line(text, color));
            }
            cachedLines = fresh;
            cachedBlockW = width;
            cachedBlockH = fresh.size() * (font.lineHeight + 2) - 2;
            cachedEffectCount = effectCount;
            lastBuildMs = nowMs;
        }
        if (cachedLines.isEmpty()) return;

        List<Line> lines = cachedLines;
        int lineH = font.lineHeight + 2;
        int blockW = cachedBlockW;
        int blockH = cachedBlockH;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float scale = (float) Config.getPotionHudScale();
        int margin = 4;

        float tx, ty;
        switch (Config.getPotionHudCorner()) {
            case TOP_RIGHT    -> { tx = sw - margin - blockW * scale; ty = margin; }
            case BOTTOM_LEFT  -> { tx = margin;                       ty = sh - margin - blockH * scale; }
            case BOTTOM_RIGHT -> { tx = sw - margin - blockW * scale; ty = sh - margin - blockH * scale; }
            default           -> { tx = margin;                       ty = margin; }
        }
        int x1 = (int) (tx - 3);
        int y1 = (int) (ty - 3);
        int x2 = (int) (tx + blockW * scale + 3);
        int y2 = (int) (ty + blockH * scale + 3);
        if (com.example.ribbon.glass.LiquidGlassTheme.drawPanel(g, x1, y1, x2, y2, 1.2F)) {
            // 玻璃底板（跟随液态玻璃主题）
        } else {
            g.fill(x1, y1, x2, y2, 0x88000000);
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(tx, ty, 0.0f);
        pose.scale(scale, scale, 1.0f);
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            g.drawString(font, line.text(), 0, i * lineH, line.color(), true);
        }
        pose.popPose();
    }
}
