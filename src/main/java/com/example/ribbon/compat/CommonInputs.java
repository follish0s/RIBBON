package com.example.ribbon.compat;

import org.lwjgl.glfw.GLFW;

/** 1.21.1 {@code com.example.ribbon.compat.CommonInputs} 在 26.2 已移除。 */
public final class CommonInputs {
    private CommonInputs() {
    }

    public static boolean selected(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE;
    }
}
