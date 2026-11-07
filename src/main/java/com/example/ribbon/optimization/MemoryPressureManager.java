/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;


/**
 * 堆内存压力自适应。
 *
 * <p>已有的 {@link AdaptivePerformanceManager} 只看帧时间（CPU/GPU 维度），
 * 本类补上内存维度：每 2 秒采样一次堆占用，超过阈值后输出 0..1 的压力系数
 * （EMA 平滑，避免 GC 前后数值跳动），供粒子预算、剔除距离、HUD 刷新间隔
 * 共同收紧；堆回落后自动放开。纯客户端启发式，不改变任何游戏逻辑。</p>
 */
public final class MemoryPressureManager {

    private static final long SAMPLE_INTERVAL_MS = 2_000L;

    private static volatile double pressure;
    private static long lastSampleMs;

    private MemoryPressureManager() {
    }

    public static void onClientTick() {
        if (FocusState.shouldPauseBackgroundWork()) return;
        if (!RuntimeConfig.memoryPressureAdaptive) {
            pressure = 0.0;
            return;
        }
        long now = System.currentTimeMillis();
        if (lastSampleMs != 0L && now - lastSampleMs < SAMPLE_INTERVAL_MS) {
            return;
        }
        lastSampleMs = now;

        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        if (max <= 0L) {
            pressure = 0.0;
            return;
        }
        double usedRatio = (runtime.totalMemory() - runtime.freeMemory()) / (double) max;
        double threshold = RuntimeConfig.memoryPressureThreshold / 100.0;
        double target = usedRatio > threshold
                ? Math.min(1.0, (usedRatio - threshold) / Math.max(0.05, 1.0 - threshold))
                : 0.0;
        double next = pressure * 0.6 + target * 0.4;
        pressure = next < 0.02 ? 0.0 : next;
    }

    public static boolean isHigh() {
        return RuntimeConfig.memoryPressureAdaptive && pressure > 0.2;
    }

    public static double pressure() {
        return RuntimeConfig.memoryPressureAdaptive ? pressure : 0.0;
    }

    /** 粒子/任务预算系数：1.0 → 0.5（最多收紧一半）。 */
    public static double budgetScale() {
        return 1.0 - pressure() * 0.5;
    }

    /** 剔除距离平方系数：1.0 → 0.7（最多收缩约 16% 距离）。 */
    public static double rangeScale() {
        return 1.0 - pressure() * 0.3;
    }

    public static void reset() {
        pressure = 0.0;
        lastSampleMs = 0L;
    }
}
