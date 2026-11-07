/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

import net.minecraft.world.phys.Vec3;

/**
 * 剔除子系统的共用调度工具：相机静止降频、缓存定期清扫、高压时限流。
 *
 * <p>解决的问题：</p>
 * <ul>
 *   <li>相机原地不动时，每 tick 都重扫全部实体 / 17×17 区块做遮挡检测纯属浪费
 *       —— 静止时把扫描频率降到 1/6，移动时立即恢复每 tick。</li>
 *   <li>{@code ConcurrentHashMap} 缓存只增不减（实体被卸载时没有可靠回调），
 *       长时间游玩会持续占用堆内存 —— 按 tick 定期清扫过期条目并设硬上限。</li>
 *   <li>帧时间高压时缩小每 tick 的 raycast 批量，把主线程时间还给渲染。</li>
 * </ul>
 *
 * <p><b>幂等门控</b>：实体剔除与方块实体剔除两个 tick handler 会在同一个游戏 tick
 * 内各调用一次 {@link #shouldScan}。这里用 {@code level.getGameTime()} 去重，保证
 * 同一 tick 只推进一次计数，且两个 handler 得到完全一致的判定结果。纯主线程调用，
 * 无需同步。</p>
 */
public final class CullingOptimizer {

    /** 静止时每 N tick 扫一次。 */
    private static final int STATIONARY_SCAN_INTERVAL = 6;
    /** 判定「静止」的位移阈值（格的平方）。 */
    private static final double STATIONARY_EPSILON_SQ = 0.25 * 0.25;
    /** 缓存清扫间隔（tick），60 tick = 3 秒。 */
    private static final int SWEEP_INTERVAL_TICKS = 60;
    /** 高压时 raycast 批量缩放。 */
    private static final double HIGH_PRESSURE_BATCH_SCALE = 0.5;

    private static long lastGameTime = Long.MIN_VALUE;
    private static Vec3 lastCameraPos;
    private static int stationaryTicks = 0;
    private static boolean scanDecision = true;
    private static boolean sweepDecision = false;

    private CullingOptimizer() {
    }

    /**
     * 是否需要在这个游戏 tick 执行一次剔除扫描。
     *
     * @param cameraPos 当前相机位置（Vec3 不可变，可安全持有）
     * @param gameTime  {@code level.getGameTime()}，用于跨 handler 幂等去重
     */
    public static boolean shouldScan(Vec3 cameraPos, long gameTime) {
        if (gameTime != lastGameTime) {
            lastGameTime = gameTime;
            sweepDecision = gameTime % SWEEP_INTERVAL_TICKS == 0;

            if (!RuntimeConfig.smartCullingEnabled) {
                lastCameraPos = null;
                stationaryTicks = 0;
                scanDecision = true;
            } else {
                boolean stationary = cameraPos != null && lastCameraPos != null
                        && lastCameraPos.distanceToSqr(cameraPos) < STATIONARY_EPSILON_SQ;
                lastCameraPos = cameraPos;
                if (!stationary) {
                    stationaryTicks = 0;
                    scanDecision = true;
                } else {
                    stationaryTicks++;
                    scanDecision = stationaryTicks % STATIONARY_SCAN_INTERVAL == 0;
                }
            }
        }
        return scanDecision;
    }

    /** 是否应该在这个游戏 tick 清扫一次过期缓存条目。 */
    public static boolean shouldSweep() {
        return sweepDecision;
    }

    /**
     * 按当前压力换算每 tick 的 raycast 批量，下限为 1。
     *
     * @param baseBatchSize 原始批量
     */
    public static int scaledBatchSize(int baseBatchSize) {
        int batch = baseBatchSize;
        if (AdaptivePerformanceManager.isUnderHighPressure()) {
            batch = (int) Math.ceil(batch * HIGH_PRESSURE_BATCH_SCALE);
        }
        return Math.max(1, batch);
    }

    /** 缓存条目数上限（防止极端情况下无界增长）。 */
    public static int maxCacheEntries() {
        return Math.max(256, RuntimeConfig.cullingCacheMaxEntries);
    }

    /** 世界重载 / 维度切换时重置，避免沿用上一个世界的相机坐标与 tick 计数。 */
    public static void reset() {
        lastGameTime = Long.MIN_VALUE;
        lastCameraPos = null;
        stationaryTicks = 0;
        scanDecision = true;
        sweepDecision = false;
    }
}
