package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class ZoomHandler {

    private static boolean zooming = false;
    private static float currentZoom = 1.0f;

    // 1. 事件改为 ClientTickEvent.Post
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            zooming = false;
            currentZoom = 1.0f;
            return;
        }
        zooming = RibbonKeys.ZOOM_KEY.isDown();
    }

    /**
     * 平台无关的缩放实现：对传入的 FOV 应用缩放并推进平滑插值。
     *
     * <p>NeoForge 侧由 {@link #onComputeFov}(ComputeFovModifierEvent) 调用；
     * Fabric 侧由 CameraMixin 注入到 {@code Camera#modifyFovBasedOnDeathOrFluid} 的
     * TAIL 调用 —— 该注入点与 NeoForge 的 {@code ClientHooks.getFieldOfView} hook
     * 完全同一位置（世界 FOV 与 HUD FOV 两条路径都生效），行为一致。</p>
     *
     * @param fov          原始 FOV（NeoForge 侧为 fovModifier，乘法上等价）
     * @param cameraEntity 当前相机实体（对应 NeoForge 事件的 getPlayer）
     */
    public static float adjustFov(float fov, Entity cameraEntity) {
        if (!Config.ZOOM_ENABLED.get()) return fov;
        Minecraft mc = Minecraft.getInstance();
        if (cameraEntity != mc.player) return fov;

        float target = zooming
                ? Config.ZOOM_FOV_MULTIPLIER.get().floatValue()
                : 1.0f;

        if (Config.ZOOM_SMOOTH.get()) {
            currentZoom = Mth.lerp(0.25f, currentZoom, target);
            if (Math.abs(currentZoom - target) < 0.001f) {
                currentZoom = target;
            }
        } else {
            currentZoom = target;
        }

        return currentZoom < 0.999f ? fov * currentZoom : fov;
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        float adjusted = adjustFov(event.getFovModifier(), event.getPlayer());
        if (adjusted != event.getFovModifier()) {
            event.setNewFovModifier(adjusted);
        }
    }
}
