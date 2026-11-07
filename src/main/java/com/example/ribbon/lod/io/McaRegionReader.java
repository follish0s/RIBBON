/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.io;

import com.example.ribbon.lod.data.LODChunk;
import com.example.ribbon.lod.data.LODColorPalette;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 直读 .mca，解码完整 3D 体素（palette 索引）+ 每 palette 的 MapColor，供 LODChunk 使用。
 * 不构造 LevelChunk、不碰 DataFixer/ChunkSerializer。
 */
public final class McaRegionReader implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/LOD-IO");

    private final ConcurrentHashMap<Long, RegionFile> regionFiles = new ConcurrentHashMap<>();
    private final Path regionDir;
    private final RegionStorageInfo storageInfo;
    private final int minBuildY;

    /** 生物群系 registry 的只读快照（{@code RegistryAccess} 的 Frozen 不可变，线程安全）。
     *  用于后台线程解码 .mca 的 biomes 时把 biome 字符串 id 解析成染色值，不碰主线程世界状态。 */
    private final Registry<Biome> biomeRegistry;

    public McaRegionReader(Path regionDir, RegionStorageInfo storageInfo, int minBuildY,
                           RegistryAccess registryAccess) {
        this.regionDir = regionDir;
        this.storageInfo = storageInfo;
        this.minBuildY = minBuildY;
        Registry<Biome> br = null;
        if (registryAccess != null) {
            try {
                br = registryAccess.registryOrThrow(Registries.BIOME);
            } catch (Throwable ignored) {
                br = null;
            }
        }
        this.biomeRegistry = br;
    }

    /** LODManager 的入口：读一个区块的体素快照。 */
    public LODChunk readLODChunk(ChunkPos chunkPos) {
        RegionFile region = getOrOpenRegion(chunkPos);
        if (region == null) return null;
        try {
            if (!region.hasChunk(chunkPos)) return null;
            long sourceVersion = Files.getLastModifiedTime(region.getPath()).toMillis();
            DataInputStream in = region.getChunkDataInputStream(chunkPos);
            if (in == null) return null;
            try {
                CompoundTag tag = NbtIo.read(in);
                return decode(chunkPos.x, chunkPos.z, tag, sourceVersion);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            LOGGER.debug("[Ribbon/LOD] 读取区块 {} 失败：{}", chunkPos, e.toString());
            return null;
        }
    }

    private LODChunk decode(int cx, int cz, CompoundTag root, long sourceVersion) {
        if (root == null || !root.contains("sections", Tag.TAG_LIST)) return null;
        ListTag sections = root.getList("sections", Tag.TAG_COMPOUND);
        if (sections.isEmpty()) return null;

        int minSecY = minBuildY >> 4;
        short[][] secData = new short[LODChunk.MAX_SECTIONS][];
        byte[][] secColors = new byte[LODChunk.MAX_SECTIONS][];
        byte[][] blockLight = new byte[LODChunk.MAX_SECTIONS][];
        byte[][] skyLight = new byte[LODChunk.MAX_SECTIONS][];

        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompound(i);
            int sectionY = section.getInt("Y");
            int arrIdx = sectionY - minSecY;
            if (arrIdx < 0 || arrIdx >= LODChunk.MAX_SECTIONS) continue;

            // 光照数据（DataLayer，tag type 7 = byte array，每 section 2048 字节半字节编码）
            if (section.contains("BlockLight", Tag.TAG_BYTE_ARRAY)) {
                byte[] bl = section.getByteArray("BlockLight");
                if (bl.length == 2048) blockLight[arrIdx] = bl;
            }
            if (section.contains("SkyLight", Tag.TAG_BYTE_ARRAY)) {
                byte[] sl = section.getByteArray("SkyLight");
                if (sl.length == 2048) skyLight[arrIdx] = sl;
            }

            if (!section.contains("block_states", Tag.TAG_COMPOUND)) continue;

            CompoundTag blockStates = section.getCompound("block_states");
            if (!blockStates.contains("palette", Tag.TAG_LIST)) continue;
            ListTag paletteList = blockStates.getList("palette", Tag.TAG_COMPOUND);
            if (paletteList.isEmpty()) continue;

            // 解析 palette -> BlockState，并预计算：是否空气、MapColor 全局索引
            boolean[] air = new boolean[paletteList.size()];
            byte[] colors = new byte[paletteList.size()];
            boolean anySolid = false;
            for (int p = 0; p < paletteList.size(); p++) {
                CompoundTag entry = paletteList.getCompound(p);
                BlockState st = null;
                try {
                    st = BlockState.CODEC.parse(NbtOps.INSTANCE, entry).result().orElse(null);
                } catch (Throwable ignored) {}
                if (st == null || st.isAir()) {
                    air[p] = true;
                    colors[p] = 0;
                } else {
                    air[p] = false;
                    int argb = BlockStateColorExtractor.extractArgb(st);
                    colors[p] = (byte) LODColorPalette.getInstance().intern(argb);
                    anySolid = true;
                }
            }
            if (!anySolid) continue; // 全空气 section，跳过

            long[] data = blockStates.getLongArray("data");
            short[] voxels = new short[4096];
            if (data.length == 0) {
                // 单色 section：整段都是 palette[0]
                short id = (short) (air[0] ? -1 : 0);
                java.util.Arrays.fill(voxels, id);
            } else {
                int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteList.size() - 1));
                for (int v = 0; v < 4096; v++) {
                    int id = readPacked(data, v, bits);
                    if (id < 0 || id >= air.length || air[id]) {
                        voxels[v] = -1;
                    } else {
                        voxels[v] = (short) id;
                    }
                }
            }
            secData[arrIdx] = voxels;
            secColors[arrIdx] = colors;
        }

        LODChunk chunk = LODChunk.create(cx, cz, minBuildY, secData, secColors, sourceVersion,
                decodeBiomeTint(root, cx, cz), blockLight, skyLight);
        // 没有任何实心方块 -> 视为无效区块，交给上层放弃（避免塌陷/空洞）
        for (short[] s : secData) if (s != null) return chunk;
        return null;
    }

    /**
     * 解码该区块的逐列群系染色（16×16 列，每列一个打包 RGB）。
     *
     * <p>.mca 里 biome 存于 {@code sections[].biomes}，是 4×4×4 的
     * {@code PalettedContainer}（{@code Strategy.SECTION_BIOMES}）。我们只取每列的
     * <b>顶面</b>（各 section 的 y=0~3 行即地面层附近）的 biome，逐列算出
     * grass/foliage/water 三通道染色并打包，供渲染端乘法调制。</p>
     *
     * <p>染色值来源：{@code biome.getGrassColor(x,z)}（含噪声，用列的世界坐标）、
     * {@code biome.getFoliageColor()}、{@code biome.getWaterColor()}，与原版
     * {@code BlockColors} 的草/树叶/水染色语义一致。若 registry 快照缺失或 biome
     * 解析失败，返回 {@code null}，渲染端按中性（不染色）兜底。</p>
     */
    private int[] decodeBiomeTint(CompoundTag root, int cx, int cz) {
        if (biomeRegistry == null) return null;
        try {
            if (!root.contains("sections", Tag.TAG_LIST)) return null;
            ListTag sections = root.getList("sections", Tag.TAG_COMPOUND);
            if (sections.isEmpty()) return null;

            int[] tint = new int[16 * 16 * 3];
            boolean any = false;

            for (int i = 0; i < sections.size(); i++) {
                CompoundTag section = sections.getCompound(i);
                if (!section.contains("biomes", Tag.TAG_COMPOUND)) continue;
                CompoundTag biomesTag = section.getCompound("biomes");
                if (!biomesTag.contains("palette", Tag.TAG_LIST)) continue;

                // 解析 palette：字符串 biome id → Holder<Biome>
                ListTag paletteList = biomesTag.getList("palette", Tag.TAG_STRING);
                Holder<Biome>[] palette = new Holder[paletteList.size()];
                boolean paletteOk = true;
                for (int p = 0; p < paletteList.size(); p++) {
                    String id = paletteList.getString(p);
                    var holder = biomeRegistry.getHolder(net.minecraft.resources.ResourceLocation.tryParse(id));
                    if (holder.isEmpty()) { paletteOk = false; break; }
                    palette[p] = holder.get();
                }
                if (!paletteOk) continue;

                long[] data = biomesTag.getLongArray("data");
                if (data.length == 0) {
                    // 单色 section：整段都是 palette[0]
                    // 只影响地面层（4×4×4 的 y=0 层对应区块内 16×16 的顶部几格）
                    fillBiomeTint(tint, palette[0], cx, cz);
                    any = true;
                    continue;
                }

                int bits = Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(paletteList.size() - 1));
                // 4×4×4 容器：64 个元素。只取 y=0 层（前 16 个）作为该列地表 biome。
                for (int y = 0; y < 4; y++) {
                    for (int z = 0; z < 4; z++) {
                        for (int x = 0; x < 4; x++) {
                            int idx = (y * 16) + (z * 4) + x;
                            int id = readPacked(data, idx, bits);
                            if (id < 0 || id >= palette.length) continue;
                            // 把 4×4 的 biome 上采样到 16×16 列：每个 biome 覆盖 4×4 列
                            Holder<Biome> h = palette[id];
                            if (h == null) continue;
                            Biome b = h.value();
                            if (b == null) continue;
                            // 列的世界坐标（用于 getGrassColor 的噪声采样）
                            int wx = cx * 16 + x * 4 + 2;
                            int wz = cz * 16 + z * 4 + 2;
                            int grass = b.getGrassColor(wx, wz);
                            int foliage = b.getFoliageColor();
                            int water = b.getWaterColor();
                            for (int dz = 0; dz < 4; dz++) {
                                for (int dx = 0; dx < 4; dx++) {
                                    int base = ((z * 4 + dz) * 16 + (x * 4 + dx)) * 3;
                                    tint[base] = grass;
                                    tint[base + 1] = foliage;
                                    tint[base + 2] = water;
                                }
                            }
                            any = true;
                        }
                    }
                }
            }

            return any ? tint : null;
        } catch (Throwable t) {
            LOGGER.debug("[Ribbon/LOD] 解码群系染色失败：{}", t.toString());
            return null;
        }
    }

    /** 单色 section 兜底：把该 biome 的染色填满全部 16×16 列。 */
    private void fillBiomeTint(int[] tint, Holder<Biome> h, int cx, int cz) {
        if (h == null) return;
        Biome b = h.value();
        if (b == null) return;
        int grass = b.getGrassColor(cx * 16 + 8, cz * 16 + 8);
        int foliage = b.getFoliageColor();
        int water = b.getWaterColor();
        for (int i = 0; i < 16 * 16; i++) {
            int base = i * 3;
            tint[base] = grass;
            tint[base + 1] = foliage;
            tint[base + 2] = water;
        }
    }

    private static int readPacked(long[] data, int index, int bits) {
        int perLong = 64 / bits;
        int longIndex = index / perLong;
        int bitOffset = (index % perLong) * bits;
        if (longIndex >= data.length) return -1;
        long mask = (1L << bits) - 1L;
        return (int) ((data[longIndex] >>> bitOffset) & mask);
    }

    private RegionFile getOrOpenRegion(ChunkPos chunkPos) {
        int rx = chunkPos.getRegionX();
        int rz = chunkPos.getRegionZ();
        long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
        RegionFile existing = regionFiles.get(key);
        if (existing != null) return existing;
        String fileName = "r." + rx + "." + rz + ".mca";
        Path regionPath = regionDir.resolve(fileName);
        if (!Files.exists(regionPath)) return null;
        try {
            RegionFile rf = new RegionFile(storageInfo, regionPath, regionDir, true);
            RegionFile raced = regionFiles.putIfAbsent(key, rf);
            return raced != null ? raced : rf;
        } catch (IOException e) {
            LOGGER.debug("[Ribbon/LOD] 打开区域文件 {} 失败：{}", fileName, e.toString());
            return null;
        }
    }

    @Override
    public void close() {
        for (RegionFile rf : regionFiles.values()) {
            try { rf.close(); } catch (IOException ignored) {}
        }
        regionFiles.clear();
    }
}