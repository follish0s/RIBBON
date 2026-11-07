package org.embeddedt.embeddium.impl.util;

import net.neoforged.fml.ModList;

/** 26.2 附加 mod 版：只保留 GUI 需要的能力，渲染器相关的平台探测已移除。 */
public final class PlatformUtil {
    private PlatformUtil() {
    }

    public static String getModName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(modId);
    }
}
