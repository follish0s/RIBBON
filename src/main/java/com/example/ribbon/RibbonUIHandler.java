package com.example.ribbon;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public class RibbonUIHandler {

    public static final KeyMapping ZOOM_KEY = new KeyMapping(
            "key.ribbon.zoom",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.ribbon"
    );

    // 播放/暂停：K
    public static final KeyMapping MUSIC_PLAY_PAUSE = new KeyMapping(
            "key.ribbon.music_play_pause",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.ribbon"
    );

    // 下一首：.
    public static final KeyMapping MUSIC_NEXT = new KeyMapping(
            "key.ribbon.music_next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            "key.categories.ribbon"
    );

    // 上一首：,
    public static final KeyMapping MUSIC_PREV = new KeyMapping(
            "key.ribbon.music_prev",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            "key.categories.ribbon"
    );

    // 打开音乐 UI：P
    public static final KeyMapping MUSIC_MENU = new KeyMapping(
            "key.ribbon.music_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "key.categories.ribbon"
    );

    @EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(ZOOM_KEY);
            event.register(MUSIC_PLAY_PAUSE);
            event.register(MUSIC_NEXT);
            event.register(MUSIC_PREV);
            event.register(MUSIC_MENU);
        }
    }
}