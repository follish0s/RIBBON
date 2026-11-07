package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

@EventBusSubscriber(value = Dist.CLIENT, modid = Ribbon.MODID)
public final class ShaderpackMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/ShaderpackMonitor");

    private static int tickCounter = 0;
    private static boolean lastShadersEnabled = false;
    private static String lastShaderpackName = null;

    private ShaderpackMonitor() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.isShaderCompatibilityWarningEnabled()) {
            ShaderpackInterceptor.clearBlocked();
            lastShadersEnabled = false;
            lastShaderpackName = null;
            return;
        }

        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            lastShadersEnabled = false;
            lastShaderpackName = null;
            return;
        }

        if (!IrisReflection.isIrisPresent()) {
            ShaderpackInterceptor.clearBlocked();
            lastShadersEnabled = false;
            lastShaderpackName = null;
            return;
        }

        boolean currentEnabled = IrisReflection.areShadersEnabled();

        // ===== 关键修复：光影未启用，不拦截、不标记 =====
        if (!currentEnabled) {
            // 仅当"上一次启用过，现在被 Iris 禁用"时标记版本不匹配
            if (lastShadersEnabled && lastShaderpackName != null
                    && !lastShaderpackName.equals(ShaderpackInterceptor.getBlockedShaderpackName())) {
                LOGGER.info("[Ribbon] Iris 自行禁用了光影 {}，标记为版本不匹配", lastShaderpackName);
                ShaderpackInterceptor.markBlocked(lastShaderpackName, ShaderpackFormat.VERSION_MISMATCH);
            } else if (lastShaderpackName == null) {
                // 从未启用过光影 → 什么都不做
                ShaderpackInterceptor.clearBlocked();
            }
            // 无光影时不清除之前已标记的版本不匹配（保留提示给用户看）
            lastShadersEnabled = false;
            return;
        }

        // ===== 光影已启用 =====
        Path shaderpackPath = IrisReflection.getCurrentShaderpackPath();
        if (shaderpackPath == null) {
            ShaderpackInterceptor.clearBlocked();
            lastShadersEnabled = true;
            lastShaderpackName = null;
            return;
        }

        String currentName = shaderpackPath.getFileName().toString();

        if (!currentName.equals(lastShaderpackName)) {
            ShaderpackInterceptor.clearBlocked();
        }

        ShaderpackFormat format = ShaderpackDetector.detectFormat(shaderpackPath);

        if (format.isIncompatible()) {
            if (IrisReflection.disableShaders()) {
                ShaderpackInterceptor.markBlocked(currentName, format);
                LOGGER.warn("[Ribbon] 已拦截不兼容光影: {} ({})", currentName, format.getDisplayName());
            }
        } else {
            // 兼容光影 → 清除任何旧标记
            ShaderpackInterceptor.clearBlocked();
        }

        lastShadersEnabled = true;
        lastShaderpackName = currentName;
    }
}