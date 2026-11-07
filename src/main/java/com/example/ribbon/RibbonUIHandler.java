package com.example.ribbon;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.embeddedt.embeddium.impl.gui.EmbeddiumVideoOptionsScreen;
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

    /**
     * 26.2 原版视频设置不再提供 mod 标签页，主菜单需要一个可见入口。
     * 通过 ScreenEvent 动态添加按钮（纯事件，无 mixin）。
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        Screen current = event.getScreen();
        Button button = Button.builder(
                        Component.literal("Ribbon 设置"),
                        b -> Minecraft.getInstance().gui.setScreen(
                                new EmbeddiumVideoOptionsScreen(current, EmbeddiumVideoOptionsScreen.makePages())))
                .bounds(current.width - 84, current.height - 26, 80, 20)
                .build();
        event.addListener(button);
    }

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