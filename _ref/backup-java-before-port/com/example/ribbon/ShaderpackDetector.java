package com.example.ribbon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ShaderpackDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/ShaderDetector");

    /** 严格匹配 JSON 里的 "api": "...vulkan..." 或 "api": "vulkan" */
    private static final Pattern VULKAN_API = Pattern.compile(
            "\"api\"\\s*:\\s*\"[^\"]*vulkan[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private ShaderpackDetector() {}

    public static ShaderpackFormat detectFormat(Path shaderpackPath) {
        if (shaderpackPath == null) return ShaderpackFormat.UNKNOWN;

        // zip 文件：不解压，直接按扩展名和文件名判定
        String fileName = shaderpackPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".zip")) {
            // Iris 光影包 zip：99% 是 Iris/OptiFine 格式
            // 只有明确是 canvas/vulkan 命名的才拦截
            if (fileName.contains("canvas")) return ShaderpackFormat.CANVAS;
            if (fileName.contains("vulkan")) return ShaderpackFormat.VULKAN;
            return ShaderpackFormat.IRIS_OPTIFINE;
        }

        if (!Files.isDirectory(shaderpackPath)) {
            return ShaderpackFormat.UNKNOWN;
        }

        try {
            // 1. Iris/OptiFine 兼容格式（最优先）
            if (Files.exists(shaderpackPath.resolve("shaders.properties"))) {
                return ShaderpackFormat.IRIS_OPTIFINE;
            }

            // 2. Canvas 格式
            if (Files.exists(shaderpackPath.resolve("canvas.properties"))) {
                LOGGER.info("[Ribbon] 检测到 Canvas 格式光影: {}", shaderpackPath.getFileName());
                return ShaderpackFormat.CANVAS;
            }

            // 3. Vulkan 格式（严格判定）
            if (hasVulkanManifest(shaderpackPath)) {
                LOGGER.info("[Ribbon] 检测到 Vulkan 格式光影: {}", shaderpackPath.getFileName());
                return ShaderpackFormat.VULKAN;
            }

            // 4. 有 .spv 文件也判定为 Vulkan
            if (containsSpvFiles(shaderpackPath)) {
                LOGGER.info("[Ribbon] 检测到 .spv 文件，判定为 Vulkan: {}", shaderpackPath.getFileName());
                return ShaderpackFormat.VULKAN;
            }

            return ShaderpackFormat.UNKNOWN;
        } catch (Exception e) {
            LOGGER.warn("[Ribbon] 检测光影格式时出错: {}", shaderpackPath, e);
            return ShaderpackFormat.UNKNOWN;
        }
    }

    private static boolean containsSpvFiles(Path dir) {
        try (Stream<Path> stream = Files.walk(dir, 3)) {
            return stream.anyMatch(p -> p.toString().endsWith(".spv"));
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean hasVulkanManifest(Path dir) {
        Path manifest = dir.resolve("manifest.json");
        if (!Files.exists(manifest)) return false;
        try {
            String content = Files.readString(manifest);
            // 只有 JSON 顶层出现 "api": "...vulkan..." 才算，不再用 contains
            return VULKAN_API.matcher(content).find();
        } catch (IOException e) {
            return false;
        }
    }
}