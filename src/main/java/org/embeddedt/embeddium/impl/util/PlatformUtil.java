package org.embeddedt.embeddium.impl.util;

import java.util.Map;

/** 26.2 双平台版：mod 名查询不再走 NeoForge 的 ModList（Fabric 侧无此类）。 */
public final class PlatformUtil {

    private static final Map<String, String> KNOWN_NAMES = Map.of(
            "ribbon", "Ribbon",
            "sodium", "Sodium",
            "minecraft", "Minecraft",
            "iris", "Iris"
    );

    private PlatformUtil() {
    }

    public static String getModName(String modId) {
        return KNOWN_NAMES.getOrDefault(modId, modId);
    }
}
