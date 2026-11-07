package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class UiInteractionHandler {

    private UiInteractionHandler() {}

    @SubscribeEvent
    public static void onScreenMousePre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!Config.isClickEffectCoverUi()) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }

        // 🚨 修复：使用安全方法，防止加载界面点击时崩溃
        Config.ClickEffectType type = Config.getClickEffectType();
        if (type == Config.ClickEffectType.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean onWidget = false;
        for (var c : event.getScreen().children()) {
            if (c instanceof AbstractWidget w && w.isMouseOver(event.getMouseX(), event.getMouseY())) {
                onWidget = true;
                break;
            }
        }

        if (Config.isClickEffectUiOnlyOnWidgets() && !onWidget) return;

        boolean right = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        ClickEffectHandler.trigger(mc, right, type);
    }

    @SubscribeEvent
    public static void onScreenMousePost(ScreenEvent.MouseButtonPressed.Post event) {
        if (!Config.isClickEffectMuteVanilla()) return;
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean onWidget = false;
        for (var c : event.getScreen().children()) {
            if (c instanceof AbstractWidget w && w.isMouseOver(event.getMouseX(), event.getMouseY())) {
                onWidget = true;
                break;
            }
        }
        if (!onWidget) return;

        SoundManager sm = mc.getSoundManager();
        sm.stop(ResourceLocation.withDefaultNamespace("ui.button.click"), SoundSource.MASTER);
    }
}