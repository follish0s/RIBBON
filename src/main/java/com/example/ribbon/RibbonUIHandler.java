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
            KeyMapping.Category.MISC
    );

    // 播放/暂停：K
    public static final KeyMapping MUSIC_PLAY_PAUSE = new KeyMapping(
            "key.ribbon.music_play_pause",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.MISC
    );

    // 下一首：.
    public static final KeyMapping MUSIC_NEXT = new KeyMapping(
            "key.ribbon.music_next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            KeyMapping.Category.MISC
    );

    // 上一首：,
    public static final KeyMapping MUSIC_PREV = new KeyMapping(
            "key.ribbon.music_prev",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            KeyMapping.Category.MISC
    );

    // 打开音乐 UI：P
    public static final KeyMapping MUSIC_MENU = new KeyMapping(
            "key.ribbon.music_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            KeyMapping.Category.MISC
    );

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