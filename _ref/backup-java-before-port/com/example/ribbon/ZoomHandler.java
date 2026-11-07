package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class ZoomHandler {

    private static boolean zooming = false;
    private static float currentZoom = 1.0f;

    // 1. 事件改为 ClientTickEvent.Post
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            zooming = false;
            currentZoom = 1.0f;
            return;
        }
        zooming = RibbonUIHandler.ZOOM_KEY.isDown();
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!Config.ZOOM_ENABLED.get()) return;
        if (event.getPlayer() != Minecraft.getInstance().player) return;

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

        if (currentZoom < 0.999f) {
            event.setNewFovModifier(event.getFovModifier() * currentZoom);
        }
    }
}