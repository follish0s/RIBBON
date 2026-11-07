/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

/**
 * 每帧粒子生成预算（防止粒子风暴打穿帧率）。
 *
 * <p>在粒子进入 tick/render 队列之前限流，比渲染期剔除更省。
 * 用单调递增的 {@code System.nanoTime()} 划分「帧窗口」：窗口按固定毫秒数切分，
 * 无需订阅事件、无需跨线程同步（粒子生成只在客户端主线程发生）。</p>
 *
 * <p>关闭开关时 {@link #tryAcquire()} 恒返回 true，零开销。</p>
 */
public final class ParticleSpawnBudget {

    /** 帧窗口长度（纳秒）。16.7ms ≈ 60fps，用 20ms 兼容低帧率。 */
    private static final long WINDOW_NS = 20_000_000L;

    private static long windowStartNs = 0L;
    private static int spawnedInWindow = 0;

    private ParticleSpawnBudget() {
    }

    /**
     * 尝试占用一个粒子生成名额。
     *
     * @return true 允许生成；false 表示该帧预算已耗尽，调用方应丢弃粒子
     */
    public static boolean tryAcquire() {
        if (!RuntimeConfig.particleSpawnBudgetEnabled) return true;

        long now = System.nanoTime();
        if (windowStartNs == 0L || now - windowStartNs >= WINDOW_NS || now < windowStartNs) {
            windowStartNs = now;
            spawnedInWindow = 0;
        }

        int limit = Math.max(1, RuntimeConfig.particleSpawnBudgetPerFrame);
        if (spawnedInWindow >= limit) return false;
        spawnedInWindow++;
        return true;
    }

    /** 供 HUD / 调试展示当前帧已生成的粒子数。 */
    public static int getSpawnedInWindow() {
        return spawnedInWindow;
    }

    /** 世界重载时重置，避免沿用上一世界的计数。 */
    public static void reset() {
        windowStartNs = 0L;
        spawnedInWindow = 0;
    }
}
