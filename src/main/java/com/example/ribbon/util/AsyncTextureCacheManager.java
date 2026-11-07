/*
 * Copyright (c) 2024 Ribbon Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.example.ribbon.util;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public class AsyncTextureCacheManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MAX_CACHE_SIZE = 512L * 1024 * 1024; // 512MB
    private static final Map<String, File> CACHE_MAP = new LinkedHashMap<>(16, 0.75f, true);

    private static File cacheDir;

    public static void init() {
        cacheDir = new File(Minecraft.getInstance().gameDirectory, "ribbon_cache/textures");
        if (!cacheDir.exists() && cacheDir.mkdirs()) {
            LOGGER.info("[Ribbon] Created texture cache directory at {}", cacheDir.getAbsolutePath());
        }
    }

    public static String generateHash(String path, long lastModified) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = path + ":" + lastModified;
            byte[] hash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf((path + lastModified).hashCode());
        }
    }

    public static byte[] get(String hash) {
        File file = new File(cacheDir, hash + ".cache");
        if (file.exists()) {
            try {
                return Files.readAllBytes(file.toPath());
            } catch (IOException e) {
                LOGGER.error("[Ribbon] Failed to read cache: {}", hash, e);
            }
        }
        return null;
    }

    public static synchronized void put(String hash, byte[] data) {
        File file = new File(cacheDir, hash + ".cache");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
            CACHE_MAP.put(hash, file);
            enforceSizeLimit();
        } catch (IOException e) {
            LOGGER.error("[Ribbon] Failed to write cache: {}", hash, e);
        }
    }

    private static void enforceSizeLimit() {
        long totalSize = 0;
        File[] files = cacheDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            totalSize += f.length();
        }

        if (totalSize > MAX_CACHE_SIZE) {
            for (Map.Entry<String, File> entry : CACHE_MAP.entrySet()) {
                File f = entry.getValue();
                if (f.delete()) {
                    totalSize -= f.length();
                }
                if (totalSize <= MAX_CACHE_SIZE) break;
            }
        }
    }
}