package com.example.ribbon.lunar;

import com.example.ribbon.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.time.LocalDate;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class FestivalTitleHandler {

    private static LocalDate lastShownDate = null;
    private static Component currentTitle = null;
    private static long startTime = 0;
    private static boolean visible = false;

    private static final long DURATION_MS = 5000;

    private FestivalTitleHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.isFestivalTitleEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        LocalDate today = LocalDate.now();
        if (today.equals(lastShownDate)) return;

        LunarDateInfo info = LunarDateProvider.today();
        if (info.festivalId == null) return;

        if (info.festivalTier != LunarDateInfo.FestivalTier.MAJOR
                && info.festivalTier != LunarDateInfo.FestivalTier.GRAND) {
            return;
        }

        lastShownDate = today;
        currentTitle = Component.literal(info.getFestivalDisplayName());
        startTime = System.currentTimeMillis();
        visible = true;
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (!visible || currentTitle == null) return;
        if (!Config.isFestivalTitleEnabled()) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > DURATION_MS) {
            visible = false;
            return;
        }

        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        float alpha;
        if (elapsed < 500) alpha = elapsed / 500.0f;
        else if (elapsed > DURATION_MS - 500) alpha = (DURATION_MS - elapsed) / 500.0f;
        else alpha = 1.0f;
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);

        LunarDateInfo info = LunarDateProvider.today();
        int baseColor = switch (info.festivalTier) {
            case GRAND -> 0xFF4444;
            case MAJOR -> 0xFFAA00;
            default    -> 0xFFDD88;
        };
        int alphaInt = (int) (alpha * 255);
        int titleColor = (alphaInt << 24) | baseColor;

        int y = sh / 3;
        int textWidth = mc.font.width(currentTitle);
        g.drawString(mc.font, currentTitle, (sw - textWidth) / 2, y, titleColor, true);
    }
}