package com.example.ribbon.compat;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** 1.21.1 {@code Screen.isSelectAll/isCopy/isPaste/isCut/hasControlDown} 在 26.2 已移除。 */
public final class KeyCompat {
    private KeyCompat() {
    }

    public static boolean hasControlDown(KeyEvent event) {
        return (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public static boolean hasShiftDown(KeyEvent event) {
        return (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    private static boolean plainCtrl(KeyEvent event) {
        return hasControlDown(event)
                && (event.modifiers() & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_ALT)) == 0;
    }

    public static boolean isSelectAll(KeyEvent event) {
        return event.key() == GLFW.GLFW_KEY_A && plainCtrl(event);
    }

    public static boolean isCopy(KeyEvent event) {
        return event.key() == GLFW.GLFW_KEY_C && plainCtrl(event);
    }

    public static boolean isPaste(KeyEvent event) {
        return event.key() == GLFW.GLFW_KEY_V && plainCtrl(event);
    }

    public static boolean isCut(KeyEvent event) {
        return event.key() == GLFW.GLFW_KEY_X && plainCtrl(event);
    }
}
