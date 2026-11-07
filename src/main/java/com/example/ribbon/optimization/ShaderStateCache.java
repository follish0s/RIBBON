/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

import com.example.ribbon.IrisReflection;

/**
 * 光影状态检测缓存。
 *
 * <p>{@link IrisReflection#areShadersEnabled()} 走反射访问 Iris 内部类，
 * 而动态光源每 tick、LOD 渲染每帧都要查一次。光影状态实际只在玩家切换光影包
 * 时变化，因此按 TTL 缓存：光影开关最多滞后 250ms 生效（视觉上无感），
 * 反射调用从每帧/每 tick 降到每 250ms 一次。</p>
 *
 * <p>所有调用都发生在客户端主线程；字段用 volatile 保证跨线程可见性。</p>
 */
public final class ShaderStateCache {

    private static final long PRESENT_TTL_MS = 5_000L;
    private static final long ENABLED_TTL_MS = 250L;

    private static volatile boolean irisPresent;
    private static volatile boolean shadersEnabled;
    private static volatile long lastPresentCheckMs;
    private static volatile long lastEnabledCheckMs;

    private ShaderStateCache() {
    }

    public static boolean isIrisPresent() {
        if (!RuntimeConfig.shaderStateCacheEnabled) {
            return IrisReflection.isIrisPresent();
        }
        long now = System.currentTimeMillis();
        if (lastPresentCheckMs == 0L || now - lastPresentCheckMs >= PRESENT_TTL_MS
                || now < lastPresentCheckMs) {
            irisPresent = IrisReflection.isIrisPresent();
            lastPresentCheckMs = now;
        }
        return irisPresent;
    }

    public static boolean areShadersEnabled() {
        if (!RuntimeConfig.shaderStateCacheEnabled) {
            return IrisReflection.areShadersEnabled();
        }
        if (!isIrisPresent()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (lastEnabledCheckMs == 0L || now - lastEnabledCheckMs >= ENABLED_TTL_MS
                || now < lastEnabledCheckMs) {
            shadersEnabled = IrisReflection.areShadersEnabled();
            lastEnabledCheckMs = now;
        }
        return shadersEnabled;
    }

    /** 光影包切换/资源重载后调用，强制下一次查询走真实检测。 */
    public static void invalidate() {
        lastPresentCheckMs = 0L;
        lastEnabledCheckMs = 0L;
    }
}
