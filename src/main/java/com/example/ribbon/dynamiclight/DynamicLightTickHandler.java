package com.example.ribbon.dynamiclight;


public final class DynamicLightTickHandler {

    private DynamicLightTickHandler() {}

    public static void onClientTick() {
        DynamicLightManager.onClientTick();
    }
}