/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

/**
 * HUD 文本刷新节流。
 *
 * <p>HUD 每帧都要构造字符串、查生物群系、遍历装备、测字体宽度——这些值在
 * 100ms 内几乎不会变化，但每帧都会产生一批短命对象。各 HUD 只在
 * {@link #shouldRefresh(long, long)} 返回 true 时重建文本，绘制仍每帧进行，
 * 视觉无差别、分配量大幅下降。</p>
 *
 * <p>堆内存高压时刷新间隔自动加倍，进一步减少 GC 压力。</p>
 */
public final class HudRefreshThrottle {

    private HudRefreshThrottle() {
    }

    /** 缓存关闭时返回 0，调用方每次都重建（等价于原版行为）。 */
    public static long intervalMs() {
        if (!RuntimeConfig.hudTextCacheEnabled) return 0L;
        long base = RuntimeConfig.hudRefreshIntervalMs;
        return MemoryPressureManager.isHigh() ? base * 2L : base;
    }

    public static boolean shouldRefresh(long lastBuildMs, long nowMs) {
        long interval = intervalMs();
        return interval <= 0L || lastBuildMs == 0L
                || nowMs - lastBuildMs >= interval || nowMs < lastBuildMs;
    }
}
