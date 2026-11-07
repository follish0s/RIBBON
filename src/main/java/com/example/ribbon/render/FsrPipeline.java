package com.example.ribbon.render;

import com.example.ribbon.Config;
import com.example.ribbon.Ribbon;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.opengl.GL33C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@EventBusSubscriber(modid = Ribbon.MODID, value = Dist.CLIENT)
public final class FsrPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/FSR");
    private static volatile FsrPipeline instance;
    private static volatile boolean fallback = false;

    private int easuFbo;
    private int easuColorTex;
    private int easuWidth, easuHeight;
    private int easuProgram;
    private int easuULocInputSize;
    private int easuULocOutputSize;

    private int rcasProgram;
    private int rcasULocInputSize;
    private int rcasULocSharpness;

    private int quadVao;
    private boolean initialized = false;
    private boolean ready = false;

    private FsrPipeline() {}

    public static FsrPipeline getInstance() {
        if (instance == null) {
            synchronized (FsrPipeline.class) {
                if (instance == null) instance = new FsrPipeline();
            }
        }
        return instance;
    }

    // 🚨 终极完美时机：在 Post 阶段，所有的 HUD 和界面都已经画完了
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (!Config.isFsrEnabled()) return;  // 仅 FSR1 模式走此管线；FSR2 由 Fsr2Renderer 独立处理
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 🚨 绝杀：只要打开了任意界面（背包、箱子、ESC 菜单），直接跳过 FSR
        // 这样原版的界面绝对不会受任何污染，画面也绝对不会变空气！
        if (mc.screen != null) return;

        getInstance().render();
    }

    private void ensureInitialized() {
        if (ready || fallback || initialized) return;
        initialized = true;

        try {
            Minecraft mc = Minecraft.getInstance();
            easuWidth = mc.getWindow().getWidth();
            easuHeight = mc.getWindow().getHeight();

            easuColorTex = createColorTexture(easuWidth, easuHeight);
            easuFbo = GL33C.glGenFramebuffers();
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, easuFbo);
            GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, easuColorTex, 0);

            if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("EasuFBO 不完整");
            }
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, 0);

            String easuVert = loadShaderString("shaders/post/fsr_quad.vsh");
            String easuFrag = loadShaderString("shaders/post/fsr_easu.fsh");
            String rcasFrag = loadShaderString("shaders/post/fsr_rcas.fsh");

            easuProgram = compileProgram(easuVert, easuFrag, "EASU");
            rcasProgram = compileProgram(easuVert, rcasFrag, "RCAS");

            easuULocInputSize = GL33C.glGetUniformLocation(easuProgram, "u_inputSize");
            easuULocOutputSize = GL33C.glGetUniformLocation(easuProgram, "u_outputSize");
            rcasULocInputSize = GL33C.glGetUniformLocation(rcasProgram, "u_inputSize");
            rcasULocSharpness = GL33C.glGetUniformLocation(rcasProgram, "u_sharpness");

            quadVao = GL33C.glGenVertexArrays();
            ready = true;
            LOGGER.info("[FSR] 管线初始化完成 (HDR 支持开启)");
        } catch (Exception e) {
            LOGGER.error("[FSR] 初始化失败，启用 Fallback", e);
            triggerFallback();
        }
    }

    private static int createColorTexture(int width, int height) {
        int tex = GL33C.glGenTextures();
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, tex);
        GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D, 0, GL33C.GL_RGBA16F, width, height, 0, GL33C.GL_RGBA, GL33C.GL_FLOAT, (java.nio.ByteBuffer) null);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MIN_FILTER, GL33C.GL_LINEAR);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_MAG_FILTER, GL33C.GL_LINEAR);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_S, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D, GL33C.GL_TEXTURE_WRAP_T, GL33C.GL_CLAMP_TO_EDGE);
        GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
        return tex;
    }

    private int compileProgram(String vertSrc, String fragSrc, String label) {
        int vert = compileShader(GL33C.GL_VERTEX_SHADER, vertSrc, label + "-vert");
        int frag = compileShader(GL33C.GL_FRAGMENT_SHADER, fragSrc, label + "-frag");
        if (vert == 0 || frag == 0) return 0;
        int program = GL33C.glCreateProgram();
        GL33C.glAttachShader(program, vert);
        GL33C.glAttachShader(program, frag);
        GL33C.glLinkProgram(program);
        if (GL33C.glGetProgrami(program, GL33C.GL_LINK_STATUS) == GL33C.GL_FALSE) {
            LOGGER.error("[FSR] {} 程序链接失败: {}", label, GL33C.glGetProgramInfoLog(program));
            GL33C.glDeleteProgram(program);
            return 0;
        }
        GL33C.glDeleteShader(vert);
        GL33C.glDeleteShader(frag);
        return program;
    }

    private int compileShader(int type, String source, String label) {
        int shader = GL33C.glCreateShader(type);
        GL33C.glShaderSource(shader, source);
        GL33C.glCompileShader(shader);
        if (GL33C.glGetShaderi(shader, GL33C.GL_COMPILE_STATUS) == GL33C.GL_FALSE) {
            LOGGER.error("[FSR] {} 编译失败: {}", label, GL33C.glGetShaderInfoLog(shader));
            GL33C.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private String loadShaderString(String resourcePath) throws IOException {
        var resourceLocation = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Ribbon.MODID, resourcePath);
        var resource = Minecraft.getInstance().getResourceManager().getResource(resourceLocation)
                .orElseThrow(() -> new IOException("找不到着色器资源: " + resourceLocation));
        try (InputStream is = resource.open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void render() {
        try {
            ensureInitialized();
            if (!ready) return;

            Minecraft mc = Minecraft.getInstance();
            int mainFbo = mc.getMainRenderTarget().frameBufferId;
            int sceneTex = mc.getMainRenderTarget().getColorTextureId();

            if (sceneTex == 0) return;

            // 1. 保存状态
            int[] vp = new int[4];
            GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, vp);
            int[] colorMask = new int[4];
            GL33C.glGetIntegerv(GL33C.GL_COLOR_WRITEMASK, colorMask);
            int[] depthMaskArr = new int[1];
            GL33C.glGetIntegerv(GL33C.GL_DEPTH_WRITEMASK, depthMaskArr);
            boolean depthMask = depthMaskArr[0] != 0;
            boolean depthTest = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST);
            boolean blend = GL33C.glIsEnabled(GL33C.GL_BLEND);
            boolean cull = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);

            // 2. 设置纯净渲染状态
            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._depthMask(false);
            GlStateManager._disableDepthTest();
            GlStateManager._disableBlend();
            GlStateManager._disableCull();

            // Pass 1: EASU
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, easuFbo);
            GL33C.glViewport(0, 0, easuWidth, easuHeight);
            GL33C.glUseProgram(easuProgram);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, sceneTex);
            GL33C.glUniform2f(easuULocInputSize, easuWidth, easuHeight);
            GL33C.glUniform2f(easuULocOutputSize, easuWidth, easuHeight);
            drawFullscreenTriangle();

            // Pass 2: RCAS
            GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, mainFbo);
            GL33C.glViewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
            GL33C.glUseProgram(rcasProgram);
            GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, easuColorTex);
            GL33C.glUniform2f(rcasULocInputSize, easuWidth, easuHeight);
            GL33C.glUniform1f(rcasULocSharpness, (float) Config.getFsrSharpness());
            drawFullscreenTriangle();

            // 3. 恢复状态
            GL33C.glUseProgram(0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
            GL33C.glViewport(vp[0], vp[1], vp[2], vp[3]);

            GlStateManager._colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            GlStateManager._depthMask(depthMask);
            if (depthTest) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
            if (blend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
            if (cull) GlStateManager._enableCull(); else GlStateManager._disableCull();

            // 强制绑定回主帧缓冲
            mc.getMainRenderTarget().bindWrite(true);

        } catch (Exception e) {
            LOGGER.error("[FSR] 渲染异常，启用 Fallback", e);
            triggerFallback();
            try {
                GL33C.glUseProgram(0);
                GlStateManager._colorMask(true, true, true, true);
                GlStateManager._depthMask(true);
                Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            } catch (Exception ignored) {}
        }
    }

    private void drawFullscreenTriangle() {
        GL33C.glBindVertexArray(quadVao);
        GL33C.glDrawArrays(GL33C.GL_TRIANGLES, 0, 3);
        GL33C.glBindVertexArray(0);
    }

    private void triggerFallback() {
        fallback = true;
        ready = false;
    }
}