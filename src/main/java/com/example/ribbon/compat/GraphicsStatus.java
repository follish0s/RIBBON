package com.example.ribbon.compat;

/**
 * 1.21.1 {@code com.example.ribbon.compat.GraphicsStatus} 在 26.2 已被
 * {@code net.minecraft.client.GraphicsPreset} 取代。
 *
 * <p>为保留 Embeddium 画质选项页的编译形状而提供的过渡枚举；绑定到原版画质设置
 * 的部分需后续按 26.2 的 {@code Options.graphicsPreset()} 重写。
 */
public enum GraphicsStatus {
    FAST,
    FANCY,
    FABULOUS
}
