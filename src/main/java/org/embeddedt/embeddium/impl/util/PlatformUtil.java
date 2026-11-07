package org.embeddedt.embeddium.impl.util;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;

public class PlatformUtil {
    public static boolean isLoadValid() {
        return !LoadingModList.get().hasErrors();
    }

    public static boolean modPresent(String modid) {
        return LoadingModList.get().getModFileById(modid) != null;
    }

    public static String getModName(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getDisplayName()).orElse(modId);
    }

    public static boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
