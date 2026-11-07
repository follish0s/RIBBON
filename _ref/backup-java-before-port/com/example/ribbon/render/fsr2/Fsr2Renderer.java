/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.render.fsr2;

import com.example.ribbon.Config;
import com.example.ribbon.Ribbon;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL46C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * FSR 2.2 时域超分辨率渲染器（编排器）。
 *
 * <p>每帧流程（在 {@code LevelRenderer.renderLevel} TAIL 由
 * {@link com.example.ribbon.mixin.MixinLevelRendererFsr2} 派发）：</p>
 * <ol>
 *   <li>{@link Fsr2MotionVectors#dispatch} — 用抖动 VP 与深度生成屏幕空间速度纹理；</li>
 *   <li>累积 Pass（fsr2_accumulate.comp）— 用速度重投影历史帧，3×3 邻域 clamp
 *       抑制拖影，按 {@link Config#getFsrHistoryWeight()} 与当前帧混合，写入新历史；</li>
 *   <li>重建 Pass（fsr2_reconstruct.comp）— RCAS 风格锐化，写入中间纹理；</li>
 *   <li>{@code glBlitFramebuffer} 把中间纹理拷进主帧缓冲——之后持拿物品与 GUI
 *       在已上采样的帧上绘制，保持 UI 锐利。</li>
 * </ol>
 *
 * <h2>渲染分辨率</h2>
 * 用 {@code mc.getMainRenderTarget().width/height} 作为渲染尺寸——即便
 * Iris/Embeddium 把主目标缩放到非窗口尺寸也能正确处理。
 * 真正的"低分辨率渲染→高分辨率上采样"需要主目标本身被缩小（由渲染缩放
 * 集成完成，超出本框架范围）；当 {@link Config#getFsrRenderScale()} == 1 时
 * 本管线等价于带锐化的时域抗锯齿（TAA），仍能显著减少锯齿与闪烁。
 *
 * <h2>GL 要求</h2>
 * 需要 OpenGL 4.6（实际计算只需 4.3，但为统一用 GL46C 静态导入并保证
 * image2D/layout(binding) 等特性可用，要求 4.6）。不满足时静默禁用。
 */
public final class Fsr2Renderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/FSR2");
    private static final int WORKGROUP = 8;
    /** 速度超过这么多"渲染像素"时，累积把历史权重降到 0（避免拖影）。 */
    private static final float VELOCITY_CLAMP_PIX = 8.0F;
    /** 速度（UV 单位）超过此阈值时强制丢弃历史，直接显示当前帧（防拖影）。 */
    private static final float MAX_VELOCITY_LENGTH_UV = 0.1F;
    /** AABB 钳制盒收缩系数：越接近 1 裁剪越紧，越能消灭高频闪烁与拖影。 */
    private static final float CLIP_BOX_SHRINK = 0.95F;

    // 程序
    private static int accumulateProgram;
    private static int reconstructProgram;
    // 累积 uniform
    private static int uAcc_currentColor, uAcc_velocity, uAcc_historyPrev;
    private static int uAcc_renderSize, uAcc_displaySize, uAcc_historyWeight;
    private static int uAcc_firstFrame, uAcc_velocityClampPix;
    private static int uAcc_maxVelocityLength, uAcc_clipBoxShrink;
    // 重建 uniform
    private static int uRec_history, uRec_displaySize, uRec_sharpness;

    // 历史纹理（ping-pong）
    private static int[] historyTex = new int[2];
    private static int histRead = 0;   // 本帧读取的历史索引；写入 1-histRead
    // 中间（锐化输出）纹理 + FBO
    private static int intermediateTex;
    private static int intermediateFbo;

    private static int texW, texH;
    private static boolean initialized = false;
    private static boolean fallback = false;     // 编译/初始化失败，永久禁用
    private static boolean computeSupported = false;
    private static boolean computeChecked = false;

    /** 首帧或刚失效时为 true，累积直接采用当前帧（不读历史）。 */
    private static boolean firstFrame = true;

    private Fsr2Renderer() {
    }

    /**
     * 由 MixinGameRendererJitter 在 renderLevel HEAD 调用。
     *
     * 【回滚说明】相机亚像素抖动（Jitter）已彻底移除——任何形式的投影矩阵注入
     * 都会破坏视锥体剔除（区块消失/闪烁）与 Iris/Oculus 阴影 Pass（阴影抽动）。
     * 失去 Jitter 后，FSR2 退化为纯时域抗锯齿（TAA）：速度纹理仍由相机运动
     * （旋转 + 平移）驱动，历史累积照常工作，画质依然优于 FSR 1.0。
     * 此处可在此检测瞬移清历史。
     */
    public static void onFrameBegin() {
        // 当前实现：依赖累积着色器的越界回退处理大位移；无需额外清历史。
    }

    /** 主入口：处理当前帧。 */
    public static void processFrame() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            RenderTarget mainRT = mc.getMainRenderTarget();
            if (mainRT == null || mainRT.getColorTextureId() == 0) {
                return;
            }
            if (fallback || !checkComputeSupport()) {
                return;
            }

            int w = mainRT.width;
            int h = mainRT.height;
            if (w <= 0 || h <= 0) {
                return;
            }

            if (!ensureInitialized(w, h)) {
                return;
            }

            int colorTex = mainRT.getColorTextureId();
            int depthTex = mainRT.getDepthTextureId();
            int mainFbo = mainRT.frameBufferId;

            // 1) 运动矢量
            if (!Fsr2MotionVectors.dispatch(w, h, depthTex)) {
                LOGGER.warn("[FSR2] 运动矢量 Pass 失败，本帧跳过");
                return;
            }

            // 2) 累积
            runAccumulate(w, h, colorTex);

            // 3) 重建（锐化）
            runReconstruct(w, h);

            // 4) Blit 中间纹理 → 主帧缓冲
            blitToMain(w, h, mainFbo);

            // 交换历史 ping-pong：本帧写入的成为下一帧读取的
            histRead ^= 1;
            firstFrame = false;

            // 强制绑回主帧缓冲写
            mainRT.bindWrite(true);
        } catch (Throwable t) {
            LOGGER.error("[FSR2] 处理帧异常，禁用 FSR2", t);
            fallback = true;
            try {
                GL46C.glUseProgram(0);
                Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void runAccumulate(int w, int h, int colorTex) {
        GL46C.glUseProgram(accumulateProgram);

        // image 写入：新历史 = historyTex[1 - histRead]
        int writeTex = historyTex[1 - histRead];
        GL46C.glBindImageTexture(0, writeTex, 0, false, 0, GL46C.GL_WRITE_ONLY, GL46C.GL_RGBA16F);

        // samplers
        GL46C.glActiveTexture(GL46C.GL_TEXTURE0);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, colorTex);
        GL46C.glUniform1i(uAcc_currentColor, 0);

        GL46C.glActiveTexture(GL46C.GL_TEXTURE1);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, Fsr2MotionVectors.getVelocityTexture());
        GL46C.glUniform1i(uAcc_velocity, 1);

        GL46C.glActiveTexture(GL46C.GL_TEXTURE2);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, historyTex[histRead]);
        GL46C.glUniform1i(uAcc_historyPrev, 2);

        GL46C.glUniform2f(uAcc_renderSize, w, h);
        GL46C.glUniform2f(uAcc_displaySize, w, h);
        GL46C.glUniform1f(uAcc_historyWeight, (float) Config.getFsrHistoryWeight());
        GL46C.glUniform1i(uAcc_firstFrame, firstFrame ? 1 : 0);
        GL46C.glUniform1f(uAcc_velocityClampPix, VELOCITY_CLAMP_PIX);
        GL46C.glUniform1f(uAcc_maxVelocityLength, MAX_VELOCITY_LENGTH_UV);
        GL46C.glUniform1f(uAcc_clipBoxShrink, CLIP_BOX_SHRINK);

        GL46C.glDispatchCompute((w + WORKGROUP - 1) / WORKGROUP,
                (h + WORKGROUP - 1) / WORKGROUP, 1);
        GL46C.glMemoryBarrier(GL46C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                | GL46C.GL_TEXTURE_FETCH_BARRIER_BIT);

        // 解绑
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        GL46C.glActiveTexture(GL46C.GL_TEXTURE1);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        GL46C.glActiveTexture(GL46C.GL_TEXTURE0);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        GL46C.glBindImageTexture(0, 0, 0, false, 0, GL46C.GL_WRITE_ONLY, GL46C.GL_RGBA16F);
        GL46C.glUseProgram(0);
    }

    private static void runReconstruct(int w, int h) {
        GL46C.glUseProgram(reconstructProgram);

        GL46C.glBindImageTexture(0, intermediateTex, 0, false, 0, GL46C.GL_WRITE_ONLY, GL46C.GL_RGBA16F);

        GL46C.glActiveTexture(GL46C.GL_TEXTURE0);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, historyTex[1 - histRead]);  // 刚写入的累积结果
        GL46C.glUniform1i(uRec_history, 0);

        GL46C.glUniform2f(uRec_displaySize, w, h);
        GL46C.glUniform1f(uRec_sharpness, (float) Config.getFsrSharpness());

        GL46C.glDispatchCompute((w + WORKGROUP - 1) / WORKGROUP,
                (h + WORKGROUP - 1) / WORKGROUP, 1);
        GL46C.glMemoryBarrier(GL46C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                | GL46C.GL_FRAMEBUFFER_BARRIER_BIT);

        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        GL46C.glBindImageTexture(0, 0, 0, false, 0, GL46C.GL_WRITE_ONLY, GL46C.GL_RGBA16F);
        GL46C.glUseProgram(0);
    }

    private static void blitToMain(int w, int h, int mainFbo) {
        // 状态保存
        int[] vp = new int[4];
        GL46C.glGetIntegerv(GL46C.GL_VIEWPORT, vp);
        boolean blend = GL46C.glIsEnabled(GL46C.GL_BLEND);
        boolean depthTest = GL46C.glIsEnabled(GL46C.GL_DEPTH_TEST);
        boolean cull = GL46C.glIsEnabled(GL46C.GL_CULL_FACE);

        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        GlStateManager._disableCull();
        GlStateManager._colorMask(true, true, true, true);

        GL46C.glBindFramebuffer(GL46C.GL_READ_FRAMEBUFFER, intermediateFbo);
        GL46C.glBindFramebuffer(GL46C.GL_DRAW_FRAMEBUFFER, mainFbo);
        GL46C.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL46C.GL_COLOR_BUFFER_BIT, GL46C.GL_LINEAR);
        GL46C.glBindFramebuffer(GL46C.GL_FRAMEBUFFER, mainFbo);

        GL46C.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (blend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
        if (depthTest) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
        if (cull) GlStateManager._enableCull(); else GlStateManager._disableCull();
    }

    private static boolean checkComputeSupport() {
        if (computeChecked) {
            return computeSupported;
        }
        computeChecked = true;
        try {
            int major = GL46C.glGetInteger(GL46C.GL_MAJOR_VERSION);
            int minor = GL46C.glGetInteger(GL46C.GL_MINOR_VERSION);
            // 需要 4.6（实际计算需 4.3，但 GL46C 静态导入要求 4.6 上下文）
            computeSupported = major > 4 || (major == 4 && minor >= 6);
            if (!computeSupported) {
                LOGGER.warn("[FSR2] 当前 OpenGL {}.{} 不满足 4.6，FSR2 已禁用", major, minor);
            }
        } catch (Throwable t) {
            computeSupported = false;
            LOGGER.error("[FSR2] 检测 GL 版本失败", t);
        }
        return computeSupported;
    }

    private static boolean ensureInitialized(int w, int h) {
        if (initialized && texW == w && texH == h) {
            return true;
        }
        try {
            if (accumulateProgram == 0) {
                accumulateProgram = compileCompute("shaders/fsr2/fsr2_accumulate.comp", "accumulate");
                reconstructProgram = compileCompute("shaders/fsr2/fsr2_reconstruct.comp", "reconstruct");
                if (accumulateProgram == 0 || reconstructProgram == 0) {
                    fallback = true;
                    return false;
                }
                cacheUniformLocations();
            }
            // 重建历史/中间纹理（尺寸变化或首次）
            for (int i = 0; i < 2; i++) {
                if (historyTex[i] != 0) {
                    GL46C.glDeleteTextures(historyTex[i]);
                }
                historyTex[i] = createRgba16FTexture(w, h, GL46C.GL_LINEAR);
            }
            if (intermediateTex != 0) {
                GL46C.glDeleteTextures(intermediateTex);
            }
            intermediateTex = createRgba16FTexture(w, h, GL46C.GL_LINEAR);

            // Mipmap Bias 补偿：纯 TAA 累积会抹平高频细节导致画面偏软，
            // 通过对历史/中间纹理施加负向 LOD 偏置，采样时偏向更高频的 mip 层，
            // 提升纹理锐度。bias = log2(renderW / displayW) - 1.0。
            // （纯 TAA 下 renderW == displayW，log2 项为 0，等效 -1.0 的固定锐化偏置。）
            applyMipmapBias(historyTex[0]);
            applyMipmapBias(historyTex[1]);
            applyMipmapBias(intermediateTex);

            if (intermediateFbo == 0) {
                intermediateFbo = GL46C.glGenFramebuffers();
            }
            GL46C.glBindFramebuffer(GL46C.GL_FRAMEBUFFER, intermediateFbo);
            GL46C.glFramebufferTexture2D(GL46C.GL_FRAMEBUFFER, GL46C.GL_COLOR_ATTACHMENT0,
                    GL46C.GL_TEXTURE_2D, intermediateTex, 0);
            if (GL46C.glCheckFramebufferStatus(GL46C.GL_FRAMEBUFFER) != GL46C.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("FSR2 中间 FBO 不完整");
            }
            GL46C.glBindFramebuffer(GL46C.GL_FRAMEBUFFER, 0);

            texW = w;
            texH = h;
            initialized = true;
            firstFrame = true;  // 尺寸变化 → 历史失效
            LOGGER.info("[FSR2] 管线初始化完成 ({}x{})", w, h);
            return true;
        } catch (Exception e) {
            LOGGER.error("[FSR2] 初始化失败", e);
            fallback = true;
            return false;
        }
    }

    private static void cacheUniformLocations() {
        uAcc_currentColor = GL46C.glGetUniformLocation(accumulateProgram, "u_currentColor");
        uAcc_velocity = GL46C.glGetUniformLocation(accumulateProgram, "u_velocity");
        uAcc_historyPrev = GL46C.glGetUniformLocation(accumulateProgram, "u_historyPrev");
        uAcc_renderSize = GL46C.glGetUniformLocation(accumulateProgram, "u_renderSize");
        uAcc_displaySize = GL46C.glGetUniformLocation(accumulateProgram, "u_displaySize");
        uAcc_historyWeight = GL46C.glGetUniformLocation(accumulateProgram, "u_historyWeight");
        uAcc_firstFrame = GL46C.glGetUniformLocation(accumulateProgram, "u_firstFrame");
        uAcc_velocityClampPix = GL46C.glGetUniformLocation(accumulateProgram, "u_velocityClampPix");
        uAcc_maxVelocityLength = GL46C.glGetUniformLocation(accumulateProgram, "u_maxVelocityLength");
        uAcc_clipBoxShrink = GL46C.glGetUniformLocation(accumulateProgram, "u_clipBoxShrink");

        uRec_history = GL46C.glGetUniformLocation(reconstructProgram, "u_history");
        uRec_displaySize = GL46C.glGetUniformLocation(reconstructProgram, "u_displaySize");
        uRec_sharpness = GL46C.glGetUniformLocation(reconstructProgram, "u_sharpness");
    }

    private static int createRgba16FTexture(int w, int h, int filter) {
        int tex = GL46C.glGenTextures();
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, tex);
        GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RGBA16F, w, h, 0,
                GL46C.GL_RGBA, GL46C.GL_HALF_FLOAT, (java.nio.ByteBuffer) null);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, filter);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, filter);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_WRAP_S, GL46C.GL_CLAMP_TO_EDGE);
        GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_WRAP_T, GL46C.GL_CLAMP_TO_EDGE);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        return tex;
    }

    /**
     * 对纹理施加负向 Mipmap LOD 偏置，补偿 TAA 累积导致的画面变软。
     * <p>核心公式：{@code bias = log2(renderWidth / displayWidth) - 1.0f}。</p>
     * <p>纯 TAA 模式下 renderWidth == displayWidth，log2 项为 0，bias 恒为 -1.0；
     * 未来接入真实上采样（renderWidth &lt; displayWidth）时，把 texW/texH 替换为
     * 渲染/显示宽即可自动适配。</p>
     * <p>注意：GL_TEXTURE_LOD_BIAS 只对采样时自动选择的 mip 层生效，需纹理已生成
     * mipmap 链。本管线历史/中间纹理原本只采样第 0 层，这里把 MIN_FILTER 切换为
     * GL_LINEAR_MIPMAP_LINEAR 并生成 mipmap 链，让 LOD bias 真正参与采样；
     * 若环境不支持（极端驱动），静默忽略不致命。</p>
     */
    private static void applyMipmapBias(int tex) {
        if (tex == 0) {
            return;
        }
        try {
            // renderWidth == displayWidth（纯 TAA）→ log2 项为 0，bias = -1.0。
            float bias = -1.0F;

            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, tex);
            // 生成 mipmap 链（RGBA16F 纹理，从第 0 层往下）
            GL46C.glGenerateMipmap(GL46C.GL_TEXTURE_2D);
            GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER,
                    GL46C.GL_LINEAR_MIPMAP_LINEAR);
            GL46C.glTexParameterf(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_LOD_BIAS, bias);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        } catch (Throwable ignored) {
            // 生成 mipmap 或设置 bias 失败时不致命：最坏情况画面略软，管线继续运行。
        }
    }

    private static int compileCompute(String resourcePath, String label) throws IOException {
        String src = loadShaderString(resourcePath);
        int shader = GL46C.glCreateShader(GL46C.GL_COMPUTE_SHADER);
        GL46C.glShaderSource(shader, src);
        GL46C.glCompileShader(shader);
        if (GL46C.glGetShaderi(shader, GL46C.GL_COMPILE_STATUS) == GL46C.GL_FALSE) {
            LOGGER.error("[FSR2] {} 着色器编译失败: {}", label, GL46C.glGetShaderInfoLog(shader));
            GL46C.glDeleteShader(shader);
            return 0;
        }
        int prog = GL46C.glCreateProgram();
        GL46C.glAttachShader(prog, shader);
        GL46C.glLinkProgram(prog);
        GL46C.glDeleteShader(shader);
        if (GL46C.glGetProgrami(prog, GL46C.GL_LINK_STATUS) == GL46C.GL_FALSE) {
            LOGGER.error("[FSR2] {} 程序链接失败: {}", label, GL46C.glGetProgramInfoLog(prog));
            GL46C.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    private static String loadShaderString(String resourcePath) throws IOException {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(Ribbon.MODID, resourcePath);
        try (InputStream is = Minecraft.getInstance().getResourceManager().getResource(rl)
                .orElseThrow(() -> new IOException("找不到着色器资源: " + rl)).open()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
