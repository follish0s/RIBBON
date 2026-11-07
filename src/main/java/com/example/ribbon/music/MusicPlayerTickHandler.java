package com.example.ribbon.music;

import com.example.ribbon.RibbonKeys;
import com.example.ribbon.Config;
import com.example.ribbon.RibbonUIHandler;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MusicPlayerTickHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/MusicTick");

    private MusicPlayerTickHandler() {}

    public static void onClientTick() {
        if (!Config.isMusicPlayerEnabled()) {
            MusicPlayerManager.getPlayer().stop();
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        while (RibbonKeys.MUSIC_MENU.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_MENU 按下，screen={}", mc.gui.screen());
            if (mc.gui.screen() == null) {
                try {
                    MusicPlayerScreen screen = new MusicPlayerScreen();
                    mc.setScreenAndShow(screen);
                    LOGGER.info("[Ribbon] 音乐 UI 已打开");
                } catch (Throwable t) {
                    LOGGER.error("[Ribbon] 打开音乐 UI 失败", t);
                }
            }
        }

        while (RibbonKeys.MUSIC_PLAY_PAUSE.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_PLAY_PAUSE 按下");
            MusicPlayerManager.playPause();
        }
        while (RibbonKeys.MUSIC_NEXT.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_NEXT 按下");
            MusicPlayerManager.next();
        }
        while (RibbonKeys.MUSIC_PREV.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_PREV 按下");
            MusicPlayerManager.prev();
        }
    }
}