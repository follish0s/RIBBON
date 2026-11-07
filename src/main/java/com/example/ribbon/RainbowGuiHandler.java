package com.example.ribbon;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class RainbowGuiHandler {

    // 1. 使用 RegisterGuiLayersEvent 注册自定义准星图层
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath("ribbon", "rainbow_crosshair"),
                (guiGraphics, deltaTracker) -> renderCrosshair(guiGraphics)
        );
    }

    private static void renderCrosshair(GuiGraphics gui) {
        if (!Config.RAINBOW_CROSSHAIR.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;

        int color = getRainbowColor();

        RenderSystem.enableBlend();
        gui.fill(cx - 7, cy - 1, cx - 2, cy, color);
        gui.fill(cx + 2, cy - 1, cx + 7, cy, color);
        gui.fill(cx, cy - 7, cx + 1, cy - 2, color);
        gui.fill(cx, cy + 2, cx + 1, cy + 7, color);
        gui.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
        RenderSystem.disableBlend();
    }

    // 2. 快捷栏高亮仍使用 RenderGuiEvent.Post
    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiEvent.Post event) {
        if (!Config.HOTBAR_HIGHLIGHT.get()) return;

        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        int selected = mc.player.getInventory().selected;
        if (selected < 0 || selected > 8) return;

        int hotbarStartX = screenWidth / 2 - 90;
        int hotbarY = screenHeight - 22;

        int x0 = hotbarStartX + selected * 20;
        int y0 = hotbarY;
        int x1 = x0 + 20;
        int y1 = y0 + 20;

        int color = getRainbowColor();
        int thickness = 1;

        gui.fill(x0, y0, x1, y0 + thickness, color);
        gui.fill(x0, y1 - thickness, x1, y1, color);
        gui.fill(x0, y0, x0 + thickness, y1, color);
        gui.fill(x1 - thickness, y0, x1, y1, color);
    }

    private static int getRainbowColor() {
        float time = (System.currentTimeMillis() % 3000) / 3000.0f;
        int r = (int) ((Mth.sin(time * (float) Math.PI * 2) * 0.5f + 0.5f) * 255);
        int g = (int) ((Mth.sin((time + 1f / 3f) * (float) Math.PI * 2) * 0.5f + 0.5f) * 255);
        int b = (int) ((Mth.sin((time + 2f / 3f) * (float) Math.PI * 2) * 0.5f + 0.5f) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}