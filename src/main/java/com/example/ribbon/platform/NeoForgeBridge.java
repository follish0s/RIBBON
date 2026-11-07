package com.example.ribbon.platform;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * NeoForge 事件 -> 平台无关 handler 的集中桥接。
 *
 * <p>26.2 双平台改造后，所有 handler 只接收平台无关参数（GuiGraphics / 无参），
 * 由本类与 Fabric 侧的 RibbonFabricHooks 各自把平台事件转成同一批调用，
 * 共享代码里不再出现任何平台事件类型。</p>
 */
@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class NeoForgeBridge {

    private NeoForgeBridge() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        com.example.ribbon.compat.GuiGraphics g =
                com.example.ribbon.compat.GuiGraphics.wrap(event.getGuiGraphics());
        com.example.ribbon.BiomeTitleHandler.onRenderGui(g);
        com.example.ribbon.ClickEffectHandler.onRender(g);
        com.example.ribbon.DamageNumberHandler.onRenderGui(g);
        com.example.ribbon.FpsTpsOverlay.onRenderOverlay(g);
        com.example.ribbon.PotionTimerHud.onRenderOverlay(g);
        com.example.ribbon.RainbowGuiHandler.onRenderHotbar(g);
        com.example.ribbon.ShaderpackWarningHandler.onRenderGui(g);
        com.example.ribbon.hud.InfoHudHandler.onRenderGui(g);
        com.example.ribbon.lunar.FestivalBadgeHandler.onRender(g);
        com.example.ribbon.lunar.FestivalCountdownHandler.onRender(g);
        com.example.ribbon.lunar.FestivalTitleHandler.onRender(g);
        com.example.ribbon.lunar.LunarHudHandler.onRender(g);
        com.example.ribbon.music.MusicPlayerHudHandler.onRender(g);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        com.example.ribbon.ShaderpackMonitor.onClientTick();
        com.example.ribbon.WindowTitleFps.onClientTick();
        com.example.ribbon.ZoomHandler.onClientTick();
        com.example.ribbon.dynamiclight.DynamicLightManager.onClientTick();
        com.example.ribbon.dynamiclight.DynamicLightTickHandler.onClientTick();
        com.example.ribbon.entityculling.EntityCullingTickHandler.onClientTick();
        com.example.ribbon.hud.InfoHudHandler.onClientTick();
        com.example.ribbon.lunar.FestivalEffectHandler.onClientTick();
        com.example.ribbon.lunar.FestivalTitleHandler.onClientTick();
        com.example.ribbon.music.MusicPlayerTickHandler.onClientTick();
        com.example.ribbon.optimization.AdaptivePerformanceManager.onClientTick();
        com.example.ribbon.optimization.MemoryPressureManager.onClientTick();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        com.example.ribbon.BiomeTitleHandler.onPlayerTick();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        com.example.ribbon.FpsTpsOverlay.onServerTick();
    }
}
