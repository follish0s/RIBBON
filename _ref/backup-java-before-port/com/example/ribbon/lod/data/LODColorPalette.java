/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.data;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * LOD 地表颜色的全局调色板（Voxy "BlockState → MapColor → Palette" 管线的本地移植）。
 *
 * <p>设计目标：把每个地表方块的真实颜色（{@code MapColor.col} 得到的 ARGB int）
 * 压缩成一个 0~255 的调色板索引，体素层（{@code LODChunk}）只存 {@code byte}
 * 索引，渲染层（{@code GreedyMesher} / {@code LODVboUploader}）再查表还原 ARGB。
 * 这样既保留真实地形色（草绿 / 土黄 / 水蓝 / 雪白），又不让每个顶点/每列
 * 携带 4 字节颜色，内存与带宽都远优于逐列存 ARGB。</p>
 *
 * <h2>线程模型</h2>
 * <p>调色板是<b>全局单例</b>，由后台线程（{@code McaRegionReader} 解码时）与
 * 渲染线程（{@code LODVboUploader} 查表）并发访问。因此内部结构必须无锁安全：
 * </p>
 * <ul>
 *   <li>{@link #indexOf} 只读 {@link #palette}，多线程并发读 {@code Int2IntOpenHashMap}
 *       是安全的（fastutil 的 get 无结构变更）；</li>
 *   <li>分配新色（{@link #intern}）仅在后台解码线程调用，且用 {@code synchronized}
 *       串行化，保证写线程唯一，避免结构并发修改。</li>
 * </ul>
 *
 * <h2>容量与溢出</h2>
 * <p>上限 {@link #MAX_COLORS}=256，因为索引用 {@code byte} 存储。溢出时不做
 * "丢弃"，而是把新色<b>合并到调色板里最近的已有色</b>（按 RGB 欧氏距离），
 * 保证任何方块都有可渲染颜色，只是远处罕见色会被近似。</p>
 */
public final class LODColorPalette {

    /** 调色板容量上限（索引用 byte，0~255）。 */
    public static final int MAX_COLORS = 256;

    /** 全局单例。 */
    private static final LODColorPalette INSTANCE = new LODColorPalette();

    /**
     * 反向表：ARGB(int) → palette index(0~255)。
     * <p>注意 key 统一存<b>不透明 ARGB</b>（alpha 强制 0xFF），因为 LOD 远景
     * 一律不透明渲染，alpha 差异无需进入调色板，可显著降低分裂度。</p>
     */
    private final Int2IntOpenHashMap palette = new Int2IntOpenHashMap();

    /** 正向表：palette index → ARGB(int)，供渲染线程 O(1) 查表。 */
    private final int[] colors = new int[MAX_COLORS];

    /** 已分配的颜色数量（0~256）。 */
    private int count;

    private LODColorPalette() {
        palette.defaultReturnValue(-1);
    }

    public static LODColorPalette getInstance() {
        return INSTANCE;
    }

    /**
     * 取指定 ARGB 的调色板索引；若调色板中不存在，则分配新索引（或溢出时
     * 合并到最近色）。线程安全：分配路径 {@code synchronized} 串行化。
     *
     * @param argb 目标颜色（alpha 会被强制置为 0xFF）
     * @return 0~255 的调色板索引
     */
    public synchronized int intern(int argb) {
        int key = argb | 0xFF000000; // 强制不透明，降低分裂度
        int existing = palette.get(key);
        if (existing >= 0) {
            return existing;
        }
        if (count < MAX_COLORS) {
            int idx = count++;
            palette.put(key, idx);
            colors[idx] = key;
            return idx;
        }
        // 溢出：合并到 RGB 欧氏距离最近的已有色
        return nearestIndex(key);
    }

    /**
     * 查调色板索引对应的 ARGB（渲染线程调用，无锁）。
     * 索引越界或未分配时兜底返回不透明白。
     */
    public int colorAt(int index) {
        if (index < 0 || index >= count) {
            return 0xFFFFFFFF;
        }
        return colors[index];
    }

    /** 当前已分配颜色数（诊断用）。 */
    public int size() {
        return count;
    }

    /** 重置调色板（世界卸载时调用，清空重建）。 */
    public synchronized void reset() {
        palette.clear();
        count = 0;
    }

    /** 计算与 {@code argb} RGB 欧氏距离最近的颜色索引。 */
    private int nearestIndex(int argb) {
        int r0 = (argb >>> 16) & 0xFF;
        int g0 = (argb >>> 8) & 0xFF;
        int b0 = argb & 0xFF;
        int best = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int c = colors[i];
            int dr = ((c >>> 16) & 0xFF) - r0;
            int dg = ((c >>> 8) & 0xFF) - g0;
            int db = (c & 0xFF) - b0;
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }
}
