package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Locale;

/**
 * FPS/TPS 悬浮层。开启「增强悬浮层」后，在 FPS/TPS 行下方追加：
 * 帧时间曲线（最近 120 帧，绿 ≤17ms / 橙 ≤33ms / 红 >33ms）、内存占用、实体/粒子数量。
 */
@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class FpsTpsOverlay {

    private static long lastServerTick = 0L;
    private static double smoothedTps = 20.0;

    private static final int FRAME_SAMPLE_COUNT = 120;
    private static final int[] FRAME_MS = new int[FRAME_SAMPLE_COUNT];
    private static int frameCursor = 0;
    private static long lastFrameNs = 0L;

    // 文本缓存：字符串/实体计数/字体宽度按 HudRefreshThrottle 的间隔重建，帧图仍每帧采样
    private static long lastTextBuildMs;
    private static String cachedMainText = "";
    private static String cachedMemText;
    private static String cachedEntText;
    private static int cachedBlockW;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long now = System.nanoTime();
        if (lastServerTick != 0L) {
            double tps = 1_000_000_000.0 / (now - lastServerTick);
            smoothedTps = smoothedTps * 0.7 + tps * 0.3;
        }
        lastServerTick = now;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) return;
        if (!Config.SHOW_OVERLAY.get()) return;

        // ===== 帧时间采样（每帧一次）=====
        long now = System.nanoTime();
        if (lastFrameNs != 0L) {
            FRAME_MS[frameCursor] = (int) Math.min(100L, (now - lastFrameNs) / 1_000_000L);
            frameCursor = (frameCursor + 1) % FRAME_SAMPLE_COUNT;
        }
        lastFrameNs = now;

        // 窗口失焦时 HUD 不可见：保留极廉价的帧采样，跳过文本重建与帧图绘制
        if (com.example.ribbon.optimization.FocusState.shouldPauseBackgroundWork()) return;

        boolean enhanced = com.example.ribbon.optimization.RuntimeConfig.overlayEnhanced;

        GuiGraphics g = event.getGuiGraphics();
        var font = mc.font;

        // ===== 文本节流：字符串构造 / 实体计数 / 字体宽度只在间隔到期时执行 =====
        long nowMs = System.currentTimeMillis();
        if (com.example.ribbon.optimization.HudRefreshThrottle.shouldRefresh(lastTextBuildMs, nowMs)) {
            cachedMainText = String.format(Locale.ROOT, "FPS: %d | TPS: %.1f",
                    mc.getFps(), Math.min(smoothedTps, 20.0));
            if (enhanced) {
                Runtime rt = Runtime.getRuntime();
                cachedMemText = String.format(Locale.ROOT, "MEM %d/%dMB",
                        (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
                cachedEntText = String.format(Locale.ROOT, "E %d | P %s",
                        countEntities(mc), mc.particleEngine.countParticles());
                cachedBlockW = Math.max(60, Math.max(font.width(cachedMainText),
                        Math.max(font.width(cachedMemText), font.width(cachedEntText))));
            } else {
                cachedMemText = null;
                cachedEntText = null;
                cachedBlockW = font.width(cachedMainText);
            }
            lastTextBuildMs = nowMs;
        }

        int th = font.lineHeight;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float s = Config.OVERLAY_SCALE.get().floatValue();
        int m = 4;
        int blockW = cachedBlockW;

        float tx, ty;
        switch (Config.OVERLAY_CORNER.get()) {
            case TOP_RIGHT    -> { tx = sw - m - blockW * s; ty = m; }
            case BOTTOM_LEFT  -> { tx = m;                  ty = sh - m - th * s; }
            case BOTTOM_RIGHT -> { tx = sw - m - blockW * s; ty = sh - m - th * s; }
            default           -> { tx = m;                  ty = m; }
        }

        var pose = g.pose();
        pose.pushPose();
        pose.translate(tx, ty, 0.0f);
        pose.scale(s, s, 1.0f);
        g.drawString(font, cachedMainText, 0, 0, 0xFFFFFF);
        pose.popPose();

        if (!enhanced) return;

        // ===== 增强信息（跟随主行，纵向排布）=====
        float graphY = ty + th * s + 2;
        drawFrameGraph(g, tx, graphY, s);

        float line1Y = graphY + 18 * s + 2;
        drawLine(g, font, cachedMemText, tx, line1Y, s);
        drawLine(g, font, cachedEntText, tx, line1Y + th * s + 1, s);
    }

    /**
     * 帧时间曲线：60 列（每列聚合 2 帧），高度 18px，颜色按 60/30 FPS 分档。
     *
     * <p>按「连续相同高度+颜色」做 run-length 合并后一次 fill：稳态帧率下
     * 60 次独立 draw call 降到 1–3 次，图形完全一致。</p>
     */
    private static void drawFrameGraph(GuiGraphics g, float x, float y, float s) {
        int w = 60;
        int h = 18;
        g.fill((int) x, (int) y, (int) (x + w * s), (int) (y + h * s), 0x66000000);

        int runStart = -1;
        int runBarH = 0;
        int runColor = 0;
        for (int i = 0; i <= w; i++) {
            int ms = i < w ? FRAME_MS[(frameCursor + i * 2) % FRAME_SAMPLE_COUNT] : 0;
            int barH = 0;
            int color = 0;
            if (ms > 0) {
                barH = Math.max(1, Math.min(h, ms * h / 50));
                color = ms <= 17 ? 0xFF67D66B : (ms <= 33 ? 0xFFF0B429 : 0xFFE4574C);
            }
            boolean sameRun = i < w && ms > 0 && runStart >= 0
                    && barH == runBarH && color == runColor;
            if (!sameRun) {
                if (runStart >= 0) {
                    g.fill((int) (x + runStart * s), (int) (y + (h - runBarH) * s),
                            (int) (x + i * s), (int) (y + h * s), runColor);
                }
                runStart = (i < w && ms > 0) ? i : -1;
                runBarH = barH;
                runColor = color;
            }
        }
    }

    private static void drawLine(GuiGraphics g, net.minecraft.client.gui.Font font, String text, float x, float y, float s) {
        if (text == null) return;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0f);
        pose.scale(s, s, 1.0f);
        g.drawString(font, text, 0, 0, 0xC8FFFFFF, true);
        pose.popPose();
    }

    /** entitiesForRendering() 返回 Iterable，手动计数（每帧一次，开销可忽略）。 */
    private static int countEntities(Minecraft mc) {
        int count = 0;
        for (var ignored : mc.level.entitiesForRendering()) {
            count++;
        }
        return count;
    }
}
