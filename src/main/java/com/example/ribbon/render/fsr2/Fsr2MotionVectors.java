/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.render.fsr2;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL46C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

/**
 * 屏幕空间运动矢量生成器（FSR 2.2 的"输入"之一）。
 *
 * <h2>算法</h2>
 * 用当前帧的<b>抖动后完整 VP</b>（投影×视图，含 bobHurt）的逆矩阵，
 * 从深度缓冲反求出当前像素对应的"相机相对世界坐标" camRel；
 * 再加上本帧相机相对上一帧相机的位移 {@code camDelta}，得到该世界点
 * 在上一帧相机坐标系下的位置 prevCamRel；用上一帧的完整 VP 投影即得
 * prevUV。速度 = currUV - prevUV（归一化 UV 单位）。
 *
 * <p>采用"相机相对坐标"是因为 Minecraft 的视图矩阵只含旋转、不含平移——
 * 相机平移是在 LevelRenderer 里通过 PoseStack 平移几何实现的。因此必须
 * 显式补偿 {@code camDelta = currCamPos - prevCamPos}，否则玩家一移动
 * 运动矢量就全错，时域累积会糊成一片。</p>
 *
 * <h2>无抖动模式（TAA）</h2>
 * 相机亚像素抖动已彻底移除（见 MixinGameRendererJitter 回滚说明）。当前帧 VP
 * 与上一帧 VP 均<b>不含抖动</b>，速度矢量纯由相机运动（旋转 + 平移）驱动。
 * 无抖动时仍可做时域累积（TAA）：相机旋转/平移产生的亚像素运动足够让多帧
 * 采样覆盖不同屏幕位置，累积后显著减少锯齿与闪烁，画质仍优于 FSR 1.0。
 *
 * <p>本类把运动矢量计算着色器以 GLSL 字符串内嵌，避免额外资源文件
 * （accumulate/reconstruct 两个 .comp 已按需求落在 resources 下）。</p>
 */
public final class Fsr2MotionVectors {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/FSR2");

    private static final int WORKGROUP = 8;

    // 复用的矩阵/向量，避免每帧分配
    private static final Matrix4f currVP = new Matrix4f();
    private static final Matrix4f prevVP = new Matrix4f();
    private static final Matrix4f invCurrVP = new Matrix4f();
    private static final Vector3f currCamPos = new Vector3f();
    private static final Vector3f prevCamPos = new Vector3f();
    private static final Vector3f camDelta = new Vector3f();
    private static final FloatBuffer MAT_BUF = BufferUtils.createFloatBuffer(16);

    private static int program;
    private static int velTex;
    private static int velW, velH;

    private static int uLocInvCurrVP, uLocPrevVP, uLocCamDelta, uLocInvRenderSize, uLocDepth;

    private static boolean matricesReady = false;
    private static boolean firstFrame = true;
    private static boolean initialized = false;
    private static boolean compileFailed = false;

    private Fsr2MotionVectors() {
    }

    /** 由 MixinLevelRendererFsr2 在 renderLevel HEAD 调用：捕获本帧 VP 与相机位置。 */
    public static void beginFrame(Camera camera, Matrix4f projection, Matrix4f view) {
        if (projection == null || view == null) {
            return;
        }
        // prev ← curr（首次调用时 prev 自动等于 curr，速度为 0，安全）
        if (firstFrame) {
            prevVP.set(projection).mul(view);
            prevCamPos.set((float) camera.getPosition().x(),
                    (float) camera.getPosition().y(),
                    (float) camera.getPosition().z());
            firstFrame = false;
        } else {
            prevVP.set(currVP);
            prevCamPos.set(currCamPos);
        }
        currVP.set(projection).mul(view);
        invCurrVP.set(currVP).invert();

        Vec3 p = camera.getPosition();
        currCamPos.set((float) p.x(), (float) p.y(), (float) p.z());
        camDelta.set(currCamPos).sub(prevCamPos);
        matricesReady = true;
    }

    /** FSR2 被关闭时调用，使下一帧重新当作"首帧"（速度=0，历史失效）。 */
    public static void invalidate() {
        matricesReady = false;
        firstFrame = true;
    }

    /** 跑运动矢量 Pass。返回 false 表示编译失败，调用方应走 fallback。 */
    public static boolean dispatch(int renderW, int renderH, int depthTextureId) {
        if (compileFailed) {
            return false;
        }
        if (!matricesReady) {
            return false;
        }
        if (!ensureInitialized(renderW, renderH)) {
            return false;
        }

        // renderLevel TAIL 必在渲染线程

        GL46C.glBindImageTexture(0, velTex, 0, false, 0, GL46C.GL_WRITE_ONLY, GL46C.GL_RG16F);
        GL46C.glUseProgram(program);

        MAT_BUF.clear();
        invCurrVP.get(MAT_BUF);
        GL46C.glUniformMatrix4fv(uLocInvCurrVP, false, MAT_BUF);
        MAT_BUF.clear();
        prevVP.get(MAT_BUF);
        GL46C.glUniformMatrix4fv(uLocPrevVP, false, MAT_BUF);
        GL46C.glUniform3f(uLocCamDelta, camDelta.x, camDelta.y, camDelta.z);
        GL46C.glUniform2f(uLocInvRenderSize, 1.0F / renderW, 1.0F / renderH);

        GL46C.glActiveTexture(GL46C.GL_TEXTURE0);
        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, depthTextureId);
        GL46C.glUniform1i(uLocDepth, 0);

