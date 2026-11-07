/*
 * Copyright (C) 2026 follish0s
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.example.ribbon.util;

import com.example.ribbon.Config;
import com.example.ribbon.LangUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TextureThreadPool {
    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/TextureLoader");
    private static ExecutorService executor;
    private static int activeThreads = -1;

    /**
     * 幂等初始化：
     * <ul>
     *   <li>「极速材质加载」关闭时直接释放线程池、不创建常驻线程；</li>
     *   <li>线程数未变化时跳过重建（GUI 拖动滑杆 / 资源重载不再反复 shutdown+新建）；</li>
     *   <li>synchronized 保证 mod 构造、GUI、资源重载三处调用不会并发建池。</li>
     * </ul>
     */
    public static synchronized void init() {
        int threads = Config.isFastTextureLoadingEnabled() ? Config.getTextureLoadingThreads() : 0;
        if (threads <= 0) {
            shutdownLocked();
            return;
        }
        if (executor != null && activeThreads == threads && !executor.isShutdown()) {
            return;
        }

        shutdownLocked();
        executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Ribbon-Texture-Worker");
            t.setDaemon(true);
            return t;
        });
        activeThreads = threads;
        if (Config.isTextureDebugLogEnabled()) {
            LOGGER.info(LangUtil.tr("log.texture.pool_init", threads));
        }
    }

    private static void shutdownLocked() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        activeThreads = -1;
    }

    /** 功能未启用时返回 {@code null}（当前无实际调用者，MixinResourceManager 中的并行加载为预留）。 */
    public static synchronized ExecutorService get() {
        if (executor == null && Config.isFastTextureLoadingEnabled()) {
            init();
        }
        return executor;
    }

    /**
     * 检测 JVM 堆内存压力。如果使用率 > 85%，返回 true。
     * Javadoc Note: Thread-safe. Can be called from any thread.
     */
    public static boolean isMemoryPressureHigh() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long used = rt.totalMemory() - rt.freeMemory();
        return used > (max * 0.85);
    }
}