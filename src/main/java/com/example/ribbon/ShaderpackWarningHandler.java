package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = Ribbon.MODID)
public final class ShaderpackWarningHandler {

    private ShaderpackWarningHandler() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!Config.isShaderCompatibilityWarningEnabled()) return;
        if (!ShaderpackInterceptor.hasBlockedShaderpack()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        ShaderpackFormat format = ShaderpackInterceptor.getBlockedFormat();
        String formatName = format != null ? format.getLocalizedName() : LangUtil.tr("shader.format.UNKNOWN");

        String line1 = LangUtil.tr("shader.warning.title");
        String line2 = LangUtil.tr("shader.warning.format", formatName);
        String line3 = LangUtil.tr("shader.warning.hint");

        int lineHeight = 10;
        int padding = 6;

        int maxWidth = Math.max(mc.font.width(line1),
                Math.max(mc.font.width(line2), mc.font.width(line3)));

        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lineHeight * 3 + padding * 2;
        int boxX = (screenWidth - boxWidth) / 2;
        int boxY = screenHeight - 60 - boxHeight;

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xC0000000);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0x60FFFFFF);
        graphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0x30FFFFFF);

        int textX = boxX + padding;
        int textY = boxY + padding;

        graphics.drawString(mc.font, line1, textX, textY, 0xFFFFAA00, true);
        graphics.drawString(mc.font, line2, textX, textY + lineHeight, 0xFFCCCCCC, true);
        graphics.drawString(mc.font, line3, textX, textY + lineHeight * 2, 0xFF888888, true);
    }
}