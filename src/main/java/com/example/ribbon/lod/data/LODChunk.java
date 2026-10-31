/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.data;

import net.minecraft.core.SectionPos;

/**
 * 远景 LOD 的 3D 体素快照。构造后不可变，可多线程无锁共享读取。
 *
 * <p>体素编码：sections[sec][voxelIndex]，voxelIndex = x + z*16 + localY*256；
 * 值为 -1 表示空气/透明，>=0 表示不透明方块的 palette 索引（用于查本 section 的
 * paletteColors 表得到全局颜色索引）。paletteColors[sec][i] 为 palette 条目 i 的
 * 全局 LODColorPalette 索引（byte，0~255）。</p>
 */
public final class LODChunk {

    public static final int SIZE = 16;

    /** 世界覆盖 -64~320 共 24 个 section。 */
    public static final int MAX_SECTIONS = 24;

    public final int chunkX;
    public final int chunkZ;

    private final short[][] sections;   // [sec][4096]，-1=空气，>=0=palette索引
    private final byte[][] paletteColors; // [sec][paletteSize]，palette索引->全局colorIndex

    /** 最低 section 的世界索引（= minBuildY >> 4）。 */
    private final int minSecY;

    public final long sourceVersion;
    public final int minY;
    public final int maxY;

    private LODChunk(int chunkX, int chunkZ, int minBuildY, short[][] sections,
                     byte[][] paletteColors, long sourceVersion, int minY, int maxY) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = sections;
        this.paletteColors = paletteColors;
        this.minSecY = minBuildY >> 4;
        this.sourceVersion = sourceVersion;
        this.minY = minY;
        this.maxY = maxY;
    }

    public static LODChunk create(int chunkX, int chunkZ, int minBuildY,
                                  short[][] sections, byte[][] paletteColors, long sourceVersion) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int baseSecY = minBuildY >> 4;
        for (int s = 0; s < sections.length; s++) {
            short[] sec = sections[s];
            if (sec == null) continue;
            boolean has = false;
            for (short v : sec) { if (v >= 0) { has = true; break; } }
            if (has) {
                int base = (baseSecY + s) * 16;
                if (base < minY) minY = base;
                if (base + 15 > maxY) maxY = base + 15;
            }
        }
        if (minY == Integer.MAX_VALUE) { minY = minBuildY; maxY = minBuildY; }
        return new LODChunk(chunkX, chunkZ, minBuildY, sections, paletteColors, sourceVersion, minY, maxY);
    }

    /** 世界坐标是否为空气/透明（越界一律视为空，保证区块边界正常发面）。 */
    public boolean isAir(int x, int z, int worldY) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return true;
        int secY = worldY >> 4;
        int s = secY - minSecY;
        if (s < 0 || s >= sections.length) return true;
        short[] sec = sections[s];
        if (sec == null) return true;
        int localY = worldY - secY * 16;
        return sec[x + z * SIZE + localY * SIZE * SIZE] < 0;
    }

    /** 世界坐标不透明方块的全局颜色索引（0~255）。空气返回 0。 */
    public int colorIndexAt(int x, int z, int worldY) {
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return 0;
        int secY = worldY >> 4;
        int s = secY - minSecY;
        if (s < 0 || s >= sections.length) return 0;
        short[] sec = sections[s];
        byte[] colors = paletteColors[s];
        if (sec == null || colors == null) return 0;
        int localY = worldY - secY * 16;
        int pid = sec[x + z * SIZE + localY * SIZE * SIZE];
        if (pid < 0 || pid >= colors.length) return 0;
        return colors[pid] & 0xFF;
    }

    /** 取某 section 的数据数组（供 mesher 高效遍历），越界返回 null。 */
    public short[] sectionData(int s) {
        if (s < 0 || s >= sections.length) return null;
        return sections[s];
    }

    public int minSectionIndex() { return minSecY; }

    public int worldMinX() { return SectionPos.sectionToBlockCoord(chunkX); }
    public int worldMinZ() { return SectionPos.sectionToBlockCoord(chunkZ); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LODChunk other)) return false;
        return chunkX == other.chunkX && chunkZ == other.chunkZ && sourceVersion == other.sourceVersion;
    }
    @Override public int hashCode() { return java.util.Objects.hash(chunkX, chunkZ, sourceVersion); }
}