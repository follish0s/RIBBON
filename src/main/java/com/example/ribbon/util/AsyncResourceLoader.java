/*
 * Copyright (c) 2024 Ribbon Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.example.ribbon.util;

import com.example.ribbon.Config;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 极速材质加载 - 异步资源加载器。
 * 负责接管 JSON 模型和语言文件的异步读取与反序列化。
 * 包含 OOM 保护逻辑：内存压力大时自动降级为同步执行。
 */
public class AsyncResourceLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 异步读取并解析 JSON 资源。
     *
     * @param inputStream 资源输入流
     * @param resourcePath 资源路径 (用于日志和缓存 Key)
     * @return 解析后的 JsonElement，如果解析失败返回 null
     */
    public static CompletableFuture<JsonElement> loadJsonAsync(InputStream inputStream, String resourcePath) {
        // OOM 保护：如果堆内存使用率 > 85%，直接降级为同步执行，避免异步线程池堆积任务导致 OOM
        if (TextureThreadPool.isMemoryPressureHigh()) {
            if (Config.isTextureDebugLogEnabled()) {
                LOGGER.warn("[Ribbon] 检测到内存压力过高，降级为同步加载: {}", resourcePath);
            }
            return CompletableFuture.completedFuture(parseJsonSync(inputStream, resourcePath));
        }

        return CompletableFuture.supplyAsync(() -> parseJsonSync(inputStream, resourcePath), TextureThreadPool.get());
    }

    /**
     * 同步解析 JSON (可在当前线程或后台线程调用)。
     */
    private static JsonElement parseJsonSync(InputStream inputStream, String resourcePath) {
        long startTime = System.nanoTime();
        try (InputStream is = inputStream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            JsonElement element = JsonParser.parseReader(reader);

            if (Config.isTextureDebugLogEnabled()) {
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                LOGGER.debug("[Ribbon] JSON 解析完成: {}, 耗时: {}ms", resourcePath, elapsedMs);
            }
            return element;
        } catch (IOException e) {
            LOGGER.error("[Ribbon] 读取 JSON 资源失败: {}", resourcePath, e);
            return null;
        } catch (Exception e) {
            LOGGER.error("[Ribbon] 解析 JSON 资源格式错误: {}", resourcePath, e);
            return null;
        }
    }

    /**
     * 异步读取语言文件 (.lang / .json)。
     * MC 1.21.1 的语言文件通常是 JSON 格式，直接复用 loadJsonAsync 即可。
     * 如果是旧版 .lang 格式，可在此扩展 Properties 解析。
     */
    public static CompletableFuture<JsonElement> loadLanguageAsync(InputStream inputStream, String resourcePath) {
        return loadJsonAsync(inputStream, resourcePath);
    }
}