package com.example.ribbon.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * 1.21.1 {@code GuiGraphics} 的 26.2 兼容桥。
 *
 * <p>26.2 把 GUI 改成 render-state 提取架构，绘制入口从 {@code GuiGraphics} 变为
 * {@link GuiGraphicsExtractor}（参考 Sodium 26.2 的 {@code extractRenderState} 迁移方式）。
 * 本类把旧代码的调用形状转发到新 API，避免逐文件重写上百个绘制点。
 *
 * <p>注意：这是「形状兼容」而非逐像素等价；旧版 setColor 的全局 tint 以颜色乘算近似，
 * 旧版 flush 在 retained 架构下为空操作。
 */
public final class GuiGraphics {
    private final GuiGraphicsExtractor graphics;
    private final PoseCompat pose;
    private float tintR = 1.0f;
    private float tintG = 1.0f;
    private float tintB = 1.0f;
    private float tintA = 1.0f;

    private GuiGraphics(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
        this.pose = new PoseCompat(graphics.pose());
    }

    public static GuiGraphics wrap(GuiGraphicsExtractor graphics) {
        return new GuiGraphics(graphics);
    }

    public GuiGraphicsExtractor unwrap() {
        return this.graphics;
    }

    private int tint(int argb) {
        if (this.tintR == 1.0f && this.tintG == 1.0f && this.tintB == 1.0f && this.tintA == 1.0f) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * this.tintA);
        int r = Math.round(((argb >>> 16) & 0xFF) * this.tintR);
        int g = Math.round(((argb >>> 8) & 0xFF) * this.tintG);
        int b = Math.round((argb & 0xFF) * this.tintB);
        return (Math.min(255, a) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // ===== 基础绘制 =====

    public void fill(int x0, int y0, int x1, int y1, int color) {
        this.graphics.fill(x0, y0, x1, y1, tint(color));
    }

    public void outline(int x, int y, int width, int height, int color) {
        this.graphics.outline(x, y, width, height, tint(color));
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int colorFrom, int colorTo) {
        this.graphics.fillGradient(x0, y0, x1, y1, tint(colorFrom), tint(colorTo));
    }

    public void horizontalLine(int x0, int x1, int y, int color) {
        this.graphics.horizontalLine(x0, x1, y, tint(color));
    }

    public void verticalLine(int x, int y0, int y1, int color) {
        this.graphics.verticalLine(x, y0, y1, tint(color));
    }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        this.graphics.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor() {
        this.graphics.disableScissor();
    }

    // ===== 文本 =====

    public int drawString(Font font, String text, int x, int y, int color) {
        this.graphics.text(font, text, x, y, tint(color));
        return x + font.width(text);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        this.graphics.text(font, text, x, y, tint(color), dropShadow);
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color) {
        this.graphics.text(font, text, x, y, tint(color));
        return x + font.width(text);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean dropShadow) {
        this.graphics.text(font, text, x, y, tint(color), dropShadow);
        return x + font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.graphics.text(font, text, x, y, tint(color));
        return x + font.width(text);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
        this.graphics.text(font, text, x, y, tint(color), dropShadow);
        return x + font.width(text);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        this.graphics.centeredText(font, text, x, y, tint(color));
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        this.graphics.centeredText(font, text, x, y, tint(color));
    }

    // ===== 纹理 =====

    /** 1.21.1 的 11 参 blit（含 uWidth/vHeight），uWidth/vHeight 在 26.2 由 width/height 承载。 */
    public void blit(Identifier location, int x, int y, int width, int height,
                     float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
        this.graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, u, v,
                width, height, textureWidth, textureHeight);
    }

    /** 1.21.1 的 9 参 blit。 */
    public void blit(Identifier location, int x, int y, float u, float v,
                     int width, int height, int textureWidth, int textureHeight) {
        this.graphics.blit(RenderPipelines.GUI_TEXTURED, location, x, y, u, v,
                width, height, textureWidth, textureHeight);
    }


    public void renderItem(ItemStack stack, int x, int y) {
        this.graphics.item(stack, x, y);
    }

    // ===== 状态 =====

    public void setColor(float r, float g, float b, float a) {
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
        this.tintA = a;
    }

    /** 26.2 为 retained 模式，无需手动 flush。 */
    public void flush() {
        // no-op
    }

    public PoseCompat pose() {
        return this.pose;
    }

    public int guiWidth() {
        return this.graphics.guiWidth();
    }

    public int guiHeight() {
        return this.graphics.guiHeight();
    }

    public void renderTooltip(Font font, List<Component> lines, int x, int y) {
        this.graphics.setComponentTooltipForNextFrame(font, lines, x, y);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        this.graphics.setComponentTooltipForNextFrame(font, lines, x, y);
    }

    /** 把旧 PoseStack 的调用形状映射到 26.2 的 2D 变换栈。 */
    public static final class PoseCompat {
        private final Matrix3x2fStack stack;

        private PoseCompat(Matrix3x2fStack stack) {
            this.stack = stack;
        }

        public Matrix3x2fStack stack() {
            return this.stack;
        }

        public PoseCompat pushPose() {
            this.stack.pushMatrix();
            return this;
        }

        public PoseCompat popPose() {
            this.stack.popMatrix();
            return this;
        }

        public PoseCompat translate(float x, float y, float z) {
            this.stack.translate(x, y);
            return this;
        }

        public PoseCompat translate(double x, double y, double z) {
            this.stack.translate((float) x, (float) y);
            return this;
        }

        public PoseCompat scale(float x, float y, float z) {
            this.stack.scale(x, y);
            return this;
        }
    }
}
