/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.render;

import com.example.ribbon.lod.LODManager;
import com.example.ribbon.lod.mesh.LODMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = com.example.ribbon.Ribbon.MODID, value = Dist.CLIENT)
public final class LODRenderEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/LOD-Render");

    /** 每帧提交的 mesh 上限。远景 LOD 顶点量小（每区块仅暴露面），提交数百个
     *  mesh 的开销远小于遗漏区块导致的"断断续续/大片空洞"。旧值 256 在视野
     *  开阔时（可见 300~400 区块）每帧漏画上百个，表现为远景地形不连贯。 */
    private static final int MAX_MESHES_PER_FRAME = 1024;
    private static final int DIAGNOSTIC_INTERVAL = 100;

    private static final Vector4f CULL_VEC = new Vector4f();

    private static volatile int frameCounter = 0;
    private static volatile boolean diagShaderPrinted = false;
    private static volatile boolean diagFirstFramePrinted = false;
    private static volatile boolean diagProgramPrinted = false;

    private LODRenderEventHandler() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 实验性总闸（默认关闭）：放在最前面，关闭时零开销、连 Iris 反射都不做
        if (!com.example.ribbon.optimization.RuntimeConfig.lodEnabled) return;

        // 【光影兼容·双阶段提交】这是解决"光影下 LOD 全黑 + 拖影"的关键。
        //
        // 无光影：在 AFTER_TRANSLUCENT_BLOCKS 提交（深度已稳定，覆盖半透明水面之上）。
        //
        // 光影（Iris deferred）：经过字节码级排查确认，AFTER_SOLID_BLOCKS 时 Iris 已把
        // WorldRenderingPhase 置为 NONE（iris$endTerrainLayer → setPhase(NONE)），即 GBuffer
        // pass 已经结束、deferred 光照数据已封存。此时用任何 shader 画 LOD 都不在 GBuffer
        // 阶段内，deferred lighting 读不到它的法线/材质 → 渲染成"无光照黑块"，且深度
        // 缓冲被 composite 重建 → 产生拖影（ghosting）。
        //
        // NeoForge 的 RenderLevelStageEvent 没有能落在 Iris GBuffer 阶段之内的 hook，因此
        // 无法让自定义 LOD 借道 deferred 光照（那是 Distant Horizons 通过 mixin Iris 内部
        // 的 compat/dh 兼容层才能做到的，风险极高）。
        //
        // 务实降级：光影下改到 AFTER_LEVEL（Iris 所有 deferred pass 结束后、最终输出
        // framebuffer 上）提交 LOD，用纯色 + 雾效直接绘制。代价是 LOD 不受光影的动态
        // 光照/阴影影响（视觉上偏平），但能正确显示地形颜色、无全黑、无拖影，远优于
        // 现状。这是低风险、确定可行的方案。
        boolean irisOn = com.example.ribbon.optimization.ShaderStateCache.isIrisPresent()
                && com.example.ribbon.optimization.ShaderStateCache.areShadersEnabled();
        RenderLevelStageEvent.Stage target = irisOn
                ? RenderLevelStageEvent.Stage.AFTER_LEVEL
                : RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        if (event.getStage() != target) return;

        LODManager manager = LODManager.getInstance();
        if (!manager.isRunning()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        net.minecraft.world.phys.Vec3 camPos = event.getCamera().getPosition();

        // 【关键·先淘汰后收割】必须先 evictFarAway 腾位，再 drainReady 放入新网格。
        // 否则 readyMeshes 满时，drainReady 会 recycle 丢弃新构建的网格（白构建），
        // 且新区域永远进不了缓存（"转化罢工"）。先淘汰跑出范围的旧网格，为
        // drainReady 腾出空间，保证玩家移动后环带能持续更新。
        int camChunkX = (int) Math.floor(camPos.x / 16.0);
        int camChunkZ = (int) Math.floor(camPos.z / 16.0);
        int radius = com.example.ribbon.Config.getLodRadiusChunks();
        // 【关键修复·用客户端选项而非服务端有效距离】
        // 此前用 mc.options.getEffectiveRenderDistance() 作为内圈，它 = min(客户端选项,
        // 服务端 view distance)。世界加载/性能模组会把服务端 view distance 短暂降到 2
        // （日志「Changing view distance to 2」），导致 innerRadius 塌缩到 2，LOD 从
        // 玩家脚边 2 区块就开始接管，叠压在原版渲染的区块之上 →「糊脚底」。
        // 客户端选项 renderDistance() 才是玩家在视频设置里选的稳定值，是原版区块
        // 实际被渲染覆盖的范围，用它做内圈才能与原版渲染边界严格对齐。
        int clientRenderDist = mc.options.renderDistance().get();
        int innerRadius = Math.max(clientRenderDist, 2);
        // 【回退·淘汰对齐覆盖】淘汰半径 = 请求覆盖外半径 + 少量缓冲，两者必须同源。
        // 请求覆盖 = innerRadius + radius，故淘汰 = innerRadius + radius + 2，
        // 既保留刚生成区块，又能淘汰真正跑出范围的旧区块，避免"生成→淘汰→再生成"抖动。
        int evictRadius = innerRadius + (radius > 0 ? radius : 8) + 2;
        manager.evictFarAway(camChunkX, camChunkZ, evictRadius);
        // 【关键修复·回收内圈遗留网格】淘汰玩家内半径（原版接管区）内的遗留 LOD，
        // 释放缓存并让原版区块独占内圈（渲染端另有内圈过滤，双保险）。
        manager.evictInner(camChunkX, camChunkZ, innerRadius);

        manager.drainReady();

        if ((++frameCounter % DIAGNOSTIC_INTERVAL) == 0) {
            double buildAvg = manager.drainBuildAvgMs();
            LOGGER.info("[Ribbon/LOD][心跳] running={}, ready={}, inFlight={}, iris={}, buildAvgMs={}, pool={}",
                    manager.isRunning(), manager.readyCount(), manager.inFlightCount(),
                    com.example.ribbon.optimization.ShaderStateCache.isIrisPresent()
                            && com.example.ribbon.optimization.ShaderStateCache.areShadersEnabled(),
                    buildAvg < 0 ? "n/a" : String.format("%.2f", buildAvg),
                    com.example.ribbon.lod.mesh.LODMeshPool.getInstance().pooledCount());
        }

        List<LODMesh> ready = manager.snapshotReady();
        if (ready.isEmpty()) return;

        Matrix4f modelView = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        if (modelView == null || projection == null) return;

        // fullView = 世界坐标 → 相机空间 的 ModelView。
        // event.getModelViewMatrix() 是纯旋转（不含相机平移），顶点需先减相机位置。
        float camX = (float) camPos.x;
        float camY = (float) camPos.y;
        float camZ = (float) camPos.z;
        Matrix4f fullView = new Matrix4f(modelView).translate(-camX, -camY, -camZ);

        List<LODMesh> visible = cullToFrustum(ready, fullView, projection);

        // 【关键修复·内圈过滤对齐原版圆形判定】LOD 只应接管「原版渲染距离之外」的
        // 远景。原版 Embeddium 的渲染距离判定是**圆形（圆柱形）**：
        //   RenderSectionDistanceFilter.DEFAULT = (dx²+dz² < maxDistance²) && |dy| < maxDistance
        // 即水平用欧氏距离 dx²+dz²，不是方形 Chebyshev。若 LOD 内圈过滤用方形，会在
        // 四个角落（max(|dx|,|dz|) 已过内圈、但 sqrt(dx²+dz²) 仍在原版圆外）产生
        // 「LOD 跳过 + 原版不渲染」的真空夹缝。改用与原版一致的欧氏距离圆形判定，
        // LOD 与原版渲染边界严格对齐，既不重叠也不留缝。
        int innerSq = innerRadius * innerRadius;
        visible.removeIf(m -> {
            int dx = m.chunkX() - camChunkX;
            int dz = m.chunkZ() - camChunkZ;
            return (dx * dx + dz * dz) <= innerSq;
        });

        // 【精度提升·按距离排序】就绪 mesh 在 ConcurrentHashMap 里是无序的，
        // 若直接按无序顺序提交，玩家正前方/近处的区块可能被挤到提交上限之外，
        // 造成"断断续续"的空洞。这里按区块中心到相机的水平距离升序排序，
        // 近处优先提交，保证玩家视野核心区域的地形连续完整。
        visible.sort((a, b) -> {
            double da = distSq(a, camPos);
            double db = distSq(b, camPos);
            return Double.compare(da, db);
        });

        if (frameCounter % DIAGNOSTIC_INTERVAL == 0) {
            LOGGER.info("[Ribbon/LOD][视锥] 帧#{} 候选={} 可见={} 相机区块=({},{}) evictR={}",
                    frameCounter, ready.size(), visible.size(), camChunkX, camChunkZ, evictRadius);
        }
        if (visible.isEmpty()) return;

        if (!diagFirstFramePrinted) {
            diagFirstFramePrinted = true;
            LODMesh first = visible.get(0);
            LOGGER.info("[Ribbon/LOD][首帧] 相机=({},{},{}) 可见mesh={} 首个=({},{}) 原点=({},{}) verts={}",
                    camPos.x, camPos.y, camPos.z, visible.size(),
                    first.chunkX(), first.chunkZ(),
                    first.chunkX() * 16, first.chunkZ() * 16, first.vertexCount());
        }

        try {
            com.example.ribbon.IrisReflection.allowUnknownShaders();

            boolean irisRendering = com.example.ribbon.optimization.ShaderStateCache.isIrisPresent()
                    && com.example.ribbon.optimization.ShaderStateCache.areShadersEnabled();

            // —— 渲染状态设置 ——
            // 无光影：走 RenderType.solid().setupRenderState()，正确设置深度测试(LEQUAL)、
            // 颜色/深度写入与 atlas 绑定，与原版区块渲染一致。
            // 光影（AFTER_LEVEL，deferred 已结束）：显式绑定主 framebuffer，清空残留深度，
            // 用 LEQUAL 深度测试 + 深度写入 + 不透明混合，让 LOD 正常显示且内部正确遮挡。
            if (!irisRendering) {
                RenderType.solid().setupRenderState();
            }

            // 【修复·强制关背面剔除】LOD 是远景低模，GreedyMesher 的绕序虽然按
            // 叉积验证为 CCW，但任何一面的偏差都会被 CULL 剔除导致"面片消失/地形穿插"。
            // 远景 LOD 顶点量小，背面剔除的收益可忽略，而误剔除的代价是可见面丢失，
            // 因此提交期间强制关闭背面剔除，draw 完再恢复。
            RenderSystem.disableCull();

            if (irisRendering) {
                // —— 光影(AFTER_LEVEL)专用状态 ——
                // deferred 管线已全部结束，LOD 作为最终画面的几何叠加绘制。
                //
                // 【关键修复·深度缓冲重置】之前「不清空深度」依赖主 framebuffer 深度缓冲
                // 里保存着近景深度。但 Iris deferred 管线的近景深度写在它自己的 GBuffer
                // framebuffer 里，主 framebuffer（getMainRenderTarget）的深度缓冲在 final
                // composite 阶段通常只写颜色、不写深度 → 主 framebuffer 深度是空的/过期的。
                // 此时 LEQUAL 深度测试对近景形同虚设（LOD 仍叠压近景），且 LOD 内部面片
                // 之间也因深度全空而互相穿插、碎片化（截图里「斑块状、支离破碎」的根源）。
                //
                // 正确做法：显式清空主 framebuffer 深度，让 LOD 自己建立深度，配合
                // 「近→远排序 + LEQUAL + 深度写入」，保证 LOD 内部山体正确遮挡、不碎片化。
                // 近景遮挡交给内圈过滤（LOD 只画在原版渲染距离之外），不依赖深度缓冲。
                // 1) 绑定主渲染目标；
                // 2) 清空深度（清掉 composite 残留的过期/空深度）；
                // 3) LEQUAL 深度测试 + 深度写入，LOD 内部正确遮挡；
                // 4) 关闭混合（不透明），杜绝 composite 残留混合状态导致过曝。
                mc.getMainRenderTarget().bindWrite(false);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.disableBlend();
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            } else {
                // 【修复·强制深度写入 + 深度测试】不依赖 setupRenderState 的隐含状态，
                // 显式确保深度测试开启且深度可写，解决"纸片穿插"。
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            }

            // 注意：VertexBuffer.drawWithShader(modelView, projection, shader) 用
            // 显式矩阵参数设置 ModelViewMat/ProjMat uniform，不依赖 RenderSystem 的
            // 全局矩阵栈，故这里无需操作 ModelViewStack（1.21.1 已无 PoseStack 语义）。

            // 【光影兼容·shader 选择】统一用自定义 lod_terrain shader（纯色 + 雾效）。
            // 光影下 AFTER_LEVEL 阶段（deferred 已结束）不在 GBuffer 内，自定义 shader
            // 直接画在最终输出 framebuffer 上，能正确显示地形颜色（不再全黑），且
            // 深度已稳定（不再拖影）。不采样 terrain atlas，避免错误纹理。
            ShaderInstance shader = LODShaderRegistry.getShader();
            if (shader != null) {
                RenderSystem.setShader(() -> shader);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                // 【关键·uniform 时机】drawWithShader 内部会 shader.apply() 把 QualityMode
                // 等自定义 uniform 重置为 json 默认值，覆盖掉 applyQualityUniforms 的设置。
                // 因此这里不能提前 set，必须等 apply 之后再设（见下方 draw 循环）。
                // 这里仅预解析 HIGH 档的 atlas/sprite 引用，供 draw 前快速绑定。
                // 【光影兼容】仅无光影时调用 clearMainBound：无光影走
                // AFTER_TRANSLUCENT_BLOCKS，此时 Iris 若存在则 isRenderingWorld 仍可能为
                // true，需 clearMainBound 让 onTail 的 shouldOverrideShaders() 返回 false，
                // 避免 Iris 把 framebuffer 切到默认（覆盖 LOD）。
                // 光影下 AFTER_LEVEL 时 Iris 的 isRenderingWorld 已为 false（finalizeLevel
                // Rendering 已置 false），shouldOverrideShaders() 本就 false，无需也不应改
                // isMainBound（改它可能干扰 Iris 收尾状态，是"看不见"的可疑诱因之一）。
                if (!irisRendering) {
                    com.example.ribbon.IrisReflection.clearMainBound();
                }
            } else {
                // shader 未注册：通常是「刚打开实验性总闸但未 F3+T 重载资源」的窗口期。
                // 这是预期状态，不是错误，直接跳过绘制（避免无效 VBO 上传与误导性 ERROR）。
                if (!diagShaderPrinted) {
                    diagShaderPrinted = true;
                    LOGGER.info("[Ribbon/LOD] shader 尚未注册，跳过绘制；按 F3+T 重载资源或重启后生效");
                }
                return;
            }

            // 【修复·远切】按区块中心到相机的距离过滤。超出视距的区块直接跳过提交，
            // 不交给 GPU 做 Far Plane 硬件裁剪——那会导致地平线处"硬切/穿插"。
            int viewDistance = com.example.ribbon.Config.getLodViewDistanceBlocks();
            double viewDistanceSq = (double) viewDistance * viewDistance;

            int submitted = 0;
            int uploadFailed = 0;
            int farClipped = 0;
            for (LODMesh mesh : visible) {
                if (submitted >= MAX_MESHES_PER_FRAME) break;

                // 【修复·NPE 兜底】空区块（vertexCount==0）或已失效的 mesh 直接跳过，
                // 不进入 VBO 上传与 draw 阶段，杜绝 drawWithShader 对空几何的 NPE。
                if (mesh == null || mesh.vertexCount() == 0) {
                    continue;
                }

                // 远切：区块中心到相机的水平距离平方（忽略 Y，仅地面距离）。
                double dx = mesh.centerX() - camPos.x;
                double dz = mesh.centerZ() - camPos.z;
                double distSq = dx * dx + dz * dz;
                if (distSq > viewDistanceSq) {
                    farClipped++;
                    continue;
                }

                VertexBuffer vb = LODVboUploader.ensureUploaded(mesh);
                if (vb == null) { uploadFailed++; continue; }

                // 把 shader 读取到局部，避免 bind 与 draw 之间被资源重载置 null 导致 NPE。
                ShaderInstance localShader = shader;
                vb.bind();
                if (localShader != null) {
                    // 顶点 Alpha 恒 0xFF（完全固体，见 LODMesh.putVertex/emitBlockVertex），
                    // 走不透明通道，无需 VertexSorting.DISTANCE_TO_ORIGIN 半透明排序。
                    //
                    // 【关键·自定义 uniform 时机】drawWithShader 内部会先
                    // setDefaultUniforms + apply()，把 QualityMode/Sampler0 等自定义
                    // uniform 重置为 json 默认值，再 draw + clear。若用 drawWithShader，
                    // applyQualityUniforms 提前设的 uniform 会被 apply() 覆盖 → HIGH 档
                    // atlas 采样失效、颜色错乱。因此这里手动复刻：
                    //   setDefaultUniforms（设 ModelViewMat/ProjMat + 默认 12 sampler）
                    //   → applyQualityUniforms（覆盖 Sampler0=atlas、QualityMode、sprite UV）
                    //   → apply()（一次性上传全部 uniform + 绑定 sampler）
                    //   → draw → clear
                    // 自定义 uniform 放在 apply() 之前，随 apply 一并上传，确保生效。
                    localShader.setDefaultUniforms(
                            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                            fullView, projection, mc.getWindow());
                    applyQualityUniforms(localShader, mc, irisRendering);
                    localShader.apply();
                    // 【诊断·正确时机】apply() 之后、draw() 之前，GL_CURRENT_PROGRAM
                    // 才是 shader 是否真正绑定的铁证（非 0 = 生效，0 = 被 Iris 拦截）。
                    if (submitted == 0 && !diagProgramPrinted) {
                        diagProgramPrinted = true;
                        int curProg = GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM);
                        LOGGER.info("[Ribbon/LOD][prog] apply后 shaderId={} GL_CURRENT_PROGRAM={} irisRendering={}",
                                localShader.getId(), curProg, irisRendering);
                    }
                    vb.draw();
                    localShader.clear();
                } else {
                    vb.draw();
                }
                submitted++;
            }
            VertexBuffer.unbind();

            if (frameCounter % DIAGNOSTIC_INTERVAL == 0) {
                // 提交统计：glErr=0 表示 draw 全程无 GL 错误。不再读 GL_CURRENT_PROGRAM
                // （draw 循环结束后 shader 已 clear，恒 0 是预期，无诊断价值，见 [prog] 行）。
                LOGGER.info("[Ribbon/LOD][提交] 帧#{} 可见={} draw={} fail={} 远切={} viewDist={} glErr={}",
                        frameCounter, visible.size(), submitted, uploadFailed, farClipped, viewDistance,
                        GL11.glGetError());
            }
        } finally {
            boolean irisRendering = com.example.ribbon.optimization.ShaderStateCache.isIrisPresent()
                    && com.example.ribbon.optimization.ShaderStateCache.areShadersEnabled();
            // 仅无光影路径调用了 clearMainBound，故仅无光影时恢复（光影下没改过，
            // 无需也不应动 Iris 的 isMainBound 状态）。
            if (!irisRendering) {
                com.example.ribbon.IrisReflection.restoreMainBound();
            }
            RenderSystem.enableCull();
            // 光影路径：恢复深度测试、深度写入与混合状态（AFTER_LEVEL 后可能还有
            // GUI/手部等渲染，不能留下被我们改过的状态污染它们）。
            if (irisRendering) {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
            // 与 setupRenderState 配对：仅无光影路径调用了 solid 状态，故仅无光影时清理。
            if (!irisRendering) {
                RenderType.solid().clearRenderState();
            }
        }
    }

    private static List<LODMesh> cullToFrustum(List<LODMesh> meshes, Matrix4f fullView, Matrix4f projection) {
        List<LODMesh> out = new ArrayList<>(meshes.size());
        for (LODMesh m : meshes) {
            if (meshVisibleInView(m, fullView, projection)) out.add(m);
        }
        return out;
    }

    private static boolean meshVisibleInView(LODMesh mesh, Matrix4f fullView, Matrix4f projection) {
        int minX = mesh.chunkX() * 16, maxX = minX + 16;
        int minZ = mesh.chunkZ() * 16, maxZ = minZ + 16;
        float yLo = -64f, yHi = 320f;
        return cornerInFrustum(minX, yLo, minZ, fullView, projection)

                || cornerInFrustum(maxX, yLo, minZ, fullView, projection)
                || cornerInFrustum(minX, yHi, minZ, fullView, projection)
                || cornerInFrustum(maxX, yHi, minZ, fullView, projection)
                || cornerInFrustum(minX, yLo, maxZ, fullView, projection)
                || cornerInFrustum(maxX, yLo, maxZ, fullView, projection)
                || cornerInFrustum(minX, yHi, maxZ, fullView, projection)
                || cornerInFrustum(maxX, yHi, maxZ, fullView, projection);
    }

    private static boolean cornerInFrustum(float x, float y, float z, Matrix4f fullView, Matrix4f projection) {
        CULL_VEC.set(x, y, z, 1f);
        fullView.transform(CULL_VEC);
        projection.transform(CULL_VEC);
        float w = CULL_VEC.w;
        if (w <= 0f) return false;
        return Math.abs(CULL_VEC.x) <= w && Math.abs(CULL_VEC.y) <= w
                && CULL_VEC.z >= -w && CULL_VEC.z <= w;
    }

    /** 区块中心到相机的水平距离平方（忽略 Y，仅地面距离）。 */
    private static double distSq(LODMesh mesh, net.minecraft.world.phys.Vec3 camPos) {
        double dx = mesh.centerX() - camPos.x;
        double dz = mesh.centerZ() - camPos.z;
        return dx * dx + dz * dz;
    }

    // ==================== 三档质量 uniform ====================

    /** HIGH 档顶面 sprite（草方块顶）的 atlas 位置。 */
    private static final ResourceLocation TOP_SPRITE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/grass_block_top");

    /** HIGH 档侧面 sprite（草方块侧，含草皮与土）的 atlas 位置。 */
    private static final ResourceLocation SIDE_SPRITE = ResourceLocation.fromNamespaceAndPath("minecraft", "block/grass_block_side");

    /** sprite UV 是否已解析（资源重载后需重建）。 */
    private static volatile boolean spriteUvResolved = false;

    /** 缓存 HIGH 档采样的 sprite UV 范围（渲染线程读取，资源重载时重建）。 */
    private static volatile float topU0, topV0, topU1, topV1;
    private static volatile float sideU0, sideV0, sideU1, sideV1;

    /** 缓存已绑定的 Sampler0 纹理 id（用于判断是否需要重新绑定）。 */
    private static volatile int boundAtlasId = -1;

    /**
     * 设置 LOD 三档质量对应的 shader uniform：{@code QualityMode}，以及 HIGH 档所需的
     * {@code Sampler0}（原版 terrain atlas）采样器与顶面/侧面的 sprite UV 范围。
     *
     * <p>必须在 {@link RenderSystem#setShader} 之后、draw 之前调用（渲染线程）。</p>
     */
    private static void applyQualityUniforms(ShaderInstance shader, Minecraft mc, boolean irisRendering) {
        com.example.ribbon.Config.LodQuality quality = com.example.ribbon.Config.getLodQuality();
        int mode = quality == com.example.ribbon.Config.LodQuality.HIGH ? 2
                : quality == com.example.ribbon.Config.LodQuality.MEDIUM ? 1 : 0;

        var q = shader.getUniform("QualityMode");
        if (q != null) q.set(mode);

        // 【光影模式】光影下 deferred 已结束，LOD 画在最终 sRGB framebuffer 上，
        // 不应重复叠方向光照/固定雾色（否则双重处理→偏暗偏色）。用 ShaderMode=1
        // 告诉 fsh 走简化路径（纯顶点色 + 极轻雾）。
        var sm = shader.getUniform("ShaderMode");
        if (sm != null) sm.set(irisRendering ? 1 : 0);

        if (mode < 2) return; // 低/中档不需要纹理采样

        // —— HIGH 档：绑定原版 terrain atlas + 设置 sprite UV ——
        try {
            AbstractTexture atlasTex = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            if (atlasTex != null) {
                int id = atlasTex.getId();
                if (id != boundAtlasId) {
                    shader.setSampler("Sampler0", id);
                    boundAtlasId = id;
                }
            }

            resolveSpriteUv(mc);

            setUniform1f(shader, "TopU0", topU0);
            setUniform1f(shader, "TopV0", topV0);
            setUniform1f(shader, "TopU1", topU1);
            setUniform1f(shader, "TopV1", topV1);
            setUniform1f(shader, "SideU0", sideU0);
            setUniform1f(shader, "SideV0", sideV0);
            setUniform1f(shader, "SideU1", sideU1);
            setUniform1f(shader, "SideV1", sideV1);
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon/LOD] HIGH 档 atlas 绑定失败，回退纯色: {}", t.toString());
        }
    }

    /** 解析顶面/侧面 sprite 的 atlas UV（惰性，资源重载后需重新解析）。 */
    private static void resolveSpriteUv(Minecraft mc) {
        if (spriteUvResolved) return;
        try {
            var topFn = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            TextureAtlasSprite top = topFn.apply(TOP_SPRITE);
            TextureAtlasSprite side = topFn.apply(SIDE_SPRITE);
            topU0 = top.getU0(); topV0 = top.getV0(); topU1 = top.getU1(); topV1 = top.getV1();
            sideU0 = side.getU0(); sideV0 = side.getV0(); sideU1 = side.getU1(); sideV1 = side.getV1();
            spriteUvResolved = true;
        } catch (Throwable t) {
            // 解析失败用全 atlas 兜底（0~1），颜色仍由 vertexColor 调制。
            topU0 = 0f; topV0 = 0f; topU1 = 1f; topV1 = 1f;
            sideU0 = 0f; sideV0 = 0f; sideU1 = 1f; sideV1 = 1f;
            LOGGER.warn("[Ribbon/LOD] sprite UV 解析失败，用全 atlas 兜底: {}", t.toString());
        }
    }

    /** 重置 sprite UV 缓存（资源重载时调用）。 */
    public static void invalidateSpriteUv() {
        spriteUvResolved = false;
        boundAtlasId = -1;
    }

    private static void setUniform1f(ShaderInstance shader, String name, float v) {
        var u = shader.getUniform(name);
        if (u != null) u.set(v);
    }
}