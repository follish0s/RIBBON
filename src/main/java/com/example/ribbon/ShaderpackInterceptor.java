package com.example.ribbon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class ShaderpackInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/ShaderInterceptor");

    private static volatile String blockedShaderpackName = null;
    private static volatile ShaderpackFormat blockedFormat = null;

    private ShaderpackInterceptor() {}

    public static boolean checkAndBlock(Path shaderpackPath) {
        if (!Config.isShaderCompatibilityWarningEnabled()) return false;

        ShaderpackFormat format = ShaderpackDetector.detectFormat(shaderpackPath);
        if (format.isIncompatible()) {
            String name = shaderpackPath != null ? shaderpackPath.getFileName().toString() : "未知";
            blockedShaderpackName = name;
            blockedFormat = format;
            LOGGER.warn("[Ribbon] 已拦截不兼容光影包: {} ({})", name, format.getDisplayName());
            return true;
        }

        clearBlocked();
        return false;
    }

    public static void markBlocked(String name, ShaderpackFormat format) {
        blockedShaderpackName = name;
        blockedFormat = format;
    }

    public static void clearBlocked() {
        blockedShaderpackName = null;
        blockedFormat = null;
    }

    public static boolean hasBlockedShaderpack() {
        return blockedShaderpackName != null;
    }

    public static String getBlockedShaderpackName() {
        return blockedShaderpackName;
    }

    public static ShaderpackFormat getBlockedFormat() {
        return blockedFormat;
    }
}