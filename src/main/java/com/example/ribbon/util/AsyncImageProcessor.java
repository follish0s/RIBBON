/*
 * Copyright (c) 2024 Ribbon Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.example.ribbon.util;

import com.example.ribbon.Config;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public class AsyncImageProcessor {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 异步解析图像，提取为纯 CPU 的 int[] 数组。
     * ⚠️ 绝对红线：严禁跨线程传递 NativeImage。
     */
    public static CompletableFuture<ProcessedImageData> processAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            try (InputStream is = inputStream) {
                // 注意：NativeImage.read 在 LWJGL 中底层是 memAlloc + stbi_load，在多线程通常安全
                NativeImage image = NativeImage.read(is);
                int width = image.getWidth();
                int height = image.getHeight();

                // 直接导出像素到安全的 int 数组
                int[] pixels = new int[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        pixels[y * width + x] = image.getPixelRGBA(x, y);
                    }
                }

                // 安全释放后台线程创建的 NativeImage
                image.close();

                if (Config.isTextureDebugLogEnabled()) {
                    long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                    LOGGER.debug("[Ribbon] 异步图像提取完成: {}x{}, 耗时: {}ms", width, height, elapsedMs);
                }

                return new ProcessedImageData(width, height, pixels);
            } catch (Exception e) {
                LOGGER.error("[Ribbon] 异步图像解析失败", e);
                return null;
            }
        }, TextureThreadPool.get());
    }

    public static class ProcessedImageData {
        public final int width;
        public final int height;
        public final int[] pixels; // ARGB 格式

        public ProcessedImageData(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }
}