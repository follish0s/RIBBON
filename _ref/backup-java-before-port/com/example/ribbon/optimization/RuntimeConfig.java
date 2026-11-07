/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

import com.example.ribbon.Config;

/**
 * 高频热路径配置值的 volatile 快照。
 *
 * <p>渲染热路径（每个实体 / 每个粒子 / 每帧）直接调用 {@code ModConfigSpec.get()}
 * 会引入查表与校验开销。参考 SodiumExtras 的 {@code updateCache()} 做法：把渲染期
 * 读到的值缓存到 volatile 字段，仅在配置加载、重载或 GUI 保存时刷新一次。</p>
 *
 * <p><b>刷新时机（必须保证，否则 GUI 改开关不生效）</b></p>
 * <ol>
 *   <li>类初始化：{@link #refresh()} —— 此时 {@code Config} 的 SPEC 已 build，
 *       各 getter 自带 {@code IllegalStateException} 兜底，安全。</li>
 *   <li>{@code Config.onLoad(ModConfigEvent)} —— 配置文件加载 / 重载。</li>
 *   <li>{@code RibbonOptions.STORAGE.save()} —— GUI 里点选开关后（{@code SPEC.save()}
 *       本身不会触发 ModConfigEvent，必须显式刷新）。</li>
 * </ol>
 *
 * <p>所有字段均为 volatile，跨渲染线程读取无锁、无同步开销。</p>
 */
public final class RuntimeConfig {

    // ===== 实验性功能 =====
    public static volatile boolean experimentalFeaturesEnabled;
    public static volatile boolean lodEnabled;

    // ===== 自适应性能调度 =====
    public static volatile boolean adaptivePerformanceEnabled;
    public static volatile int adaptiveTargetFps;

    // ===== 剔除精简开销 =====
    public static volatile boolean smartCullingEnabled;
    public static volatile int cullingCacheMaxEntries;

    // ===== 剔除热路径（开关 + 距离平方，避免热路径里做乘法 / new Vec3） =====
    public static volatile boolean entityCullingEnabled;
    public static volatile int entityCullingRangeSq;
    public static volatile boolean blockEntityCullingEnabled;
    public static volatile int blockEntityCullingRangeSq;
    public static volatile boolean particleCullingEnabled;
    public static volatile int particleCullingRangeSq;
    public static volatile boolean itemEntityCullingEnabled;
    public static volatile int itemEntityMaxDistanceSq;

    // ===== 实体距离剔除 / 白名单 / 粒子预算 =====
    public static volatile boolean entityDistanceCullingEnabled;
    public static volatile int entityDistanceCullingRangeSq;
    public static volatile boolean entityCullingWhitelistEnabled;
    public static volatile boolean particleSpawnBudgetEnabled;
    public static volatile int particleSpawnBudgetPerFrame;
    public static volatile boolean adaptiveCullingDistanceEnabled;

    // ===== 液态玻璃 GUI 主题 =====
    public static volatile boolean liquidGlassEnabled;
    public static volatile int liquidGlassBlur;

    // ===== 悬浮层增强 / 药水 HUD / 夜视 / 窗口标题 FPS =====
    public static volatile boolean overlayEnhanced;
    public static volatile boolean potionHudEnabled;
    public static volatile boolean nightVisionEnabled;
    public static volatile float fullbrightStrength;
    public static volatile boolean showFpsInTitle;

    // ===== HUD 节流 / 光影状态缓存 / 内存压力 / 物品光缓存 =====
    public static volatile boolean hudTextCacheEnabled;
    public static volatile int hudRefreshIntervalMs;
    public static volatile boolean shaderStateCacheEnabled;
    public static volatile boolean memoryPressureAdaptive;
    public static volatile int memoryPressureThreshold;
    public static volatile boolean itemLightCacheEnabled;

    // ===== 输入法（IME）兼容修复 =====
    public static volatile boolean imeFixEnabled;
    public static volatile boolean imeCompositionGuard;

    // ===== GUI 防抖 / 失焦暂停 =====
    public static volatile boolean guiSearchDebounceEnabled;
    public static volatile boolean pauseWhenUnfocused;

    private RuntimeConfig() {
    }

    static {
        refresh();
    }

    /** 从 Config 重新读取一次全部热路径值。配置未就绪时各 getter 会返回安全默认值。 */
    public static void refresh() {
        // 兜底：配置 SPEC 尚未就绪或某个新键在旧配置文件里缺失时，任何异常都不能
        // 让类初始化失败（ExceptionInInitializerError 会直接崩掉游戏）。
        try {
            experimentalFeaturesEnabled = Config.isExperimentalFeaturesEnabled();
            lodEnabled = Config.isLodEnabled();

            adaptivePerformanceEnabled = Config.isAdaptivePerformanceEnabled();
            adaptiveTargetFps = Config.getAdaptiveTargetFps();
            smartCullingEnabled = Config.isSmartCullingEnabled();
            cullingCacheMaxEntries = Config.getCullingCacheMaxEntries();

            entityCullingEnabled = Config.isEntityCullingEnabled();
            entityCullingRangeSq = square(Config.getEntityCullingRange());
            blockEntityCullingEnabled = Config.isBlockEntityCullingEnabled();
            blockEntityCullingRangeSq = square(Config.getBlockEntityCullingRange());
            particleCullingEnabled = Config.isParticleCullingEnabled();
            particleCullingRangeSq = square(Config.getParticleCullingRange());
            itemEntityCullingEnabled = Config.isItemEntityCullingEnabled();
            itemEntityMaxDistanceSq = square(Config.getItemEntityMaxDistance());

            entityDistanceCullingEnabled = Config.isEntityDistanceCullingEnabled();
            entityDistanceCullingRangeSq = square(Config.getEntityDistanceCullingRange());
            entityCullingWhitelistEnabled = Config.isEntityCullingWhitelistEnabled();
            particleSpawnBudgetEnabled = Config.isParticleSpawnBudgetEnabled();
            particleSpawnBudgetPerFrame = Config.getParticleSpawnBudgetPerFrame();
            adaptiveCullingDistanceEnabled = Config.isAdaptiveCullingDistanceEnabled();

            liquidGlassEnabled = Config.isLiquidGlassEnabled();
            liquidGlassBlur = Config.getLiquidGlassBlur();

            overlayEnhanced = Config.isOverlayEnhanced();
            potionHudEnabled = Config.isPotionHudEnabled();
            nightVisionEnabled = Config.isNightVisionEnabled();
            fullbrightStrength = Config.getNightVisionStrength() / 100.0f;
            showFpsInTitle = Config.isShowFpsInTitle();

            hudTextCacheEnabled = Config.isHudTextCacheEnabled();
            hudRefreshIntervalMs = Config.getHudRefreshIntervalMs();
            shaderStateCacheEnabled = Config.isShaderStateCacheEnabled();
            memoryPressureAdaptive = Config.isMemoryPressureAdaptive();
            memoryPressureThreshold = Config.getMemoryPressureThreshold();
            itemLightCacheEnabled = Config.isItemLightCacheEnabled();

            imeFixEnabled = Config.isImeFixEnabled();
            imeCompositionGuard = Config.isImeCompositionGuardEnabled();

            guiSearchDebounceEnabled = Config.isGuiSearchDebounceEnabled();
            pauseWhenUnfocused = Config.isPauseWhenUnfocused();
        } catch (Throwable ignored) {
            // 保持上一次（或字段默认值）的快照，下一次配置事件会重试
        }
    }

    private static int square(int value) {
        int clamped = Math.max(1, value);
        return clamped * clamped;
    }
}
