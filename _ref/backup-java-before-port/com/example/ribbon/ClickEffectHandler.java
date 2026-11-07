package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class ClickEffectHandler {

    private static final List<ClickEffect> EFFECTS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static final int MAX_EFFECTS = 48;
    private static final long DURATION = 700;
    private static final int PIXEL = 2; // 每个像素块的大小

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        int button = event.getButton();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;

        Config.ClickEffectType type = Config.CLICK_EFFECT_TYPE.get();
        if (type == Config.ClickEffectType.NONE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        if (Config.isClickEffectCoverUi() && mc.screen != null) return;

        trigger(mc, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT, type);
    }

    public static void trigger(Minecraft mc, boolean right, Config.ClickEffectType type) {
        if (type == Config.ClickEffectType.NONE) return;
        int[] pos = getMouseGuiPos(mc);
        if (pos == null) return;

        while (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(new ClickEffect(pos[0], pos[1], System.currentTimeMillis(), right, type));
    }

    public static int[] getMouseGuiPos(Minecraft mc) {
        if (mc.mouseHandler == null || mc.getWindow() == null) return null;
        try {
            double scale = mc.getWindow().getGuiScale();
            if (scale <= 0) return null;
            int guiX = (int) (mc.mouseHandler.xpos() / scale);
            int guiY = (int) (mc.mouseHandler.ypos() / scale);
            return new int[]{guiX, guiY};
        } catch (Throwable t) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        long now = System.currentTimeMillis();

        Iterator<ClickEffect> it = EFFECTS.iterator();
        while (it.hasNext()) {
            ClickEffect e = it.next();
            float t = (now - e.startTime) / (float) DURATION;
            if (t >= 1f) {
                it.remove();
                continue;
            }
            render(gui, mc, e, t);
        }
    }

    private static void render(GuiGraphics gui, Minecraft mc, ClickEffect e, float t) {
        switch (e.type) {
            case RIPPLE  -> renderRipple(gui, e, t);
            case RAIN    -> renderRain(gui, e, t);
            case SAKURA  -> renderSakura(gui, e, t);
            case CARD    -> renderCard(gui, mc, e, t);

            // 生肖：像素画
            case RAT     -> renderPixelIcon(gui, e, t, PIXEL_RAT,     PAL_RAT);
            case OX      -> renderPixelIcon(gui, e, t, PIXEL_OX,      PAL_OX);
            case TIGER   -> renderPixelIcon(gui, e, t, PIXEL_TIGER,   PAL_TIGER);
            case RABBIT  -> renderPixelIcon(gui, e, t, PIXEL_RABBIT,  PAL_RABBIT);
            case DRAGON  -> renderPixelIcon(gui, e, t, PIXEL_DRAGON,  PAL_DRAGON);
            case SNAKE   -> renderPixelIcon(gui, e, t, PIXEL_SNAKE,   PAL_SNAKE);
            case HORSE   -> renderPixelIcon(gui, e, t, PIXEL_HORSE,   PAL_HORSE);
            case GOAT    -> renderPixelIcon(gui, e, t, PIXEL_GOAT,    PAL_GOAT);
            case MONKEY  -> renderPixelIcon(gui, e, t, PIXEL_MONKEY,  PAL_MONKEY);
            case ROOSTER -> renderPixelIcon(gui, e, t, PIXEL_ROOSTER, PAL_ROOSTER);
            case DOG     -> renderPixelIcon(gui, e, t, PIXEL_DOG,     PAL_DOG);
            case PIG     -> renderPixelIcon(gui, e, t, PIXEL_PIG,     PAL_PIG);

            // 季节：像素画
            case SPRING  -> renderPixelIcon(gui, e, t, PIXEL_SPRING,  PAL_SPRING);
            case SUMMER  -> renderPixelIcon(gui, e, t, PIXEL_SUMMER,  PAL_SUMMER);
            case AUTUMN  -> renderPixelIcon(gui, e, t, PIXEL_AUTUMN,  PAL_AUTUMN);
            case WINTER  -> renderPixelIcon(gui, e, t, PIXEL_WINTER,  PAL_WINTER);

            default -> {}
        }
    }

    // ============ 像素画通用渲染 ============

    /**
     * 绘制 9×9 像素画。
     * pattern 每个字符对应调色板索引，'.' 表示透明。
     */
    private static void drawPixelIcon(GuiGraphics gui, int cx, int cy, float scale, int alpha,
                                      String[] pattern, int[] palette) {
        if (scale <= 0) return;
        int rows = pattern.length;
        int cols = pattern[0].length();
        int sz = Math.max(1, (int) scale);
        int offsetX = -(cols * sz) / 2;
        int offsetY = -(rows * sz) / 2;

        for (int r = 0; r < rows; r++) {
            String row = pattern[r];
            for (int c = 0; c < cols; c++) {
                char ch = row.charAt(c);
                if (ch == '.') continue;
                int idx = ch - '0';
                if (idx < 0 || idx >= palette.length) continue;
                int base = palette[idx];
                int a = ((base >>> 24) & 0xFF) * alpha / 255;
                int color = (a << 24) | (base & 0x00FFFFFF);
                int px = cx + offsetX + c * sz;
                int py = cy + offsetY + r * sz;
                gui.fill(px, py, px + sz, py + sz, color);
            }
        }
    }

    /** 生肖/季节图标通用渲染：放大 + 上浮 + 光晕 + 环形粒子 */
    private static void renderPixelIcon(GuiGraphics gui, ClickEffect e, float t,
                                        String[] pattern, int[] palette) {
        float alpha = 1f - t;
        int a = Mth.clamp((int)(alpha * 255), 0, 255);

        // 放大动画：从 0.5 到 2.2
        float scale = 0.5f + t * 1.8f;
        int cx = e.x;
        int cy = e.y - (int)(t * 12);

        // 光晕（前 0.4）
        if (t < 0.4f) {
            float glowT = t / 0.4f;
            int ga = Mth.clamp((int)((1f - glowT) * 120), 0, 255);
            int glowColor = (ga << 24) | (palette[0] & 0x00FFFFFF);
            int glowR = (int)(14 * glowT) + 3;
            for (int dx = -glowR; dx <= glowR; dx++) {
                for (int dy = -glowR; dy <= glowR; dy++) {
                    if (dx * dx + dy * dy <= glowR * glowR) {
                        gui.fill(e.x + dx, e.y + dy, e.x + dx + 1, e.y + dy + 1, glowColor);
                    }
                }
            }
        }

        // 主图标
        drawPixelIcon(gui, cx, cy, scale, a, pattern, palette);

        // 环形粒子
        int particleCount = 10;
        for (int i = 0; i < particleCount; i++) {
            double ang = e.seed + i * (Math.PI * 2 / particleCount);
            double dist = t * 34 + 8;
            int px = e.x + (int)(Math.cos(ang) * dist);
            int py = e.y + (int)(Math.sin(ang) * dist * 0.7 - t * 10);
            int pa = Mth.clamp((int)(alpha * 200), 0, 255);
            int pc = (pa << 24) | (palette[0] & 0x00FFFFFF);
            gui.fill(px, py, px + 2, py + 2, pc);
        }
    }

    // ============ 通用图案 ============

    private static void drawDiamond(GuiGraphics gui, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int d = 0; d <= r; d++) {
            int w = r - d;
            gui.fill(cx - w, cy - d, cx + w + 1, cy - d + 1, color);
            if (d != 0) gui.fill(cx - w, cy + d, cx + w + 1, cy + d + 1, color);
        }
    }

    private static void drawStar4(GuiGraphics gui, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int d = 0; d < r; d++) {
            int w = Math.max(1, r - d);
            gui.fill(cx - 1, cy - d, cx + 1, cy - d + 1, color);
            gui.fill(cx - 1, cy + d, cx + 1, cy + d + 1, color);
            gui.fill(cx - d, cy - 1, cx - d + 1, cy + 1, color);
            gui.fill(cx + d, cy - 1, cx + d + 1, cy + 1, color);
        }
    }

    private static void drawRing(GuiGraphics gui, int cx, int cy, float radius, int color, int thickness) {
        int segs = Math.max(16, (int)(radius * 2));
        for (int i = 0; i < segs; i++) {
            float ang = (float)(i * 2 * Math.PI / segs);
            int px = cx + (int)(Math.cos(ang) * radius);
            int py = cy + (int)(Math.sin(ang) * radius);
            gui.fill(px, py, px + thickness, py + thickness, color);
        }
    }

    // ============ 原版 4 个特效 ============

    private static void renderRipple(GuiGraphics gui, ClickEffect e, float t) {
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        float alpha = 1f - t;
        int a = Mth.clamp((int)(alpha * 255), 0, 255);

        int r, g, b;
        if (e.right) { r = 255; g = 100; b = 200; }
        else         { r = 150; g = 230; b = 255; }

        int mainColor = (a << 24) | (r << 16) | (g << 8) | b;
        drawRing(gui, e.x, e.y, 28f * eased, mainColor, Math.max(1, (int)(2.5f * alpha)));

        if (t > 0.15f) {
            float t2 = (t - 0.15f) / 0.85f;
            float eased2 = 1f - (1f - t2) * (1f - t2) * (1f - t2);
            int a2 = Mth.clamp((int)((1f - t2) * 180), 0, 255);
            int innerColor = (a2 << 24) | (r << 16) | (g << 8) | b;
            drawRing(gui, e.x, e.y, 18f * eased2, innerColor, 1);
        }

        if (t < 0.3f) {
            int ca = Mth.clamp((int)((1f - t / 0.3f) * 255), 0, 255);
            int crossColor = (ca << 24) | (r << 16) | (g << 8) | b;
            int len = (int)(10 * (1 - t / 0.3f)) + 2;
            gui.fill(e.x - len, e.y, e.x + len, e.y + 1, crossColor);
            gui.fill(e.x, e.y - len, e.x + 1, e.y + len, crossColor);
        }

        if (t < 0.7f) {
            int sa = Mth.clamp((int)((1f - t / 0.7f) * 200), 0, 255);
            int sparkColor = (sa << 24) | (r << 16) | (g << 8) | b;
            for (int i = 0; i < 8; i++) {
                double ang = e.seed + i * Math.PI / 4;
                int dist = (int)(30f * eased) + 6;
                int sx = e.x + (int)(Math.cos(ang) * dist);
                int sy = e.y + (int)(Math.sin(ang) * dist);
                drawDiamond(gui, sx, sy, 1, sparkColor);
            }
        }
    }

    private static void renderRain(GuiGraphics gui, ClickEffect e, float t) {
        float alpha = 1f - t;
        int a = Mth.clamp((int)(alpha * 255), 0, 255);

        for (int i = 0; i < 7; i++) {
            double offX = Math.cos(e.seed + i * 1.7) * 26;
            double offZ = Math.sin(e.seed + i * 2.3) * 14;
            float dropY = -45f + (51f) * t;
            int px = e.x + (int)(offX + offZ * 0.4);
            int py = e.y + (int)dropY;
            int dropLen = 5 + (i % 3);

            for (int d = 0; d < dropLen; d++) {
                int dropA = Mth.clamp((int)(alpha * 255 * (1 - d / (float)dropLen)), 0, 255);
                int dropColor = (dropA << 24) | (0x4A << 16) | (0xB6 << 8) | 0xF0;
                gui.fill(px, py - d, px + 1, py - d + 1, dropColor);
            }
            int headColor = (a << 24) | (0x9F << 16) | (0xE0 << 8) | 0xFF;
            drawDiamond(gui, px, py, 1, headColor);
        }

        if (t < 0.5f) {
            float splashT = t / 0.5f;
            int sa = Mth.clamp((int)((1f - splashT) * 200), 0, 255);
            int splashColor = (sa << 24) | (0x6F << 16) | (0xC8 << 8) | 0xFF;
            drawRing(gui, e.x, e.y, 12 * splashT, splashColor, 1);
        }
    }

    private static void renderSakura(GuiGraphics gui, ClickEffect e, float t) {
        float alpha = 1f - t;
        int a = Mth.clamp((int)(alpha * 255), 0, 255);

        int[][] colors = {
                {0xFF, 0xB0, 0xD8}, {0xE0, 0xA0, 0xE8},
                {0xFF, 0x9E, 0xC8}, {0xC8, 0xA8, 0xF0},
        };

        for (int i = 0; i < 8; i++) {
            double baseAng = e.seed + i * (Math.PI * 2 / 8);
            float drift = 40f * t + 4f;
            float sway = (float)Math.sin(t * Math.PI * 3 + i * 1.3) * 6f;
            float fall = 18f * t;
            int px = e.x + (int)(Math.cos(baseAng) * drift + sway);
            int py = e.y + (int)(Math.sin(baseAng) * drift * 0.6 - fall);
            int[] rgb = colors[i % colors.length];
            int color = (a << 24) | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            drawDiamond(gui, px, py, 2, color);
        }

        if (t < 0.4f) {
            float glowT = t / 0.4f;
            int ga = Mth.clamp((int)((1f - glowT) * 150), 0, 255);
            int glowColor = (ga << 24) | (0xFF << 16) | (0xC8 << 8) | 0xE8;
            int glowR = (int)(8 * glowT) + 2;
            for (int dx = -glowR; dx <= glowR; dx++) {
                for (int dy = -glowR; dy <= glowR; dy++) {
                    if (dx * dx + dy * dy <= glowR * glowR) {
                        gui.fill(e.x + dx, e.y + dy, e.x + dx + 1, e.y + dy + 1, glowColor);
                    }
                }
            }
        }
    }

    private static void renderCard(GuiGraphics gui, Minecraft mc, ClickEffect e, float t) {
        float alpha = 1f - t;
        int a = Mth.clamp((int)(alpha * 255), 0, 255);

        float flyDist = 30f * t + 5f;
        float angle = (float)(t * Math.PI * 2);
        int cx = e.x + (int)(Math.cos(e.seed + angle) * flyDist);
        int cy = e.y + (int)(Math.sin(e.seed + angle) * flyDist * 0.5f);

        gui.fill(cx - 5, cy + 8, cx + 6, cy + 10, (a / 3 << 24) | 0x000000);

        int halfW = Math.max(3, (int)(6 * Math.abs(Math.cos(angle))));
        int halfH = 8;

        int cardColor = (a << 24) | 0xFFFFFF;
        gui.fill(cx - halfW, cy - halfH, cx + halfW, cy + halfH, cardColor);

        int borderColor = (a << 24) | 0x333333;
        gui.fill(cx - halfW, cy - halfH, cx + halfW, cy - halfH + 1, borderColor);
        gui.fill(cx - halfW, cy + halfH - 1, cx + halfW, cy + halfH, borderColor);
        gui.fill(cx - halfW, cy - halfH, cx - halfW + 1, cy + halfH, borderColor);
        gui.fill(cx + halfW - 1, cy - halfH, cx + halfW, cy + halfH, borderColor);

        String[] suits = {"\u2660", "\u2665", "\u2666", "\u2663"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        int suitIdx = (int)(e.seed * 100) % suits.length;
        int rankIdx = (int)(e.seed * 200) % ranks.length;

        boolean red = suits[suitIdx].equals("\u2665") || suits[suitIdx].equals("\u2666");
        int textColor = red ? 0xFF0000 : 0x000000;
        int fullTextColor = (a << 24) | textColor;

        if (mc.font != null) {
            gui.drawString(mc.font, ranks[rankIdx], cx - halfW + 1, cy - halfH + 1, fullTextColor);
            gui.drawString(mc.font, suits[suitIdx], cx - halfW + 1, cy + 2, fullTextColor);
        }
    }

    // ============ 像素画数据 ============
    // 索引含义：'0' 主色，'1' 深色，'2' 亮色，'3' 白色，'4' 黑色，'.' 透明

    private static final String[] PIXEL_RAT = {
            ".........",
            "0.......0",
            "0.......0",
            "000000000",
            "030404030",
            "000000000",
            ".0222220.",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_RAT = {0xFF9E9E9E, 0xFF616161, 0xFFE0E0E0, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_OX = {
            "3.......3",
            ".3.....3.",
            ".0.....0.",
            "..00000..",
            "000000000",
            "030404030",
            "000000000",
            ".0000000.",
            "..01110.."
    };
    private static final int[] PAL_OX = {0xFF8D6E63, 0xFF5D4037, 0xFFBCAAA4, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_TIGER = {
            ".........",
            "0.......0",
            "00.....00",
            "000000000",
            "011111110",
            "040404040",
            "011111110",
            "000000000",
            ".0000000."
    };
    private static final int[] PAL_TIGER = {0xFFFFA726, 0xFF000000, 0xFFFFF176, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_RABBIT = {
            "..0...0..",
            "..0...0..",
            "..02.20..",
            "..00000..",
            ".0000000.",
            ".0404040.",
            "000000000",
            "..0.0.0..",
            "..00000.."
    };
    private static final int[] PAL_RABBIT = {0xFFF8BBD0, 0xFFCC8899, 0xFFFFE4E1, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_DRAGON = {
            "4.......4",
            "04.....40",
            ".0000000.",
            "000000000",
            "003030300",
            "000000000",
            ".0202020.",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_DRAGON = {0xFFFFD54F, 0xFFEF6C00, 0xFFFFE082, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_SNAKE = {
            ".....00..",
            "...0000..",
            "..000....",
            "..0.0....",
            "...00000.",
            "..00..00.",
            ".000.....",
            "..00.....",
            "...0000.."
    };
    private static final int[] PAL_SNAKE = {0xFF2E7D32, 0xFF1B5E20, 0xFF81C784, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_HORSE = {
            "..000..0.",
            ".0000000.",
            "000000000",
            "030403030",
            "000000000",
            ".0111110.",
            "..0...0..",
            "..0...0..",
            "..0...0.."
    };
    private static final int[] PAL_HORSE = {0xFFBF360C, 0xFF6E1700, 0xFFE64A19, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_GOAT = {
            "..3...3..",
            "..0...0..",
            ".0000000.",
            "000000000",
            ".0304030.",
            "0111111110",
            "..0222220",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_GOAT = {0xFFFAFAFA, 0xFFBDBDBD, 0xFFFFFDE7, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_MONKEY = {
            ".........",
            ".0.....0.",
            ".00...00.",
            "000000000",
            "030404030",
            "000000000",
            ".0222220.",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_MONKEY = {0xFFA1887F, 0xFF5D4037, 0xFFD7CCC8, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_ROOSTER = {
            "...4.....",
            "..404....",
            ".44444...",
            "..00000..",
            ".0303000.",
            "00000000.",
            ".0200020.",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_ROOSTER = {0xFFF57F17, 0xFF8D6E63, 0xFFFBC02D, 0xFFFFFFFF, 0xFFD32F2F};

    private static final String[] PIXEL_DOG = {
            ".........",
            "0.......0",
            "00.....00",
            ".0000000.",
            ".0304030.",
            "000000000",
            ".0222220.",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_DOG = {0xFF8D6E63, 0xFF5D4037, 0xFFD7CCC8, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_PIG = {
            ".........",
            ".........",
            ".0000000.",
            "000000000",
            "030404030",
            "000000000",
            ".0444440.",
            "..00000..",
            "...000..."
    };
    private static final int[] PAL_PIG = {0xFFF48FB1, 0xFFC2185B, 0xFFF8BBD0, 0xFFFFFFFF, 0xFFAD1457};

    private static final String[] PIXEL_SPRING = {
            "....0....",
            "...000...",
            "..00000..",
            ".00.0.00.",
            "00000300.",
            ".00.0.00.",
            "..00000..",
            "...000...",
            "....0...."
    };
    private static final int[] PAL_SPRING = {0xFFFF80AB, 0xFFE91E63, 0xFFFFCDD2, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_SUMMER = {
            "....0....",
            "0.......0",
            "..00000..",
            ".0033300.",
            "..03330..",
            ".0033300.",
            "..00000..",
            "0.......0",
            "....0...."
    };
    private static final int[] PAL_SUMMER = {0xFFFFEB3B, 0xFFF57F17, 0xFFFFF9C4, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_AUTUMN = {
            "....0....",
            "..0...0..",
            "...000...",
            "..00000..",
            ".00000 00",
            "..00000..",
            "...000...",
            "..0...0..",
            "....0...."
    };
    private static final int[] PAL_AUTUMN = {0xFFFF8A65, 0xFFBF360C, 0xFFFFCCBC, 0xFFFFFFFF, 0xFF000000};

    private static final String[] PIXEL_WINTER = {
            "....0....",
            "..0.0.0..",
            "...000...",
            "..00000..",
            ".0003000.",
            "..00000..",
            "...000...",
            "..0.0.0..",
            "....0...."
    };
    private static final int[] PAL_WINTER = {0xFFE1F5FE, 0xFF81D4FA, 0xFFB3E5FC, 0xFFFFFFFF, 0xFF000000};

    private static class ClickEffect {
        final int x, y;
        final long startTime;
        final boolean right;
        final double seed;
        final Config.ClickEffectType type;

        ClickEffect(int x, int y, long startTime, boolean right, Config.ClickEffectType type) {
            this.x = x;
            this.y = y;
            this.startTime = startTime;
            this.right = right;
            this.seed = RANDOM.nextDouble() * Math.PI * 2;
            this.type = type;
        }
    }
}