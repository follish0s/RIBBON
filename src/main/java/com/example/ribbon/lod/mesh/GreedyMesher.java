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
     * 光照贴图 UV 默认值。LOD 远景没有真实的区块光照数据（不 new LevelChunk、不查
     * 原版光照引擎），统一填满光照（240 = 0xF0 = 满亮档），避免光影下远景全黑。
     * 值对齐原版 {@code LightTexture} 的满亮档位（FULL_BRIGHT = 0xF000F0，
     * block<<4 = 0xF0，sky<<4 = 0xF0）。
     */
    private static final int SKY_LIGHT_U = 240;
    private static final int SKY_LIGHT_V = 240;

    /**
     * 区块边界防缝 epsilon：侧面 quad 的顶点会沿法线方向外扩这个极小量，
     * 让相邻两个 LOD 区块在共享边界上的侧面互相重叠一个亚像素宽度，从而
     * 消除浮点精度不一致导致的"1 像素缝隙"。取 1/2048 块（约 0.00049），
     * 视觉上不可见，但足以盖住光栅化时因坐标舍入产生的裂缝。
     */
    private static final float SEAM_EPSILON = 1.0f / 2048.0f;

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
                        float wx = baseX + x, wz = baseZ + z, wy = worldY + yBias;

                        // —— 竖直方向：天空/基岩剔除，正常发面 ——
                        if (chunk.isAir(x, z, worldY + 1))
                            emitTop(mesh, wx, wz, wy + 1, color);                 // +Y
                        if (chunk.isAir(x, z, worldY - 1))
                            emitBottom(mesh, wx, wz, wy, color);                  // -Y

                        // —— 水平方向：邻居越界(邻块)则不发面，只在区块内部判空 ——
                        int nxp = x + 1, nxm = x - 1, nzp = z + 1, nzm = z - 1;
                        if (nxp < 16 && chunk.isAir(nxp, z, worldY))
                            emitSideX(mesh, wx + 1, wz, wz + 1, wy, wy + 1, color, true);  // +X
                        if (nxm >= 0 && chunk.isAir(nxm, z, worldY))
                            emitSideX(mesh, wx, wz, wz + 1, wy, wy + 1, color, false);      // -X
                        if (nzp < 16 && chunk.isAir(x, nzp, worldY))
                            emitSideZ(mesh, wz + 1, wx, wx + 1, wy, wy + 1, color, true);   // +Z
                        if (nzm >= 0 && chunk.isAir(x, nzm, worldY))
                            emitSideZ(mesh, wz, wx, wx + 1, wy, wy + 1, color, false);      // -Z
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
    private static void emitTop(LODMesh mesh, float x0, float z0, float y, int color) {
        float x1 = x0 + 1, z1 = z0 + 1;
        // CCW（从 +Y 向下看）：(x0,z0) (x0,z1) (x1,z1) (x1,z0)
        mesh.putVertex(x0, y, z0, color, x0, z0, SKY_LIGHT_U, SKY_LIGHT_V, 0, 1, 0);
        mesh.putVertex(x0, y, z1, color, x0, z1, SKY_LIGHT_U, SKY_LIGHT_V, 0, 1, 0);
        mesh.putVertex(x1, y, z1, color, x1, z1, SKY_LIGHT_U, SKY_LIGHT_V, 0, 1, 0);
        mesh.putVertex(x1, y, z0, color, x1, z0, SKY_LIGHT_U, SKY_LIGHT_V, 0, 1, 0);
    }

    /** 底面（朝下，法线 -Y）。UV 按世界坐标平铺（x → u，z → v）。 */
    private static void emitBottom(LODMesh mesh, float x0, float z0, float y, int color) {
        float x1 = x0 + 1, z1 = z0 + 1;
        mesh.putVertex(x0, y, z0, color, x0, z0, SKY_LIGHT_U, SKY_LIGHT_V, 0, -1, 0);
        mesh.putVertex(x1, y, z0, color, x1, z0, SKY_LIGHT_U, SKY_LIGHT_V, 0, -1, 0);
        mesh.putVertex(x1, y, z1, color, x1, z1, SKY_LIGHT_U, SKY_LIGHT_V, 0, -1, 0);
        mesh.putVertex(x0, y, z1, color, x0, z1, SKY_LIGHT_U, SKY_LIGHT_V, 0, -1, 0);
    }

    /**
     * 沿 X 平面的竖直面（x=wallX）。pos=true 发 +X 法线面，否则发 -X 法线面。
     * UV 按面的世界坐标平铺：u 随 z（面内水平），v 随 y（面内竖直）。
     */
    private static void emitSideX(LODMesh mesh, float wallX, float zMin, float zMax,
                                  float yBot, float yTop, int color, boolean pos) {
        float nx = pos ? 1 : -1;
        // 沿法线外扩 epsilon，盖住相邻区块边界处的亚像素缝隙。
        float wx = wallX + nx * SEAM_EPSILON;
        if (pos) {
            mesh.putVertex(wx, yTop, zMax, color, zMax, yTop, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMax, color, zMax, yBot, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMin, color, zMin, yBot, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
            mesh.putVertex(wx, yTop, zMin, color, zMin, yTop, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
        } else {
            mesh.putVertex(wx, yTop, zMin, color, zMin, yTop, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMin, color, zMin, yBot, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
            mesh.putVertex(wx, yBot, zMax, color, zMax, yBot, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
            mesh.putVertex(wx, yTop, zMax, color, zMax, yTop, SKY_LIGHT_U, SKY_LIGHT_V, nx, 0, 0);
        }
    }

    /**
     * 沿 Z 平面的竖直面（z=wallZ）。pos=true 发 +Z 法线面，否则发 -Z 法线面。
     * UV 按面的世界坐标平铺：u 随 x（面内水平），v 随 y（面内竖直）。
     */
    private static void emitSideZ(LODMesh mesh, float wallZ, float xMin, float xMax,
                                  float yBot, float yTop, int color, boolean pos) {
        float nz = pos ? 1 : -1;
        // 沿法线外扩 epsilon，盖住相邻区块边界处的亚像素缝隙。
        float wz = wallZ + nz * SEAM_EPSILON;
        if (pos) {
            mesh.putVertex(xMin, yTop, wz, color, xMin, yTop, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
            mesh.putVertex(xMin, yBot, wz, color, xMin, yBot, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
            mesh.putVertex(xMax, yBot, wz, color, xMax, yBot, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
            mesh.putVertex(xMax, yTop, wz, color, xMax, yTop, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
        } else {
            mesh.putVertex(xMax, yTop, wz, color, xMax, yTop, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
            mesh.putVertex(xMax, yBot, wz, color, xMax, yBot, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
            mesh.putVertex(xMin, yBot, wz, color, xMin, yBot, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
            mesh.putVertex(xMin, yTop, wz, color, xMin, yTop, SKY_LIGHT_U, SKY_LIGHT_V, 0, 0, nz);
        }
    }
}