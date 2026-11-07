package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class RainbowHealthHandler {

    private static final ResourceLocation HEART_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/icons.png");

    // icons.png 中血条的 UV 坐标（1.21 vanilla）
    private static final int U_CONTAINER = 16;  // 空心底
    private static final int U_FULL      = 52;  // 满心
    private static final int U_HALF      = 61;  // 半心（左半）

    private static float lastHealth = 20.0f;
    private static long lastDamageTime = 0;
    private static final long FLASH_DURATION = 300;

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.PLAYER_HEALTH,
                ResourceLocation.fromNamespaceAndPath("ribbon", "rainbow_health"),
                (guiGraphics, deltaTracker) -> renderHealth(guiGraphics)
        );
    }

    private static void renderHealth(GuiGraphics gui) {
        if (!Config.RAINBOW_HEALTH.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        float health = mc.player.getHealth();
        float maxHealth = mc.player.getMaxHealth();
        float absorption = mc.player.getAbsorptionAmount();

        if (health < lastHealth) {
            lastDamageTime = System.currentTimeMillis();
        }
        lastHealth = health;

        int totalHearts = Mth.ceil(maxHealth / 2.0f);
        if (totalHearts <= 0) return;

        int baseX = screenWidth / 2 - 91;
        int baseY = screenHeight - 39;

        int shakeX = 0, shakeY = 0;
        if (Config.HEALTH_LOW_SHAKE.get() && maxHealth > 0) {
            float healthPercent = health / maxHealth * 100;
            if (healthPercent <= Config.HEALTH_LOW_SHAKE_THRESHOLD.get()) {
                float intensity = (Config.HEALTH_LOW_SHAKE_THRESHOLD.get() - healthPercent) / 100f;
                intensity = Mth.clamp(intensity, 0.0f, 1.0f);
                long time = System.currentTimeMillis();
                shakeX = (int) (Math.sin(time * 0.05) * 3 * intensity);
                shakeY = (int) (Math.cos(time * 0.07) * 3 * intensity);
            }
        }

        int startX = baseX + shakeX;
        int startY = baseY + shakeY;

        boolean flashing = (System.currentTimeMillis() - lastDamageTime) < FLASH_DURATION;
        float flashAmount = flashing
                ? (1.0f - (System.currentTimeMillis() - lastDamageTime) / (float) FLASH_DURATION)
                : 0.0f;

        long now = System.currentTimeMillis();

        // ===== 普通心 =====
        for (int i = 0; i < totalHearts; i++) {
            int col = i % 10;
            int row = i / 10;
            int x = startX + col * 8;
            int y = startY - row * 10;

            float heartHealth = Mth.clamp(health - i * 2.0f, 0.0f, 2.0f);
            float ratio = heartHealth / 2.0f;

            // 1) 先画空心底（白色）
            gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            gui.blit(HEART_TEXTURE, x, y, U_CONTAINER, 0, 9, 9, 256, 256);

            // 2) 再叠加彩色的实心前景
            if (ratio > 0.0f) {
                int color = getHeartColor(i, now);

                if (flashAmount > 0) {
                    color = blendColor(color, 0xFFFFFF, flashAmount);
                }

                float r = ((color >> 16) & 0xFF) / 255.0f;
                float g = ((color >> 8) & 0xFF) / 255.0f;
                float b = (color & 0xFF) / 255.0f;
                gui.setColor(r, g, b, 1.0f);

                if (ratio >= 1.0f) {
                    // 满心
                    gui.blit(HEART_TEXTURE, x, y, U_FULL, 0, 9, 9, 256, 256);
                } else {
                    // 半心
                    gui.blit(HEART_TEXTURE, x, y, U_HALF, 0, 9, 9, 256, 256);
                }
            }
        }

        // ===== 吸收心 =====
        if (absorption > 0.0f) {
            int absHearts = Mth.ceil(absorption / 2.0f);
            for (int i = 0; i < absHearts; i++) {
                int col = totalHearts + i;
                int row = col / 10;
                int x = startX + (col % 10) * 8;
                int y = startY - row * 10;

                // 空心底
                gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.blit(HEART_TEXTURE, x, y, U_CONTAINER, 0, 9, 9, 256, 256);

                // 金色满心
                gui.setColor(1.0f, 0.85f, 0.3f, 1.0f);
                gui.blit(HEART_TEXTURE, x, y, U_FULL, 0, 9, 9, 256, 256);
            }
        }

        // ===== 复位颜色，避免污染后续 HUD =====
        gui.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static int getHeartColor(int heartIndex, long time) {
        Config.HealthColorMode mode = Config.HEALTH_COLOR_MODE.get();
        if (mode == Config.HealthColorMode.CUSTOM) {
            try {
                String hex = Config.HEALTH_CUSTOM_COLOR.get().replace("#", "").trim();
                return Integer.parseInt(hex, 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return 0xFF5555;
            }
        } else {
            float speed = Config.HEALTH_RAINBOW_SPEED.get().floatValue();
            float hue = ((heartIndex * 0.08f) + (time * 0.001f * speed)) % 1.0f;
            if (hue < 0) hue += 1.0f;
            return hsvToRgb(hue, 1.0f, 1.0f);
        }
    }

    private static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6.0f);
        float f = h * 6.0f - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);

        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        int ri = (int) (r * 255.0f);
        int gi = (int) (g * 255.0f);
        int bi = (int) (b * 255.0f);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static int blendColor(int color1, int color2, float amount) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * amount);
        int g = (int) (g1 + (g2 - g1) * amount);
        int b = (int) (b1 + (b2 - b1) * amount);

        return (r << 16) | (g << 8) | b;
    }
}