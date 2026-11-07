package com.example.ribbon;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public class ShadowRendererDetector {
    private static boolean initialized = false;
    private static Method isRenderingShadowPassMethod = null;

    public static boolean isShadowPassActive() {
        if (!initialized) init();
        if (isRenderingShadowPassMethod == null) return false;
        try {
            return (Boolean) isRenderingShadowPassMethod.invoke(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void init() {
        initialized = true;
        if (!ModList.get().isLoaded("iris")) return;

        String[] apiClassNames = new String[]{
                "net.irisshaders.iris.api.v0.IrisApi",
                "net.coderbot.iris.api.v0.IrisApi"
        };

        Class<?> apiClass = null;
        for (String name : apiClassNames) {
            try { apiClass = Class.forName(name); break; } catch (ClassNotFoundException ignored) {}
        }
        if (apiClass == null) return;

        Object apiInstance = null;
        try {
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            apiInstance = getInstanceMethod.invoke(null);
        } catch (Throwable ignored) {}
        if (apiInstance == null) return;

        try {
            isRenderingShadowPassMethod = apiInstance.getClass().getMethod("isRenderingShadowPass");
        } catch (NoSuchMethodException ignored) {}
    }
}