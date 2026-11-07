package com.example.ribbon.music;

import com.example.ribbon.Config;
import com.example.ribbon.RibbonUIHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class MusicPlayerTickHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/MusicTick");

    private MusicPlayerTickHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.isMusicPlayerEnabled()) {
            MusicPlayerManager.getPlayer().stop();
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        while (RibbonUIHandler.MUSIC_MENU.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_MENU 按下，screen={}", mc.screen);
            if (mc.screen == null) {
                try {
                    MusicPlayerScreen screen = new MusicPlayerScreen();
                    mc.setScreen(screen);
                    LOGGER.info("[Ribbon] 音乐 UI 已打开");
                } catch (Throwable t) {
                    LOGGER.error("[Ribbon] 打开音乐 UI 失败", t);
                }
            }
        }

        while (RibbonUIHandler.MUSIC_PLAY_PAUSE.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_PLAY_PAUSE 按下");
            MusicPlayerManager.playPause();
        }
        while (RibbonUIHandler.MUSIC_NEXT.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_NEXT 按下");
            MusicPlayerManager.next();
        }
        while (RibbonUIHandler.MUSIC_PREV.consumeClick()) {
            LOGGER.info("[Ribbon] MUSIC_PREV 按下");
            MusicPlayerManager.prev();
        }
    }
}