package com.example.ribbon.shadercache;

import com.example.ribbon.Config;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

public class ShaderSourceCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/ShaderSourceCache");
    private static File cacheDir;

    public static void init() {
        cacheDir = new File(Minecraft.getInstance().gameDirectory, "ribbon_shader_src_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    // 用原始路径作为Key的一部分，加上修改时间
    public static String getCacheKey(String name, String source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            // 算上源码的内容，确保只有内容没变才用缓存
            byte[] hash = md.digest((name + ":" + source.length() + ":" + source.hashCode()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf((name + source).hashCode());
        }
    }

    public static String getCachedSource(String key) {
        if (!Config.isShaderCacheEnabled()) return null;
        File file = new File(cacheDir, key + ".glsl");
        if (file.exists()) {
            try {
                LOGGER.info("[Ribbon] 命中 Shader 源码缓存: {}", key);
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static void saveSource(String key, String source) {
        if (!Config.isShaderCacheEnabled() || source == null) return;
        File file = new File(cacheDir, key + ".glsl");
        try {
            Files.write(file.toPath(), source.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}