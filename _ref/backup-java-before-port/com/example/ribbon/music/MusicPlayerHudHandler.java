package com.example.ribbon.music;

import com.example.ribbon.Config;
import com.example.ribbon.LangUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.io.File;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class MusicPlayerHudHandler {

    private MusicPlayerHudHandler() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (!Config.isMusicPlayerEnabled()) return;
        if (!Config.isMusicHudEnabled()) return;

        Mp3Player player = MusicPlayerManager.getPlayer();
        if (player.isStopped()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        File track = MusicLibrary.getTrack(MusicPlayerManager.getCurrentIndex());
        String name = track != null
                ? track.getName().replaceAll("(?i)\\.mp3$", "")
                : LangUtil.tr("music.unknown_track");

        String statusKey = player.isPaused() ? "music.paused" : "music.playing";
        String status = LangUtil.tr(statusKey);
        String text = "[" + status + "] " + name;

        int tw = mc.font.width(text);
        int th = mc.font.lineHeight;
        int m = 6;

        int x, y;
        switch (Config.getMusicHudCorner()) {
            case TOP_LEFT     -> { x = m;             y = m; }
            case TOP_RIGHT    -> { x = sw - tw - m;   y = m; }
            case BOTTOM_LEFT  -> { x = m;             y = sh - th - m; }
            default           -> { x = sw - tw - m;   y = sh - th - m; } // BOTTOM_RIGHT
        }

        g.drawString(mc.font, text, x, y, 0xFFFFFFFF, true);
    }
}