/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.hud;

import com.example.ribbon.Config;
import com.example.ribbon.Ribbon;
import net.minecraft.client.Minecraft;
import com.example.ribbon.compat.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 综合信息 HUD：坐标、群系、左右键 CPS、装备耐久。
 * 监听 RenderGuiEvent.Post，在 HUD 最上层绘制。
 */
public final class InfoHudHandler {

    // ==================== CPS 追踪 ====================
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static final long CPS_WINDOW_MS = 1000;

    // 鼠标按键状态缓存（tick 里采样）
    private static boolean lastLeftDown = false;
    private static boolean lastRightDown = false;

    // 文本缓存：坐标/群系/CPS/耐久每帧都会变但 100ms 内无视觉差异，按节流间隔重建
    private static long lastBuildMs;
    private static java.util.List<String> cachedLines = java.util.List.of();
    private static int cachedMaxW;

    // ==================== Tick：采样鼠标点击 ====================

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!Config.isInfoHudEnabled()) return;

        long now = System.currentTimeMillis();

        // 左键
        boolean leftDown = mc.options.keyAttack.isDown();
        if (leftDown && !lastLeftDown) {
            leftClicks.addLast(now);
        }
        lastLeftDown = leftDown;

        // 右键
        boolean rightDown = mc.options.keyUse.isDown();
        if (rightDown && !lastRightDown) {
            rightClicks.addLast(now);
        }
        lastRightDown = rightDown;

        // 清理过期点击（>1秒前的记录）
        purgeOld(leftClicks, now);
        purgeOld(rightClicks, now);
    }

    private static void purgeOld(Deque<Long> deque, long now) {
        while (!deque.isEmpty() && now - deque.peekFirst() > CPS_WINDOW_MS) {
            deque.pollFirst();
        }
    }

    // ==================== 渲染 ====================

    public static void onRenderGui(com.example.ribbon.compat.GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (com.example.ribbon.compat.LegacyRenderState.isGuiHidden(mc) || mc.level == null || mc.player == null) return;
        if (!Config.isInfoHudEnabled()) return;
        if (com.example.ribbon.optimization.FocusState.shouldPauseBackgroundWork()) return;
        float scale = (float) Config.getInfoHudScale();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int margin = 4;
        int lineH = mc.font.lineHeight;
        float scaledLineH = lineH * scale;

        // 收集所有要显示的行（节流重建，避免每帧分配字符串与查群系）
        long nowMs = System.currentTimeMillis();
        if (com.example.ribbon.optimization.HudRefreshThrottle.shouldRefresh(lastBuildMs, nowMs)) {
            java.util.List<String> fresh = new java.util.ArrayList<>(5);

            // ① 坐标
            BlockPos pos = mc.player.blockPosition();
            fresh.add(String.format("XYZ: %d %d %d", pos.getX(), pos.getY(), pos.getZ()));

            // ② 生物群系
            var biomeKey = mc.level.getBiome(pos).unwrapKey();
            String biomeName = biomeKey.map(k -> k.identifier().getPath()).orElse("unknown");
            fresh.add("Biome: " + biomeName);

            // ③ CPS
            int leftCps = leftClicks.size();
            int rightCps = rightClicks.size();
            fresh.add(String.format("CPS  L:%d  R:%d", leftCps, rightCps));

            // ④ 装备耐久（头盔、胸甲、护腿、靴子）
            EquipmentSlot[] slots = {
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            String[] slotNames = {"H", "C", "L", "B"};
            StringBuilder sb = new StringBuilder("Armor: ");
            boolean hasArmor = false;
            for (int i = 0; i < slots.length; i++) {
                ItemStack stack = mc.player.getItemBySlot(slots[i]);
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    int maxDmg = stack.getMaxDamage();
                    int remaining = maxDmg - stack.getDamageValue();
                    int pct = (int) ((remaining * 100.0) / maxDmg);
                    if (i > 0 && hasArmor) sb.append(" ");
                    sb.append(slotNames[i]).append(":").append(pct).append("%");
                    hasArmor = true;
                }
            }
            if (hasArmor) {
                fresh.add(sb.toString());
            }

            int width = 0;
            for (String line : fresh) {
                int w = mc.font.width(line);
                if (w > width) width = w;
            }
            cachedLines = fresh;
            cachedMaxW = width;
            lastBuildMs = nowMs;
        }

        java.util.List<String> lines = cachedLines;
        float scaledMaxW = cachedMaxW * scale;
        float totalH = lines.size() * scaledLineH + (lines.size() - 1) * 2 * scale;

        // 根据角落配置计算起始坐标
        Config.OverlayCorner corner = Config.getInfoHudCorner();
        float tx, ty;
        switch (corner) {
            case TOP_RIGHT    -> { tx = sw - margin - scaledMaxW - 6 * scale; ty = margin + 2 * scale; }
            case BOTTOM_LEFT  -> { tx = margin + 2 * scale;                   ty = sh - margin - totalH - 4 * scale; }
            case BOTTOM_RIGHT -> { tx = sw - margin - scaledMaxW - 6 * scale; ty = sh - margin - totalH - 4 * scale; }
            default           -> { tx = margin + 2 * scale;                   ty = margin + 2 * scale; }
        }

        // 绘制半透明背景
        int bgLeft = (int) (tx - 2 * scale);
        int bgTop = (int) (ty - 2 * scale);
        int bgRight = (int) (tx + scaledMaxW + 4 * scale);
        int bgBottom = (int) (ty + totalH + 2 * scale);
        g.fill(bgLeft, bgTop, bgRight, bgBottom, 0x66000000);

        // 逐行绘制
        var pose = g.pose();
        pose.pushPose();
        pose.translate(tx, ty, 0.0f);
        pose.scale(scale, scale, 1.0f);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = (i == 0) ? 0xFF55FF55   // 坐标：绿色
                    : (i == 1) ? 0xFF55FFFF      // 群系：青色
                    : (i == 2) ? 0xFFFFFF55      // CPS：黄色
                    : 0xFFFF8855;                // 耐久：橙色
            g.drawString(mc.font, line, 0, i * (lineH + 2), color, true);
        }

        pose.popPose();
    }
}
