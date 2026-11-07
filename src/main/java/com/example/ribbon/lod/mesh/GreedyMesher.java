/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.mesh;

import com.example.ribbon.lod.data.LODChunk;
import com.example.ribbon.lod.data.LODColorPalette;

/**
 * 3D 体素表面提取：遍历体素，只对暴露在空气/透明中的面发射 quad。
 * 纯 CPU，不写 OpenGL，只通过 LODMesh.putVertex 写入 DirectByteBuffer（28 字节/顶点）。
 *
 * <p>关键修复（解决"辐射条带"）：水平 +X/-X/+Z/-Z 邻居一旦越出本区块（邻块范围），
 * 一律视为"实心"、不发面，避免在每个区块四周砌出整堵切面墙。顶/底（±Y）正常判空发面。</p>
 */
public final class GreedyMesher {

    /**
     * 区块边界防缝 epsilon：侧面 quad 的顶点会沿法线方向外扩这个极小量，
     * 让相邻两个 LOD 区块在共享边界上的侧面互相重叠一个亚像素宽度，从而
     * 消除浮点精度不一致导致的"1 像素缝隙"。取 1/2048 块（约 0.00049），
     * 视觉上不可见，但足以盖住光栅化时因坐标舍入产生的裂缝。
     */
    private static final float SEAM_EPSILON = 1.0f / 2048.0f;

    /**
     * 需要应用群系染色的 MapColor 判定（用 {@code col & 0xFFFFFF} 剥离 alpha 后比较）。
     * LOD 数据层只保留了方块静态 MapColor，没有方块身份，这是离线解码路径下唯一可用
     * 的判据。草类 → grass 染色、植物/树叶类 → foliage 染色、水体 → water 染色，
     * 与原版 {@code BlockColors} 的染色语义一致；石头/沙子/雪不受群系影响。
     */
    private static final int MAP_COLOR_GRASS = net.minecraft.world.level.material.MapColor.GRASS.col & 0xFFFFFF;
    private static final int MAP_COLOR_PLANT = net.minecraft.world.level.material.MapColor.PLANT.col & 0xFFFFFF;
    private static final int MAP_COLOR_WATER = net.minecraft.world.level.material.MapColor.WATER.col & 0xFFFFFF;

