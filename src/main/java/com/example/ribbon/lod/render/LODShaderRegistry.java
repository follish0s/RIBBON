/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.render;

import com.example.ribbon.Ribbon;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 注册 Ribbon LOD 专用的独立 {@link ShaderInstance}。
 *
 * <h2>为什么不用原版 shader</h2>
 * <p>Iris 的 deferred 管线会替换它<b>认识</b>的内置 shader（{@code rendertype_solid} 等），
 * 但不会碰 mod 自己通过 {@link RegisterShadersEvent} 注册的独立 shader。因此 LOD 用它
 * 自己的 {@code lod_terrain} shader 渲染，就能完全绕开 Iris 对原版 RenderType/shader 的
 * 拦截，避免「顶点被 G-Buffer 重定向 / 格式被转换 / endBatch 被清空」这一整条坑链。</p>
 *
 * <h2>生命周期</h2>
 * <p>shader 实例在 {@link RegisterShadersEvent} 时创建，资源重载（切换光影、改资源包）
 * 时通过 registerShader 的回调收到新实例，这里只保留最新引用。渲染线程每帧只读
 * {@link #getShader()} 返回的 volatile 引用，不做任何跨线程写。</p>
 */
@EventBusSubscriber(modid = Ribbon.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LODShaderRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/LOD-Shader");

    /**
     * LOD 地形 shader 的资源名。
     *
     * <p><b>关键</b>：Minecraft 1.21.1 运行版 {@link ShaderInstance} 的构造器
     * {@code ShaderInstance(ResourceProvider, String, VertexFormat)} 内部用
     * {@code ResourceLocation.withDefaultNamespace(...)} 把 json/vsh/fsh 一律解析到
     * {@code minecraft} namespace（忽略传入的 namespace）。因此 shader 资源必须放在
     * {@code assets/minecraft/shaders/core/lod_terrain.*}，且这里必须用 {@code minecraft}
     * namespace 才能命中。曾用 {@code ribbon} namespace 导致注册时报
     * {@code FileNotFound: minecraft:shaders/core/lod_terrain.vsh}。</p>
     */
    public static final ResourceLocation LOD_TERRAIN = ResourceLocation.fromNamespaceAndPath("minecraft", "lod_terrain");

    /** 探针 shader（全屏红三角诊断用，绕开矩阵/雾/颜色一切变量）。 */
    public static final ResourceLocation LOD_PROBE = ResourceLocation.fromNamespaceAndPath("minecraft", "lod_probe");

    /** 当前 shader 实例（volatile，资源重载时被回调替换）。 */
    private static volatile ShaderInstance lodTerrainShader;

    /** 探针 shader 实例（volatile）。 */
    private static volatile ShaderInstance lodProbeShader;

    private LODShaderRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            // 【关键重构】格式升级为 DefaultVertexFormat.BLOCK —— 与 VBO 上传、以及
            // Iris 的 GBuffer/Shadow Pass 完全对齐。此前用 POSITION_COLOR 丢掉了
            // UV0/UV2/Normal，导致光影下 LOD 顶点属性绑定错位、地形消失。
            ShaderInstance shader = new ShaderInstance(
                    event.getResourceProvider(),
                    LOD_TERRAIN.getPath(),
                    DefaultVertexFormat.BLOCK);
            event.registerShader(shader, newShader -> {
                lodTerrainShader = newShader;
                com.example.ribbon.lod.render.LODRenderEventHandler.invalidateSpriteUv();
                LOGGER.info("[Ribbon/LOD] LOD terrain shader 已重载");
            });
            lodTerrainShader = shader;
            LOGGER.info("[Ribbon/LOD] LOD terrain shader 注册成功 (BLOCK)");
        } catch (IOException e) {
            LOGGER.error("[Ribbon/LOD] LOD terrain shader 注册失败", e);
            lodTerrainShader = null;
        }

        // 探针 shader：POSITION 格式（12 字节/顶点），片元写死纯红。
        try {
            ShaderInstance probe = new ShaderInstance(
                    event.getResourceProvider(),
                    LOD_PROBE.getPath(),
                    DefaultVertexFormat.POSITION);
            event.registerShader(probe, newShader -> {
                lodProbeShader = newShader;
                LOGGER.info("[Ribbon/LOD] LOD probe shader 已重载");
            });
            lodProbeShader = probe;
            LOGGER.info("[Ribbon/LOD] LOD probe shader 注册成功 (POSITION)");
        } catch (IOException e) {
            LOGGER.error("[Ribbon/LOD] LOD probe shader 注册失败", e);
            lodProbeShader = null;
        }
    }

    /**
     * 获取 LOD 地形 shader（渲染线程每帧调用）。
     * 未就绪或注册失败时返回 null，调用方应跳过渲染。
     */
    public static ShaderInstance getShader() {
        return lodTerrainShader;
    }

    /** 获取探针 shader（渲染线程每帧调用）。 */
    public static ShaderInstance getProbeShader() {
        return lodProbeShader;
    }
}