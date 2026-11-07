package com.example.ribbon;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 键位常量（纯 Minecraft 类，无平台依赖）。
 *
 * <p>26.2 双平台：拆出来是为了让 HUD/tick 等共享代码只依赖键位对象，
 * 而不把 {@code RibbonUIHandler}（含 NeoForge 屏幕事件）拉进 Fabric 的类加载链。
 * 注册由各平台的入口完成（NeoForge: RegisterKeyMappingsEvent / Fabric: KeyBindingHelper）。</p>
 */
public final class RibbonKeys {

    private RibbonKeys() {
    }

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
}
