package com.example.ribbon.lunar;

import com.example.ribbon.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 农历 HUD 常驻显示。
 */
@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class LunarHudHandler {

    private LunarHudHandler() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (!Config.isLunarHudEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        LunarDateInfo info = LunarDateProvider.today();
        if (info.lunarMonth == 0) return;

        StringBuilder sb = new StringBuilder();
        sb.append(info.formatLunarDate());
        if (Config.isShowFestival()) {
            String festivalName = info.getFestivalDisplayName();
            if (festivalName != null) {
                sb.append(" · ").append(festivalName);
            }
        }
        if (Config.isShowSolarTerm() && info.solarTerm != null) {
            sb.append(" · ").append(info.solarTerm);
        }
        String text = sb.toString();

        int color = pickColor(info);
        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = mc.font.width(text);
        int th = mc.font.lineHeight;
        float s = (float) Config.getLunarHudScale();
        int m = 4;

        float tx, ty;
        switch (Config.getLunarHudCorner()) {
            case TOP_RIGHT    -> { tx = sw - m - tw * s; ty = m; }
            case BOTTOM_LEFT  -> { tx = m;             ty = sh - m - th * s; }
            case BOTTOM_RIGHT -> { tx = sw - m - tw * s; ty = sh - m - th * s; }
            default           -> { tx = m;             ty = m; }
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(tx, ty, 0.0f);
        pose.scale(s, s, 1.0f);
        g.drawString(mc.font, text, 0, 0, color, true);
        pose.popPose();
    }

    /** 按节日等级选颜色 */
    private static int pickColor(LunarDateInfo info) {
        return switch (info.festivalTier) {
            case GRAND -> 0xFFFF4444;   // 金红（春节/除夕）
            case MAJOR -> 0xFFFFAA00;   // 橙（中秋/端午等）
            case MINOR -> 0xFFFFDD88;   // 淡黄
            case NONE  -> 0xFFFFFFFF;   // 白
        };
    }
}