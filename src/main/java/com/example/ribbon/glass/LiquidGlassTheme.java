/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.glass;

import com.example.ribbon.optimization.RuntimeConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import com.example.ribbon.compat.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.embeddedt.embeddium.impl.Embeddium;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 液态玻璃主题渲染器（Apple Liquid Glass 风格）。
 *
 * <p>设计参数参考 Liquid Glass 设计语言（白色底 tint 5%-15%、1px 镜面边框、
 * 对角 sheen 高光、强背景模糊 + 约 30% 暗化保证文字可读性）。</p>
 *
 * <p>实现方式：启动后首次绘制时在渲染线程生成两张程序化纹理（圆角玻璃面板
 * 9-patch + 对角高光带），之后每帧只是少量 blit + fill，无 GL 状态泄漏。</p>
 *
 * <p>线程约定：所有公开方法只能在渲染线程调用（GUI 渲染天然满足）。</p>
 */
public final class LiquidGlassTheme {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiquidGlassTheme.class);

    private static final Identifier PANEL_LOCATION =
            Identifier.fromNamespaceAndPath("ribbon", "textures/gui/liquid_glass_panel");
    private static final Identifier SHEEN_LOCATION =
            Identifier.fromNamespaceAndPath("ribbon", "textures/gui/liquid_glass_sheen");

    private static final int TEX_SIZE = 64;
    private static final int SHEEN_SIZE = 128;
    /** 9-patch 角块边长（纹理像素）。 */
    private static final int CORNER = 8;
    /** 面板圆角半径（纹理像素）。 */
    private static final float RADIUS = 6.0f;

    private static volatile boolean initialized;
    private static volatile boolean failed;

    private LiquidGlassTheme() {
    }

    /** 液态玻璃总开关是否生效（配置开启 + 纹理已就绪）。 */
    public static boolean isActive() {
        return RuntimeConfig.liquidGlassEnabled && initialized;
    }

    /**
     * 惰性初始化两张程序化纹理。失败时永久降级（{@link #isActive()} 返回 false），
     * 调用方回退到原版绘制路径，绝不阻塞 GUI。
     */
    private static void ensureInitialized() {
        if (initialized || failed) {
            return;
        }
        synchronized (LiquidGlassTheme.class) {
            if (initialized || failed) {
                return;
            }
            try {
                Minecraft mc = Minecraft.getInstance();
                DynamicTexture panel = new DynamicTexture(() -> "ribbon-dynamic", buildPanelImage());
                panel.setFilter(true, false);
                mc.getTextureManager().register(PANEL_LOCATION, panel);
                DynamicTexture sheen = new DynamicTexture(() -> "ribbon-dynamic", buildSheenImage());
                sheen.setFilter(true, false);
                mc.getTextureManager().register(SHEEN_LOCATION, sheen);
                initialized = true;
                LOGGER.info("[Ribbon] Liquid glass textures generated (panel 64x64, sheen 128x128)");
            } catch (Throwable t) {
                failed = true;
                LOGGER.warn("[Ribbon] Liquid glass init failed; effect disabled for this session", t);
            }
        }
    }

    /**
     * 生成玻璃面板纹理：白色渐变底 + 1px 镜面亮边（顶部最强）+ 内圈柔光 + AA 圆角。
     * 颜色恒为白色，形态全部由 alpha 承载，便于绘制时整体调透明度。
     */
    private static NativeImage buildPanelImage() {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, false);
        float half = TEX_SIZE / 2.0f;
        float inner = half - RADIUS;
        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                // 圆角矩形 SDF（像素中心坐标系）
                float px = x + 0.5f - half;
                float py = y + 0.5f - half;
                float qx = Math.abs(px) - inner;
                float qy = Math.abs(py) - inner;
                float outX = Math.max(qx, 0.0f);
                float outY = Math.max(qy, 0.0f);
                float distOutside = (float) Math.sqrt(outX * outX + outY * outY);
                float signedDist = distOutside + Math.min(Math.max(qx, qy), 0.0f) - RADIUS;
                float inside = -signedDist; // >0 表示在形状内部，0 为边界
                float coverage = clamp01(0.5f + inside); // 1px 抗锯齿

                float v = y / (float) (TEX_SIZE - 1);
                // 底 tint：白 44 → 22（≈ 17% → 9%），符合深色模式玻璃 5%-15% 区间
                float alpha = 44.0f - 22.0f * v;
                // 镜面边框：最外 1px，顶部 115 → 底部 60（顶部受光最强）
                float rimStrength = clamp01(1.5f - inside);
                alpha += rimStrength * (115.0f - 55.0f * v);
                // 内圈柔光线（约 2px 处），顶部更明显
                float innerLine = clamp01(1.0f - Math.abs(2.0f - inside));
                alpha += innerLine * (30.0f - 16.0f * v);

                int a = (int) clamp255(alpha * coverage);
                image.setPixel(x, y, packAbgr(255, 255, 255, a));
            }
        }
        return image;
    }

    /**
     * 生成对角高光带纹理（sheen）：沿 135° 对角的单条柔和白色光带，
     * 绘制时以叠加混合 + 裁剪缓慢扫过面板，模拟玻璃表面的动态镜面反射。
     */
    private static NativeImage buildSheenImage() {
        NativeImage image = new NativeImage(SHEEN_SIZE, SHEEN_SIZE, false);
        float diagonal = 2.0f * (SHEEN_SIZE - 1);
        for (int y = 0; y < SHEEN_SIZE; y++) {
            for (int x = 0; x < SHEEN_SIZE; x++) {
                float t = (x + y) / diagonal; // 0..1，沿对角推进
                float d = (t - 0.5f) / 0.17f;
                float gaussian = (float) Math.exp(-d * d);
                int a = (int) (92.0f * gaussian);
                image.setPixel(x, y, packAbgr(255, 255, 255, a));
            }
        }
        return image;
    }

    /**
     * 绘制一块玻璃面板。面板很小（放不下 9-patch）时退化为半透明白色填充。
     *
     * @param alphaMul 整体透明度倍率（悬停增强 / 禁用减弱）
     * @return 是否成功绘制（false = 纹理未就绪，调用方应走原版路径）
     */
    public static boolean drawPanel(GuiGraphics gfx, int x1, int y1, int x2, int y2, float alphaMul) {
        ensureInitialized();
        if (!initialized) {
            return false;
        }
        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0) {
            return true;
        }
        float alpha = Math.min(Math.max(alphaMul, 0.0f), 1.8f);
        com.example.ribbon.compat.LegacyRenderState.enableBlend();
        com.example.ribbon.compat.LegacyRenderState.defaultBlendFunc();
        if (width < 2 * CORNER || height < 2 * CORNER) {
            gfx.fill(x1, y1, x2, y2, scaleAlpha(0x32FFFFFF, alpha));
            return true;
        }
        gfx.setColor(1.0f, 1.0f, 1.0f, alpha);
        blitNineSlice(gfx, PANEL_LOCATION, x1, y1, width, height);
        gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        return true;
    }

    /**
     * 在面板范围内绘制缓慢漂移的镜面高光带（叠加混合 + 裁剪）。
     * 光带位置按 16s 周期沿对角往返，安静不抢眼。
     */
    public static void renderSheen(GuiGraphics gfx, int x, int y, int width, int height) {
        if (!initialized || width <= 0 || height <= 0) {
            return;
        }
        float phase = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() * (Math.PI * 2.0 / 16000.0));
        int size = (int) (Math.max(width, height) * 2.2f);
        int sx = x + (int) ((width - size) * phase);
        int sy = y + (int) ((height - size) * (1.0f - phase));
        gfx.enableScissor(x, y, x + width, y + height);
        com.example.ribbon.compat.LegacyRenderState.enableBlend();
        com.example.ribbon.compat.LegacyRenderState.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        gfx.setColor(1.0f, 1.0f, 1.0f, 0.55f);
        gfx.blit(SHEEN_LOCATION, sx, sy, size, size, 0.0f, 0.0f, SHEEN_SIZE, SHEEN_SIZE, SHEEN_SIZE, SHEEN_SIZE);
        com.example.ribbon.compat.LegacyRenderState.defaultBlendFunc();
        gfx.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        gfx.disableScissor();
    }

    /**
     * 玻璃背后的中性暗化层（约 27% → 18% 垂直渐变），
     * 保证亮场景（雪原、沙漠）下白字依然可读。
     */
    public static void renderScrim(GuiGraphics gfx, int width, int height) {
        com.example.ribbon.compat.LegacyRenderState.enableBlend();
        com.example.ribbon.compat.LegacyRenderState.defaultBlendFunc();
        gfx.fillGradient(0, 0, width, height, 0x46000000, 0x2B000000);
    }

    private static void blitNineSlice(GuiGraphics gfx, Identifier location, int x, int y, int width, int height) {
        int c = CORNER;
        int inner = TEX_SIZE - 2 * c;
        // 四角（1:1 像素，保证圆角锐利）
        gfx.blit(location, x, y, c, c, 0, 0, c, c, TEX_SIZE, TEX_SIZE);
        gfx.blit(location, x + width - c, y, c, c, TEX_SIZE - c, 0, c, c, TEX_SIZE, TEX_SIZE);
        gfx.blit(location, x, y + height - c, c, c, 0, TEX_SIZE - c, c, c, TEX_SIZE, TEX_SIZE);
        gfx.blit(location, x + width - c, y + height - c, c, c, TEX_SIZE - c, TEX_SIZE - c, c, c, TEX_SIZE, TEX_SIZE);
        // 四边（拉伸）
        gfx.blit(location, x + c, y, width - 2 * c, c, c, 0, inner, c, TEX_SIZE, TEX_SIZE);
        gfx.blit(location, x + c, y + height - c, width - 2 * c, c, c, TEX_SIZE - c, inner, c, TEX_SIZE, TEX_SIZE);
        gfx.blit(location, x, y + c, c, height - 2 * c, 0, c, c, inner, TEX_SIZE, TEX_SIZE);
        gfx.blit(location, x + width - c, y + c, c, height - 2 * c, TEX_SIZE - c, c, c, inner, TEX_SIZE, TEX_SIZE);
        // 中心（拉伸）
        gfx.blit(location, x + c, y + c, width - 2 * c, height - 2 * c, c, c, inner, inner, TEX_SIZE, TEX_SIZE);
    }

    private static int scaleAlpha(int argb, float multiplier) {
        int a = (int) ((argb >>> 24) * multiplier);
        a = Math.min(255, Math.max(0, a));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** NativeImage 期望 ABGR 打包（Mojang 惯例）。 */
    private static int packAbgr(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static float clamp01(float v) {
        return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
    }

    private static float clamp255(float v) {
        return v < 0.0f ? 0.0f : (v > 255.0f ? 255.0f : v);
    }
}
