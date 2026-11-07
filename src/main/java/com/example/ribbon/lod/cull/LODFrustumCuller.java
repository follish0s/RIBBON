/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.cull;

import com.example.ribbon.lod.mesh.LODMesh;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * LOD 批次的 CPU 侧视锥剔除器。
 *
 * <h2>设计取舍</h2>
 * <p>复用原版 {@link Frustum}（{@code RenderLevelStageEvent#getFrustum()} 每帧提供），
 * 而非自建 Gribb-Hartmann 平面提取。原因：</p>
 * <ul>
 *   <li>原版视锥已按当前相机 VP 正确构建，直接复用零误差、零维护；</li>
 *   <li>{@code Frustum.isVisible(AABB)} 是标准 6 平面 AABB 测试，CPU 开销极低；</li>
 *   <li>避免重复实现提取逻辑，降低 bug 面。</li>
 * </ul>
 *
 * <h2>剔除策略</h2>
 * <p>先算每个 LODMesh 的世界包围盒（由高度图 minY/maxY 得到），再逐个测
 * {@code Frustum.isVisible}。远景批次数量可控（< 数百），线性遍历足够；
 * 若未来批次暴涨，可在此类内加"区域四叉树"做层次化剔除，接口不变。</p>
 */
public final class LODFrustumCuller {

    private LODFrustumCuller() {
    }

    /**
     * 剔除不可见批次。
     *
     * @param candidates 候选网格
     * @param frustum    当前帧视锥（来自 RenderLevelStageEvent.getFrustum()）
     * @return 可见的网格列表（顺序保持）
     */
    public static List<LODMesh> cull(List<LODMesh> candidates, Frustum frustum) {
        if (frustum == null) return candidates; // 无视锥信息时不剔除，宁多画不漏画

        List<LODMesh> visible = new ArrayList<>(candidates.size());
        for (LODMesh mesh : candidates) {
            if (mesh == null || mesh.vertexCount() == 0) continue;
            if (isVisible(mesh, frustum)) {
                visible.add(mesh);
            }
        }
        return visible;
    }

    /**
     * 判断单个网格是否可见：构造其世界包围盒并做视锥测试。
     *
     * <p>包围盒水平范围 = 16 方块（区块），垂直范围 = [-64, 320] 的宽松扩展，
     * 覆盖原版全高度，避免因侧面/裙边漏判。AABB 不可变（原版字段 final），
     * 每次构造新实例 —— 远景可见批次每帧仅数十个，分配开销可忽略，
     * 正确性优先于此处微优化。</p>
     */
    public static boolean isVisible(LODMesh mesh, Frustum frustum) {
        int minX = mesh.chunkX() * 16;
        int minZ = mesh.chunkZ() * 16;
        AABB box = new AABB(minX, -64, minZ, minX + 16, 320, minZ + 16);
        return frustum.isVisible(box);
    }

    /** 便捷重载：用中心点距离做远距剔除（配合 LOD 渲染距离配置）。 */
    public static List<LODMesh> cullWithDistance(List<LODMesh> candidates, Frustum frustum,
                                                 Vector3f cameraPos, double maxDistanceSq) {
        List<LODMesh> visible = cull(candidates, frustum);
        if (maxDistanceSq <= 0) return visible;

        List<LODMesh> inRange = new ArrayList<>(visible.size());
        for (LODMesh mesh : visible) {
            double cx = mesh.chunkX() * 16.0 + 8.0;
            double cz = mesh.chunkZ() * 16.0 + 8.0;
            double dx = cx - cameraPos.x;
            double dz = cz - cameraPos.z;
            double distSq = dx * dx + dz * dz;
            if (distSq <= maxDistanceSq) {
                inRange.add(mesh);
            }
        }
        return inRange;
    }
}