        GL46C.glDispatchCompute((renderW + WORKGROUP - 1) / WORKGROUP,
                (renderH + WORKGROUP - 1) / WORKGROUP, 1);
        // 累积 Pass 要以 sampler 方式读 velocity，需要 image→texture 可见性
        GL46C.glMemoryBarrier(GL46C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                | GL46C.GL_TEXTURE_FETCH_BARRIER_BIT);

        GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
        GL46C.glUseProgram(0);
        GL46C.glBindImageTexture(0, 0, 0, false, 0, GL46C.GL_WRITE_ONLY, GL46C.GL_RG16F);
        return true;
    }

    /** 累积 Pass 用它把速度纹理绑到 sampler。 */
    public static int getVelocityTexture() {
        return velTex;
    }

    private static boolean ensureInitialized(int w, int h) {
        if (initialized && velW == w && velH == h && program != 0) {
            return true;
        }
        try {
            if (program == 0 && !compileFailed) {
                program = compileMotionProgram();
                if (program == 0) {
                    compileFailed = true;
                    return false;
                }
                uLocInvCurrVP = GL46C.glGetUniformLocation(program, "u_invCurrVP");
                uLocPrevVP = GL46C.glGetUniformLocation(program, "u_prevVP");
                uLocCamDelta = GL46C.glGetUniformLocation(program, "u_camDelta");
                uLocInvRenderSize = GL46C.glGetUniformLocation(program, "u_invRenderSize");
                uLocDepth = GL46C.glGetUniformLocation(program, "u_depth");
            }
            if (velTex != 0) {
                GL46C.glDeleteTextures(velTex);
            }
            velTex = GL46C.glGenTextures();
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, velTex);
            GL46C.glTexImage2D(GL46C.GL_TEXTURE_2D, 0, GL46C.GL_RG16F, w, h, 0,
                    GL46C.GL_RG, GL46C.GL_HALF_FLOAT, (java.nio.ByteBuffer) null);
            GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MIN_FILTER, GL46C.GL_NEAREST);
            GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_MAG_FILTER, GL46C.GL_NEAREST);
            GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_WRAP_S, GL46C.GL_CLAMP_TO_EDGE);
            GL46C.glTexParameteri(GL46C.GL_TEXTURE_2D, GL46C.GL_TEXTURE_WRAP_T, GL46C.GL_CLAMP_TO_EDGE);
            GL46C.glBindTexture(GL46C.GL_TEXTURE_2D, 0);
            velW = w;
            velH = h;
            initialized = true;
            return true;
        } catch (Exception e) {
            LOGGER.error("[FSR2] 运动矢量管线初始化失败", e);
            compileFailed = true;
            return false;
        }
    }

    private static int compileMotionProgram() {
        int shader = GL46C.glCreateShader(GL46C.GL_COMPUTE_SHADER);
        GL46C.glShaderSource(shader, MOTION_GLSL);
        GL46C.glCompileShader(shader);
        if (GL46C.glGetShaderi(shader, GL46C.GL_COMPILE_STATUS) == GL46C.GL_FALSE) {
            LOGGER.error("[FSR2] 运动矢量着色器编译失败: {}", GL46C.glGetShaderInfoLog(shader));
            GL46C.glDeleteShader(shader);
            return 0;
        }
        int prog = GL46C.glCreateProgram();
        GL46C.glAttachShader(prog, shader);
        GL46C.glLinkProgram(prog);
        GL46C.glDeleteShader(shader);
        if (GL46C.glGetProgrami(prog, GL46C.GL_LINK_STATUS) == GL46C.GL_FALSE) {
            LOGGER.error("[FSR2] 运动矢量程序链接失败: {}", GL46C.glGetProgramInfoLog(prog));
            GL46C.glDeleteProgram(prog);
            return 0;
        }
        return prog;
    }

    private static final String MOTION_GLSL = """
            #version 460 core
            layout(local_size_x = 8, local_size_y = 8) in;
            layout(binding = 0, rg16f) writeonly uniform image2D u_velocityOut;
            uniform sampler2D u_depth;        // unit 0
            uniform mat4 u_invCurrVP;
            uniform mat4 u_prevVP;
            uniform vec3 u_camDelta;          // currCamPos - prevCamPos
            uniform vec2 u_invRenderSize;
            void main() {
                ivec2 pix = ivec2(gl_GlobalInvocationID.xy);
                vec2 uv = (vec2(pix) + 0.5) * u_invRenderSize;
                float depth = texture(u_depth, uv).x;
                // 反投影当前像素到相机相对世界坐标
                vec4 ndc = vec4(2.0 * uv - 1.0, 2.0 * depth - 1.0, 1.0);
                vec4 h = u_invCurrVP * ndc;
                vec3 camRel = h.xyz / max(h.w, 1.0e-6);
                // 补偿相机平移，得到上一帧相机坐标系下的同一世界点
                vec3 prevCamRel = camRel + u_camDelta;
                vec4 prevClip = u_prevVP * vec4(prevCamRel, 1.0);
                vec2 prevUV = (prevClip.xy / max(prevClip.w, 1.0e-6)) * 0.5 + 0.5;
                vec2 velocity = uv - prevUV;
                imageStore(u_velocityOut, pix, vec4(velocity, 0.0, 0.0));
            }
            """;
}
