/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.lod.io;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.lang.reflect.Field;

/**
 * 从 {@link BlockState} 提取"静态默认 MapColor"的 ARGB 颜色，供 LOD 离线解码使用。
 *
 * <p>Voxy 的 "BlockState → MapColor" 映射在离线（无世界上下文）场景下需要拿到
 * <b>不依赖生物群系染色的默认色</b>。原版 {@code BlockState.getMapColor(BlockGetter,
 * BlockPos)} 对草方块/树叶/水会依赖 {@code BlockGetter} 做生物群系染色，离线读取时
 * 传入 {@code null} 会 NPE。因此这里<b>绕过</b>该重载，直接读取 {@code BlockStateBase}
 * 在构造时缓存的默认 {@code mapColor} 字段（{@code BlockBehaviour$Properties.mapColor}
 * 应用后的静态值）。</p>
 *
 * <p>该字段是 {@code private final MapColor mapColor}，位于父类
 * {@code net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase}，
 * 用一次反射定位后缓存 {@link Field} 句柄，之后每次只 {@code get}，开销可忽略。</p>
 *
 * <h2>兜底</h2>
 * <p>若反射失败（极端环境），退回 {@code blockState.getMapColor(null, BlockPos.ZERO)}，
 * 并捕获异常返回不透明白。保证任何情况下都不崩溃、不阻断解码。</p>
 */
public final class BlockStateColorExtractor {

    private static Field MAP_COLOR_FIELD;

    private static volatile boolean fieldResolved;

    private BlockStateColorExtractor() {
    }

    /**
     * 提取 {@code state} 的静态默认颜色，并做<b>去饱和 + 亮度归一</b>。
     *
     * <p>原版远景地形经过「生物群系色调 × 光照贴图 × 大气透视（雾）」三重调制后，
     * 饱和度会明显下降、整体偏灰。而 {@code MapColor.col} 是满饱和的纯色，直接用作
     * LOD 顶点色会导致远景"荧光绿 / 纯蓝 / 刺眼"，与近景偏灰的自然色调严重脱节。</p>
     *
     * <p>这里在提取阶段做一次静态去饱和：把 RGB 向灰度（luma）方向拉近
     * {@link #DESATURATION} 比例，并略微压暗，模拟大气透视的灰化效果。这与原版
     * {@code Color#multiply} + 雾效的视觉效果方向一致，但开销为零（离线一次性）。</p>
     *
     * @param state 目标方块状态（非空气）
     * @return 不透明 ARGB int（0xAARRGGBB）
     */
    public static int extractArgb(BlockState state) {
        MapColor mc = resolveDefaultMapColor(state);
        if (mc != null) {
            return desaturate(mc.col);
        }
        // 兜底：透明白 → 改用不透明白，避免透明顶点在 LOD 中不可见
        return 0xFFFFFFFF;
    }

    /**
     * 去饱和强度（0=保留原色，1=完全灰度）。原版 MapColor 本身已是中等饱和度，
     * 再叠 CPU 端去饱和会让草绿/树叶绿进一步变灰，远景显得"发灰发棕"、与原版
     * 鲜亮地形脱节。改为 0：完全保留 MapColor 满饱和色，远处的灰化交给 fsh 的
     * 大气透视雾（FOG）完成——雾本身就会把远景拉向灰蓝，无需 CPU 端预先去饱和。
     */
    private static final float DESATURATION = 0.0f;

    /** 亮度系数（0=纯黑，1=不变）。1.0 不压暗，保留原版 MapColor 的亮度层次。 */
    private static final float BRIGHTNESS = 1.0f;

    /**
     * 对 {@code MapColor.col}（<b>ARGB</b> 字节序，0xAARRGGBB）做去饱和 + 亮度归一，
     * 返回标准 <b>ARGB</b> int（0xAARRGGBB）。
     *
     * <p><b>字节序铁证</b>（用 MapColor 常量反推，常识验证）：</p>
     * <ul>
     *   <li>{@code WATER = 4210943 = 0x004040FF} → 高字节 0x40=R、低字节 0xFF=B，
     *       即 RGB(64,64,255) 蓝——<b>符合水体蓝色</b>；</li>
     *   <li>{@code SAND = 16247203 = 0x00F7E9A3} → RGB(247,233,163) 沙黄——符合；</li>
     *   <li>{@code FIRE = 16711680 = 0x00FF0000} → RGB(255,0,0) 红——符合；</li>
     *   <li>{@code GRASS = 8368696 = 0x007FB238} → RGB(127,178,56) 草绿——符合。</li>
     * </ul>
     * <p>即 {@code col} 的高字节是<b>红</b>（bits16–23）、中字节是绿、低字节是<b>蓝</b>，
     * 是标准 ARGB 字节序（R 在高位）。</p>
     *
     * <p><b>血泪教训</b>：上一轮误读 {@code MapColor#calculateRGBColor} 的
     * {@code return 0xFF000000 | l<<16 | k<<8 | j} 以为 {@code col} 是 ABGR，把解析
     * 反了过来，导致<b>红蓝互换</b>（水体→红、火/沙→蓝），正是截图里"大片正红地形、
     * 颜色怪异"的根源。本轮已按常识铁证改回正确 ARGB 解析。</p>
     *
     * @param argb {@code MapColor.col} 的 ARGB 颜色值
     * @return 不透明标准 ARGB int
     */
    private static int desaturate(int argb) {
        int r = (argb >>> 16) & 0xFF;   // ARGB 高字节 = 红
        int g = (argb >>> 8) & 0xFF;    // ARGB 中字节 = 绿
        int b = argb & 0xFF;            // ARGB 低字节 = 蓝
        // 亮度加权（Rec.709 luma）：人眼对绿色最敏感，与去饱和的视觉结果更一致。
        float luma = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float nr = (r * (1f - DESATURATION) + luma * DESATURATION) * BRIGHTNESS;
        float ng = (g * (1f - DESATURATION) + luma * DESATURATION) * BRIGHTNESS;
        float nb = (b * (1f - DESATURATION) + luma * DESATURATION) * BRIGHTNESS;
        int ir = Math.min(255, Math.max(0, (int) (nr + 0.5f)));
        int ig = Math.min(255, Math.max(0, (int) (ng + 0.5f)));
        int ib = Math.min(255, Math.max(0, (int) (nb + 0.5f)));
        return 0xFF000000 | (ir << 16) | (ig << 8) | ib;
    }

    private static MapColor resolveDefaultMapColor(BlockState state) {
        Field f = resolveField();
        if (f != null) {
            try {
                Object v = f.get(state);
                if (v instanceof MapColor mc) {
                    return mc;
                }
            } catch (Throwable ignored) {
            }
        }
        // 兜底路径：对不依赖 context 的方块，null BlockGetter 也能返回默认色
        try {
            return state.getMapColor(null, net.minecraft.core.BlockPos.ZERO);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field resolveField() {
        if (fieldResolved) {
            return MAP_COLOR_FIELD;
        }
        synchronized (BlockStateColorExtractor.class) {
            if (fieldResolved) {
                return MAP_COLOR_FIELD;
            }
            try {
                Class<?> base = Class.forName(
                        "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase");
                for (Field f : base.getDeclaredFields()) {
                    if (f.getType() == MapColor.class) {
                        f.setAccessible(true);
                        MAP_COLOR_FIELD = f;
                        break;
                    }
                }
            } catch (Throwable ignored) {
                MAP_COLOR_FIELD = null;
            }
            fieldResolved = true;
            return MAP_COLOR_FIELD;
        }
    }
}
