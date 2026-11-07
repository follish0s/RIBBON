package com.example.ribbon;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class DistantHorizonsCompat {
    private static boolean dhLoaded = false;
    private static Object graphicsConfig = null;
    private static Method setOverdrawPreventionMethod = null;
    private static long lastUpdateTime = 0;
    private static final long UPDATE_COOLDOWN = 50;

    public static void init() {
        if (!ModList.get().isLoaded("distanthorizons")) return;
        try {
            Class<?> dhApiClass = Class.forName("com.seibel.distanthorizons.api.DhApi");
            Method getGraphicsConfigMethod = dhApiClass.getMethod("getGraphicsConfig");
            graphicsConfig = getGraphicsConfigMethod.invoke(null);
            if (graphicsConfig != null) {
                setOverdrawPreventionMethod = graphicsConfig.getClass().getMethod("setOverdrawPrevention", double.class);
                dhLoaded = true;
            }
        } catch (Throwable t) {
        }
    }

    public static void updateOverdrawPrevention(double value) {
        if (!dhLoaded || graphicsConfig == null || setOverdrawPreventionMethod == null) return;
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime < UPDATE_COOLDOWN) return;
        lastUpdateTime = now;
        try {
            setOverdrawPreventionMethod.invoke(graphicsConfig, value);
        } catch (Throwable ignored) {}
    }
}