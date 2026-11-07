package com.example.ribbon.dynamiclight;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class DynamicLightTickHandler {

    private DynamicLightTickHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DynamicLightManager.onClientTick();
    }
}