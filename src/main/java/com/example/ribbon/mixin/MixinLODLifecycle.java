/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.Config;
import com.example.ribbon.lod.LODManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * 驱动 LODManager 生命周期的 Mixin：世界加载时启动、卸载时停止、每 tick 请求
 * 玩家周围的 LOD 区块。
 *
 * <h2>红线遵守</h2>
 * <p>本 Mixin 只做<b>纯通知</b>：调用 {@link LODManager} 的 start/stop/requestLOD，
 * 不修改任何原版字段、不触碰 OpenGL、不 new LevelChunk。所有失败都静默回退，
 * 绝不破坏原版世界加载流程。</p>
 */
@Mixin(Minecraft.class)
public abstract class MixinLODLifecycle {

    /** LOD 请求半径（区块）。远景 LOD 覆盖玩家周围该半径范围。 */
    private static final int DEFAULT_LOD_RADIUS_CHUNKS = 8;

    /** 请求节流：每 N tick 才重新计算一次玩家周围区块集合。 */
    private static final int DEFAULT_REQUEST_INTERVAL_TICKS = 5;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void ribbon$onLevelLoad(ClientLevel level, net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason reason,
                                    CallbackInfo ci) {
        try {
            if (level == null) return;
            // 远景 LOD 总开关：关闭时完全跳过（不启动调度器）
            if (!Config.isLodEnabled()) return;
            // 只在单人存档（或能解析到区域目录）时启用 LOD；解析失败则保持关闭。
            Minecraft mc = Minecraft.getInstance();
            Path regionDir = resolveRegionDir(mc, level);
            if (regionDir == null) return;

            RegionStorageInfo info = new RegionStorageInfo(
                    "main", level.dimension(), "chunk");
            int minBuildY = level.getMinBuildHeight();

            LODManager.getInstance().start(regionDir, info, minBuildY);
        } catch (Throwable t) {
            // 任何异常都静默，绝不干扰世界加载
        }
    }

    @Inject(method = "clearClientLevel", at = @At("HEAD"))
    private void ribbon$onLevelUnload(net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {
        try {
            LODManager.getInstance().stop();
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ribbon$onClientTick(CallbackInfo ci) {
        try {
            LODManager manager = LODManager.getInstance();
            if (!manager.isRunning()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;

            // 节流：每 requestIntervalTicks 才重算一次
            int requestInterval = Config.getLodRequestIntervalTicks();
            if (requestInterval <= 0) requestInterval = DEFAULT_REQUEST_INTERVAL_TICKS;
            if (mc.level.getGameTime() % requestInterval != 0) return;

            int radius = Config.getLodRadiusChunks();
            if (radius <= 0) radius = DEFAULT_LOD_RADIUS_CHUNKS;

            // 【关键修复·环带中心对齐】请求中心必须与渲染端淘汰中心一致，
            // 否则 LOD 环带会围绕"玩家脚底区块"生成，而淘汰/绘制围绕"相机区块"，
            // 两者差几个区块时，环带整体偏移、边缘断层（表现为"LOD 一直偏离一点"）。
            // 统一用相机（眼睛）位置取区块坐标，与 LODRenderEventHandler 的
            // camChunkX/camChunkZ（floor(camPos.x/16)）完全同源。
            net.minecraft.world.phys.Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            int camChunkX = (int) Math.floor(cam.x / 16.0);
            int camChunkZ = (int) Math.floor(cam.z / 16.0);
            ChunkPos center = new ChunkPos(camChunkX, camChunkZ);

            // 内圈 = 原版有效渲染距离（原版区块会接管这部分，LOD 不应重叠画）。
            // 之前写死跳过 4 区块是错的——原版渲染距离可能是 2~32 区块，
            // 写死 4 会导致 LOD 环带要么与原版重叠（renderDistance 大时），
            // 要么中间留出真空带（renderDistance 小时，如 2 区块时 32~64 格空白）。
            // 正确做法：从原版有效渲染距离之外开始接管。
            int innerRadius = Math.max(mc.options.getEffectiveRenderDistance(), 2);

            // 【回退·范围修正】外半径 = innerRadius + radius（radius 默认 8 区块）。
            // 关键认知：LOD 只能基于实际存在的 .mca 数据生成，无法凭空请求未探索区块。
            // 世界实际探索范围通常只有 8~16 区块半径（由 .mca 区域文件数量决定），
            // 把 outerRadius 强行对齐 viewDistance(256 区块) 会导致：
            //   1. 每 tick 构建数十万 ChunkPos 的 HashSet，主线程卡顿（"更碎"直接原因）
            //   2. 后台线程对 99.99% 不存在的区块空转 readLODChunk
            //   3. 玩家附近有数据的区块反而被海量空请求淹没
            // 正确做法：radius 语义保持"原版渲染距离之外的额外环带宽度"，
            // 覆盖范围 = 原版渲染距离 + radius，与玩家实际探索范围匹配。
            int outerRadius = innerRadius + radius;

            Set<ChunkPos> wanted = new HashSet<>();
            // 【关键修复·圆形判定对齐原版】原版 Embeddium 的渲染距离判定是圆形
            // （欧氏距离 dx²+dz² < renderDistance²，见 RenderSectionDistanceFilter.DEFAULT），
            // 不是方形 Chebyshev。若 LOD 用方形环带，会在四个角落与原版圆形渲染范围
            // 错配：角落处 LOD 已被方形内圈跳过、但原版圆形渲染也未覆盖 → 夹缝；
            // 反之角落 LOD 与原版重叠 → "落下又变 LOD"。改用欧氏距离圆形判定，
            // LOD 环带与原版渲染边界严格对齐。
            int innerSq = innerRadius * innerRadius;
            int outerSq = outerRadius * outerRadius;
            for (int dx = -outerRadius; dx <= outerRadius; dx++) {
                for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                    int d2 = dx * dx + dz * dz;
                    if (d2 > innerSq && d2 <= outerSq) {
                        wanted.add(new ChunkPos(center.x + dx, center.z + dz));
                    }
                }
            }
            manager.requestLOD(wanted);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 解析世界保存目录下的 "region" 目录。
     *
     * <p>单人：{@code getSingleplayerServer().getWorldPath(new LevelResource("region"))}；
     * 多人（联机）：无 IntegratedServer，返回 {@code null}，LOD 保持关闭（安全降级）。</p>
     */
    private static Path resolveRegionDir(Minecraft mc, ClientLevel level) {
        try {
            net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
            if (server == null) return null; // 联机，本地无完整 .mca
            Path region = server.getWorldPath(new net.minecraft.world.level.storage.LevelResource("region"));
            if (region != null && java.nio.file.Files.isDirectory(region)) {
                return region;
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
