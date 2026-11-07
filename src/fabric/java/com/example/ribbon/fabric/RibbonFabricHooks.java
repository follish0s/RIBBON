package com.example.ribbon.fabric;

import com.example.ribbon.compat.GuiGraphics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;

/**
 * Fabric 事件 -> 平台无关 handler（与 NeoForgeBridge 一一对应）。
 *
 * <p>26.2 的 HUD 走抽取式管线，Fabric 侧用 HudElementRegistry.addLast 注册一个统一的
 * HUD 元素，把 Ribbon 的所有 HUD 画在同一层；tick 用 ClientTickEvents / ServerTickEvents。</p>
 */
public final class RibbonFabricHooks {

    private RibbonFabricHooks() {
    }

    public static void register() {
        // 键位（对应 NeoForge 侧的 RegisterKeyMappingsEvent）
        KeyMappingHelper.registerKeyMapping(com.example.ribbon.RibbonKeys.ZOOM_KEY);
        KeyMappingHelper.registerKeyMapping(com.example.ribbon.RibbonKeys.MUSIC_PLAY_PAUSE);
        KeyMappingHelper.registerKeyMapping(com.example.ribbon.RibbonKeys.MUSIC_NEXT);
        KeyMappingHelper.registerKeyMapping(com.example.ribbon.RibbonKeys.MUSIC_PREV);
        KeyMappingHelper.registerKeyMapping(com.example.ribbon.RibbonKeys.MUSIC_MENU);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("ribbon", "hud"),
                (gfx, deltaTracker) -> {
                    GuiGraphics g = GuiGraphics.wrap(gfx);
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
                });

        // 彩虹准星：挂在原版准星图层之后（对应 NeoForge 的
        // RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CROSSHAIR, ...)）
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.fromNamespaceAndPath("ribbon", "rainbow_crosshair"),
                (gfx, deltaTracker) ->
                        com.example.ribbon.RainbowGuiHandler.renderCrosshair(GuiGraphics.wrap(gfx)));

        // 伤害数字：对应 NeoForge 的 LivingDamageEvent.Post（两者都在服务端逻辑触发，
        // 单人模式下集成服务端与客户端同 JVM，静态状态共享，行为边界一致）
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) ->
                com.example.ribbon.DamageNumberHandler.onDamage(entity, source, damageTaken));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
            if (client.player != null) {
                com.example.ribbon.BiomeTitleHandler.onPlayerTick();
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            com.example.ribbon.FpsTpsOverlay.onServerTick();
        });
    }
}
