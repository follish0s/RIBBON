/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * 帧时间驱动的自适应负载调度器（对标 OptiCores 的 Dynamic Load Throttling）。
 *
 * <p>思路：不用固定值硬砍画质，而是用 {@link Minecraft#getFrameTimeNs()} 采样上一帧
 * 的真实耗时，做指数平滑后换算成 0..1 的「压力值」。掉帧时临时收紧后台批处理预算
 * （区块重建任务数、遮挡检测频率），帧率恢复后自动放开。目标是削掉「区块大面积
 * 更新 / 实体密集」时的帧时间尖峰，而不是长期降质。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>预算缩放有硬下限（默认最低 40%），绝不把重建任务饿死导致世界一直不加载。</li>
 *   <li>只在进入世界后采样，暂停、加载、切换维度时的异常帧时间直接丢弃。</li>
 *   <li>开关关闭时所有 {@code get*} 返回中性值，行为与原版完全一致。</li>
 * </ul>
 */
public final class AdaptivePerformanceManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** EMA 平滑系数：越大越灵敏。0.12 ≈ 8 tick 内响应一次持续掉帧。 */
    private static final double EMA_ALPHA = 0.12;
    /** 超过该毫秒数的帧视为暂停 / 卡顿尖峰，不参与统计。 */
    private static final double MAX_SAMPLE_MS = 250.0;
    /** 预算缩放硬下限，避免重建任务被完全饿死。 */
    private static final double MIN_BUDGET_SCALE = 0.40;
    /** 触发「高压」判定的阈值，用于跳过非必要扫描。 */
    private static final double HIGH_PRESSURE = 0.66;
    /** 进入世界后的预热 tick 数，避免加载期尖峰污染 EMA。 */
    private static final int WARMUP_TICKS = 20;

    private static volatile double smoothedFrameMs = 16.7;
    private static volatile double pressure = 0.0;
    private static volatile int warmupTicks = WARMUP_TICKS;
    private static volatile boolean lastEnabledState = false;

    private AdaptivePerformanceManager() {
    }

    public static void onClientTick() {
        boolean enabled = RuntimeConfig.adaptivePerformanceEnabled;
        if (enabled != lastEnabledState) {
            lastEnabledState = enabled;
            if (!enabled) {
                pressure = 0.0;
                smoothedFrameMs = 1000.0 / Math.max(30, targetFps());
            }
        }
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            warmupTicks = WARMUP_TICKS;
            pressure = 0.0;
            return;
        }
        if (warmupTicks > 0) {
            warmupTicks--;
            return;
        }

        double sampleMs = mc.getFrameTimeNs() / 1_000_000.0;
        if (sampleMs <= 0.0 || sampleMs > MAX_SAMPLE_MS) return;

        double ema = smoothedFrameMs + EMA_ALPHA * (sampleMs - smoothedFrameMs);
        smoothedFrameMs = ema;

        // 目标帧时间 16.7ms(60fps)。达到 2 倍目标帧时间时视为满压力。
        double targetMs = 1000.0 / Math.max(30, targetFps());
        double ratio = (ema - targetMs) / targetMs;
        double next = Math.max(0.0, Math.min(1.0, ratio));
        // 小幅回落滞后，避免预算在阈值附近来回抖动
        if (next < pressure) {
            next = pressure + (next - pressure) * 0.25;
        }
        pressure = next;
    }

    private static int targetFps() {
        return RuntimeConfig.adaptiveTargetFps;
    }

    /**
     * 区块重建任务预算的缩放系数。关闭时恒为 1.0。
     * 压力最大时降到 {@link #MIN_BUDGET_SCALE}，仍保留足够的重建吞吐。
     */
    public static double getChunkBudgetScale() {
        if (!RuntimeConfig.adaptivePerformanceEnabled) return 1.0;
        double p = pressure;
        if (p <= 0.0) return 1.0;
        return Math.max(MIN_BUDGET_SCALE, 1.0 - p * 0.60);
    }

    /** 当前是否处于高压状态（用于跳过整帧的非必要扫描）。 */
    public static boolean isUnderHighPressure() {
        return RuntimeConfig.adaptivePerformanceEnabled && pressure >= HIGH_PRESSURE;
    }

    /** 供 HUD / 调试展示的当前压力值（0..1）。 */
    public static double getPressure() {
        return pressure;
    }

    /** 供 HUD / 调试展示的平滑帧时间（毫秒）。 */
    public static double getSmoothedFrameMs() {
        return smoothedFrameMs;
    }

    /** 维度切换 / 世界重载时调用，重置统计避免旧数据污染。 */
    public static void reset() {
        warmupTicks = WARMUP_TICKS;
        pressure = 0.0;
        smoothedFrameMs = 1000.0 / Math.max(30, targetFps());
        LOGGER.debug("[Ribbon/Adaptive] statistics reset");
    }
}
