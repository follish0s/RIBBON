package com.example.ribbon.compat;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * 1.21.1 GL 状态方法在 26.2 的过渡兼容层。
 *
 * <p><b>重要</b>：26.2 把混合/深度/裁剪/shader 状态全部收归 {@code RenderPipeline} 与
 * {@code GpuDevice}，旧的 {@code RenderSystem.*} / {@code GlStateManager._*} 立即式状态
 * 调用已不存在。这里提供空实现只为让功能层源码能够编译；依赖这些调用的特效
 * （液态玻璃叠加、LOD 混合等）需要后续按 26.2 的 RenderPipeline 语义重写才能真正生效。
 */
public final class LegacyRenderState {
    private LegacyRenderState() {
    }

    public static void enableBlend() {
    }

    public static void disableBlend() {
    }

    public static void defaultBlendFunc() {
    }

    public static void blendFunc(Object... ignored) {
    }

    public static void blendFuncSeparate(Object... ignored) {
    }

    public static void depthMask(boolean ignored) {
    }

    public static void depthFunc(int ignored) {
    }

    public static void enableDepthTest() {
    }

    public static void disableDepthTest() {
    }

    public static void enableCull() {
    }

    public static void disableCull() {
    }

    public static void enableScissor(int x0, int y0, int x1, int y1) {
    }

    public static void disableScissor() {
    }

    public static void setShaderColor(float r, float g, float b, float a) {
    }

    public static void setShader(Object ignored) {
    }

    public static void setShaderFogStart(float ignored) {
    }

    public static void setShaderFogEnd(float ignored) {
    }

    public static float getShaderFogStart() {
        return 0.0f;
    }

    public static float getShaderFogEnd() {
        return 0.0f;
    }

    public static float[] getShaderFogColor() {
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    public static Object getShaderFogShape() {
        return null;
    }

    public static int getShaderTexture(int unit) {
        return 0;
    }

    public static Matrix4f getProjectionMatrix() {
        return new Matrix4f();
    }

    public static void setProjectionMatrix(Object ignored) {
    }

    public static void activeTexture(int ignored) {
    }

    public static void bindTexture(int ignored) {
    }

    public static void colorMask(boolean r, boolean g, boolean b, boolean a) {
    }

    public static int glGetProgrami(int program, int pname) {
        return 0;
    }

    public static int glGetShaderi(int shader, int pname) {
        return 0;
    }

    /**
     * 1.21.1 的 {@code Options.hideGui}（F1）在 26.2 已移除，状态改由 Gui 内部维护。
     * 这里返回 false 表示“不隐藏”，属功能降级；后续应改为读取 26.2 的 Gui 隐藏状态。
     */
    public static boolean isGuiHidden(Minecraft mc) {
        return false;
    }
}
