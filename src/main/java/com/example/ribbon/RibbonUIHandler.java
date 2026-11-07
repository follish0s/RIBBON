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

@EventBusSubscriber(modid = Ribbon.MODID, value = Dist.CLIENT)
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
        Screen current = event.getScreen();
        boolean titleMenu = current instanceof TitleScreen;
        // instanceof 编译期引用，杜绝字符串匹配静默失效
        boolean videoSettings = current instanceof net.minecraft.client.gui.screens.options.VideoSettingsScreen;
        if (!titleMenu && !videoSettings) return;

        // Init.Post 触发时 screen.width/height 尚未赋值，改用窗口的 GUI 缩放尺寸
        var window = Minecraft.getInstance().getWindow();
        int w = window.getGuiScaledWidth();
        int h = window.getGuiScaledHeight();
        int bx = w - 84;                      // 统一右下角（与主菜单一致，便于查找）
        int by = h - 26;
        Button button = Button.builder(
                        Component.literal("Ribbon 设置"),
                        b -> Minecraft.getInstance().gui.setScreen(
                                new EmbeddiumVideoOptionsScreen(current, EmbeddiumVideoOptionsScreen.makePages())))
                .bounds(bx, by, 80, 20)
                .build();
        event.addListener(button);
        com.mojang.logging.LogUtils.getLogger().info(
                "[Ribbon] 设置按钮已注入 {} @ ({},{})", current.getClass().getSimpleName(), bx, by);
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