    /**
     * 对基础 MapColor 应用逐列群系染色。
     *
     * <p>只对「草 / 植物 / 水」三类做<b>逐通道乘法调制</b>（base × tint / 255），与原版
     * 草/树叶/水染色的乘法语义一致。染色值来自 {@code LODChunk.columnTintAt(x,z,ch)}，
     * 是后台解码 .mca 的 biomes 时按列算好的完整 RGB（每列 grass/foliage/water 三通道）。</p>
     *
     * @param baseArgb 方块基础色（ARGB，来自 MapColor）
     * @param tintRgb  该列对应类别的完整 RGB 染色值（0xRRGGBB）；0 表示无染色数据
     * @return 染色后的 ARGB
     */
    private static int applyBiomeTint(int baseArgb, int tintRgb) {
        if (tintRgb == 0) return baseArgb;
        int br = (baseArgb >>> 16) & 0xFF, bg = (baseArgb >>> 8) & 0xFF, bb = baseArgb & 0xFF;
        int tr = (tintRgb >>> 16) & 0xFF, tg = (tintRgb >>> 8) & 0xFF, tb = tintRgb & 0xFF;
        int r = (br * tr) / 255;
        int g = (bg * tg) / 255;
        int b = (bb * tb) / 255;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * 对颜色做亮度缩放（假 AO）：保留 alpha 与色调，仅按 {@code factor} 缩放 RGB。
     * 用于按面朝向制造明暗层次（顶面 1.0 / 侧面 0.8 / 底面 0.5）。
     */
    private static int shade(int argb, float factor) {
        int r = (int) (((argb >>> 16) & 0xFF) * factor);
        int g = (int) (((argb >>> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        if (r > 255) r = 255;
        if (g > 255) g = 255;
        if (b > 255) b = 255;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private GreedyMesher() {}

    /** 入口：签名与 LODManager 调用 build(chunk, mesh, lodLevel) 对齐。 */
    public static LODMesh build(LODChunk chunk, LODMesh mesh, int lodLevel) {
        mesh.setChunkPos(chunk.chunkX, chunk.chunkZ);
        mesh.setLodLevel(lodLevel);

        int yBias = (int) Math.round(com.example.ribbon.Config.getLodVerticalBias());
        int baseX = chunk.worldMinX();
        int baseZ = chunk.worldMinZ();
        int minSecY = chunk.minSectionIndex();
        final LODColorPalette colorPalette = LODColorPalette.getInstance();

        for (int s = 0; s < LODChunk.MAX_SECTIONS; s++) {
            short[] vox = chunk.sectionData(s);
            if (vox == null) continue;
            int baseY = (minSecY + s) * 16;
            for (int ly = 0; ly < 16; ly++) {
                int worldY = baseY + ly;
                int yRow = ly * 256;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (vox[x + z * 16 + yRow] < 0) continue; // 空气/透明
                        int color = colorPalette.colorAt(chunk.colorIndexAt(x, z, worldY));
                        // 【逐列群系染色】按该列 (x,z) 的 biome 染色值对草/树叶/水做逐通道乘法调制。
                        // tint 由后台解码 .mca 时算好（每列 grass/foliage/water 三个完整 RGB），
                        // 无染色数据时为 0（不染色）。按 MapColor 判定方块类别，取对应通道染色。
                        int baseRgb = color & 0xFFFFFF;
                        if (baseRgb == MAP_COLOR_GRASS) {
                            color = applyBiomeTint(color, chunk.columnTintAt(x, z, 0));
                        } else if (baseRgb == MAP_COLOR_PLANT) {
                            color = applyBiomeTint(color, chunk.columnTintAt(x, z, 1));
                        } else if (baseRgb == MAP_COLOR_WATER) {
                            color = applyBiomeTint(color, chunk.columnTintAt(x, z, 2));
                        }
                        // 【真实光照】解码 .mca 的 BlockLight/SkyLight（0~15），乘 16 转成
                        // 光照贴图 UV2 坐标（0~240）。这是 LOD 光影下「有明暗、不偏平」的关键：
                        // 此前写死满亮 240，等于把所有面都当正午直射，完全丢失日照明暗层次。
                        int lightU = chunk.blockLightAt(x, z, worldY) << 4;
                        int lightV = chunk.skyLightAt(x, z, worldY) << 4;
                        float wx = baseX + x, wz = baseZ + z, wy = worldY + yBias;

                        // 【假 AO 暗化】光影下 LOD 不进 GBuffer，无法用真实环境光遮蔽，
                        // 这里按面朝向人为暗化制造立体感：顶面 ×1.0、侧面 ×0.8、底面 ×0.5，
                        // 模拟天空光只照亮朝上/侧面的自然明暗层次，杜绝「整片均匀色块」的扁平感。
                        int sideColor = shade(color, 0.8f);
                        int bottomColor = shade(color, 0.5f);

                        // —— 竖直方向：天空/基岩剔除，正常发面 ——
                        if (chunk.isAir(x, z, worldY + 1))
                            emitTop(mesh, wx, wz, wy + 1, color, lightU, lightV);        // +Y
                        if (chunk.isAir(x, z, worldY - 1))
                            emitBottom(mesh, wx, wz, wy, bottomColor, lightU, lightV);   // -Y

                        // —— 水平方向：邻居越界(邻块)则不发面，只在区块内部判空 ——
                        int nxp = x + 1, nxm = x - 1, nzp = z + 1, nzm = z - 1;
                        if (nxp < 16 && chunk.isAir(nxp, z, worldY))
                            emitSideX(mesh, wx + 1, wz, wz + 1, wy, wy + 1, sideColor, true, lightU, lightV);  // +X
                        if (nxm >= 0 && chunk.isAir(nxm, z, worldY))
                            emitSideX(mesh, wx, wz, wz + 1, wy, wy + 1, sideColor, false, lightU, lightV);      // -X
                        if (nzp < 16 && chunk.isAir(x, nzp, worldY))
                            emitSideZ(mesh, wz + 1, wx, wx + 1, wy, wy + 1, sideColor, true, lightU, lightV);   // +Z
                        if (nzm >= 0 && chunk.isAir(x, nzm, worldY))
                            emitSideZ(mesh, wz, wx, wx + 1, wy, wy + 1, sideColor, false, lightU, lightV);      // -Z
                    }
                }
            }
        }

        mesh.markReady(chunk.sourceVersion);
        return mesh;
    }

    /**
     * 顶面（朝上，法线 +Y）。
     *
     * <p>UV 映射：按<b>面的世界坐标</b>平铺（x → u，z → v），而非中心点 0.5。
     * 这样相邻方块纹理连续、不产生"每格独立采样"的豆腐块感；远景即使将来
     * 接入真实纹理 atlas，也能与近景地形无缝衔接。法线严格 (0,1,0)。</p>
     */
    private static void emitTop(LODMesh mesh, float x0, float z0, float y, int color, int lightU, int lightV) {
        float x1 = x0 + 1, z1 = z0 + 1;
        // CCW（从 +Y 向下看）：(x0,z0) (x0,z1) (x1,z1) (x1,z0)
        mesh.putVertex(x0, y, z0, color, x0, z0, lightU, lightV, 0, 1, 0);
        mesh.putVertex(x0, y, z1, color, x0, z1, lightU, lightV, 0, 1, 0);
        mesh.putVertex(x1, y, z1, color, x1, z1, lightU, lightV, 0, 1, 0);
        mesh.putVertex(x1, y, z0, color, x1, z0, lightU, lightV, 0, 1, 0);
    }

    /** 底面（朝下，法线 -Y）。UV 按世界坐标平铺（x → u，z → v）。 */
    private static void emitBottom(LODMesh mesh, float x0, float z0, float y, int color, int lightU, int lightV) {
        float x1 = x0 + 1, z1 = z0 + 1;
        mesh.putVertex(x0, y, z0, color, x0, z0, lightU, lightV, 0, -1, 0);
        mesh.putVertex(x1, y, z0, color, x1, z0, lightU, lightV, 0, -1, 0);
        mesh.putVertex(x1, y, z1, color, x1, z1, lightU, lightV, 0, -1, 0);
        mesh.putVertex(x0, y, z1, color, x0, z1, lightU, lightV, 0, -1, 0);
    }

    /**
     * 沿 X 平面的竖直面（x=wallX）。pos=true 发 +X 法线面，否则发 -X 法线面。
     * UV 按面的世界坐标平铺：u 随 z（面内水平），v 随 y（面内竖直）。
     */
    private static void emitSideX(LODMesh mesh, float wallX, float zMin, float zMax,
                                  float yBot, float yTop, int color, boolean pos, int lightU, int lightV) {
        float nx = pos ? 1 : -1;
        // 沿法线外扩 epsilon，盖住相邻区块边界处的亚像素缝隙。
        float wx = wallX + nx * SEAM_EPSILON;
        if (pos) {
            mesh.putVertex(wx, yTop, zMax, color, zMax, yTop, lightU, lightV, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMax, color, zMax, yBot, lightU, lightV, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMin, color, zMin, yBot, lightU, lightV, nx, 0, 0);
            mesh.putVertex(wx, yTop, zMin, color, zMin, yTop, lightU, lightV, nx, 0, 0);
        } else {
            mesh.putVertex(wx, yTop, zMin, color, zMin, yTop, lightU, lightV, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMin, color, zMin, yBot, lightU, lightV, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMax, color, zMax, yBot, lightU, lightV, nx, 0, 0);
            mesh.putVertex(wx, yTop, zMax, color, zMax, yTop, lightU, lightV, nx, 0, 0);
        }
    }

    /**
     * 沿 Z 平面的竖直面（z=wallZ）。pos=true 发 +Z 法线面，否则发 -Z 法线面。
     * UV 按面的世界坐标平铺：u 随 x（面内水平），v 随 y（面内竖直）。
     */
    private static void emitSideZ(LODMesh mesh, float wallZ, float xMin, float xMax,
                                  float yBot, float yTop, int color, boolean pos, int lightU, int lightV) {
        float nz = pos ? 1 : -1;
        // 沿法线外扩 epsilon，盖住相邻区块边界处的亚像素缝隙。
        float wz = wallZ + nz * SEAM_EPSILON;
        if (pos) {
            mesh.putVertex(xMin, yTop, wz, color, xMin, yTop, lightU, lightV, 0, 0, nz);
            mesh.putVertex(xMin, yBot, wz, color, xMin, yBot, lightU, lightV, 0, 0, nz);
            mesh.putVertex(xMax, yBot, wz, color, xMax, yBot, lightU, lightV, 0, 0, nz);
            mesh.putVertex(xMax, yTop, wz, color, xMax, yTop, lightU, lightV, 0, 0, nz);
        } else {
            mesh.putVertex(xMax, yTop, wz, color, xMax, yTop, lightU, lightV, 0, 0, nz);
            mesh.putVertex(xMax, yBot, wz, color, xMax, yBot, lightU, lightV, 0, 0, nz);
            mesh.putVertex(xMin, yBot, wz, color, xMin, yBot, lightU, lightV, 0, 0, nz);
            mesh.putVertex(xMin, yTop, wz, color, xMin, yTop, lightU, lightV, 0, 0, nz);
        }
    }
}