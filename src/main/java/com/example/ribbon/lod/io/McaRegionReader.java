/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.io;

import com.example.ribbon.lod.data.LODChunk;
import com.example.ribbon.lod.data.LODColorPalette;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
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

    public McaRegionReader(Path regionDir, RegionStorageInfo storageInfo, int minBuildY) {
        this.regionDir = regionDir;
        this.storageInfo = storageInfo;
        this.minBuildY = minBuildY;
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

        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompound(i);
            int sectionY = section.getInt("Y");
            int arrIdx = sectionY - minSecY;
            if (arrIdx < 0 || arrIdx >= LODChunk.MAX_SECTIONS) continue;
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

        LODChunk chunk = LODChunk.create(cx, cz, minBuildY, secData, secColors, sourceVersion);
        // 没有任何实心方块 -> 视为无效区块，交给上层放弃（避免塌陷/空洞）
        for (short[] s : secData) if (s != null) return chunk;
        return null;
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