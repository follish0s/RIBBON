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

    /**
     * 逐列群系染色快照：每列存 3 个完整 RGB 染色值（grass / foliage / water）。
     *
     * <p>存储布局：{@code columnTint[(x + z*16) * 3 + ch]}，{@code ch} 为
     * 0=grass、1=foliage、2=water，每个值都是<b>完整 RGB</b>（0xRRGGBB，低 24 位）。
     * 由后台解码线程在读取 .mca 的 biomes PalettedContainer 时一次性算好
     * （持有 RegistryAccess 只读快照），渲染端 {@code GreedyMesher} 按体素列坐标
     * 查表做逐通道乘法调制，零锁、零 registry 访问。</p>
     *
     * <p>为 {@code null} 表示解码时未取到 biome（或全中性兜底），调用方按中性
     * （不染色）处理。</p>
     */
    private final int[] columnTint;

    /** 方块光照（BlockLight）DataLayer 原始数据：[sec][2048]，半字节编码。null 表示未解码。 */
    private final byte[][] blockLight;

    /** 天空光照（SkyLight）DataLayer 原始数据：[sec][2048]，半字节编码。null 表示未解码。 */
    private final byte[][] skyLight;

    private LODChunk(int chunkX, int chunkZ, int minBuildY, short[][] sections,
                     byte[][] paletteColors, long sourceVersion, int minY, int maxY,
                     int[] columnTint, byte[][] blockLight, byte[][] skyLight) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = sections;
        this.paletteColors = paletteColors;
        this.minSecY = minBuildY >> 4;
        this.sourceVersion = sourceVersion;
        this.minY = minY;
        this.maxY = maxY;
        this.columnTint = columnTint;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
    }

    public static LODChunk create(int chunkX, int chunkZ, int minBuildY,
                                  short[][] sections, byte[][] paletteColors, long sourceVersion) {
        return create(chunkX, chunkZ, minBuildY, sections, paletteColors, sourceVersion, null, null, null);
    }

    public static LODChunk create(int chunkX, int chunkZ, int minBuildY,
                                  short[][] sections, byte[][] paletteColors, long sourceVersion,
                                  int[] columnTint, byte[][] blockLight, byte[][] skyLight) {
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
        return new LODChunk(chunkX, chunkZ, minBuildY, sections, paletteColors, sourceVersion, minY, maxY,
                columnTint, blockLight, skyLight);
    }

    /**
     * 取世界坐标处的天空光照（0~15）。无数据时返回 15（满亮，与原版"无天空光"维度一致）。
     * DataLayer 编码：索引 = y<<8 | z<<4 | x，每字节两方块（低 4bit=偶索引，高 4bit=奇索引）。
     */
    public int skyLightAt(int x, int z, int worldY) {
        return lightAt(skyLight, x, z, worldY, 15);
    }

    /** 取世界坐标处的方块光照（0~15）。无数据时返回 0。 */
    public int blockLightAt(int x, int z, int worldY) {
        return lightAt(blockLight, x, z, worldY, 0);
    }

    private int lightAt(byte[][] layers, int x, int z, int worldY, int fallback) {
        if (layers == null) return fallback;
        if (x < 0 || x >= SIZE || z < 0 || z >= SIZE) return fallback;
        int secY = worldY >> 4;
        int s = secY - minSecY;
        if (s < 0 || s >= layers.length) return fallback;
        byte[] layer = layers[s];
        if (layer == null) return fallback;
        int localY = worldY - secY * 16;
        int idx = (localY << 8) | (z << 4) | x; // 对齐 DataLayer.getIndex(x,y,z)=y<<8|z<<4|x
        byte b = layer[idx >> 1];
        return (idx & 1) == 0 ? (b & 0xF) : ((b >> 4) & 0xF);
    }

    /** 是否有天空光照数据（诊断用）。 */
    public boolean hasSkyLight() { return skyLight != null; }

    /**
     * 取 (x,z) 列的某个染色通道（完整 RGB，0xRRGGBB）。
     *
     * @param x 区块内列 x（0~15）
     * @param z 区块内列 z（0~15）
     * @param channel 0=grass、1=foliage、2=water
     * @return 该列该通道的完整 RGB 染色值；无染色数据时返回 0（调用方按中性处理）
     */
    public int columnTintAt(int x, int z, int channel) {
        if (columnTint == null) return 0;
        return columnTint[(x + z * SIZE) * 3 + channel] & 0xFFFFFF;
    }

    /** 是否有逐列染色数据（诊断用）。 */
    public boolean hasColumnTint() { return columnTint != null; }

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