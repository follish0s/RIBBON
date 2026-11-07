package com.example.ribbon.music;

import com.example.ribbon.LangUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.File;
import java.util.List;

/**
 * 独立音乐播放器界面。
 * 纯黑背景 + 底部控制栏。
 */
public class MusicPlayerScreen extends Screen {

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_TOP = 64;
    private static final int SIDE_PADDING = 24;

    // 底部控制栏的高度
    private static final int CONTROL_BAR_HEIGHT = 40;

    private int scrollOffset = 0;

    public MusicPlayerScreen() {
        super(Component.literal(LangUtil.tr("music.screen.title")));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // ===== 液态玻璃背景 =====
        drawLiquidGlassBackground(g);

        // ===== 点缀 1：四角直角标记 =====
        drawCorner(g, 8, 8, 1, 1);
        drawCorner(g, this.width - 8, 8, -1, 1);
        drawCorner(g, 8, this.height - 8, 1, -1);
        drawCorner(g, this.width - 8, this.height - 8, -1, -1);

        // ===== 点缀 2：标题下方装饰线 =====
        int decoW = 80;
        g.fill((this.width - decoW) / 2, 38, (this.width + decoW) / 2, 39, 0x44FFFFFF);

        // ===== 标题 =====
        String title = LangUtil.tr("music.screen.title");
        int tw = this.font.width(title);
        g.drawString(this.font, title, (this.width - tw) / 2, 22, 0xFFFFFFFF, true);

        // ===== 曲目列表区域 =====
        int listBottom = this.height - CONTROL_BAR_HEIGHT - 10;
        int maxRows = (listBottom - LIST_TOP) / ROW_HEIGHT;
        int currentIndex = MusicPlayerManager.getCurrentIndex();
        Mp3Player player = MusicPlayerManager.getPlayer();

        List<File> tracks = MusicLibrary.getTracks();
        if (tracks.isEmpty()) {
            String empty = LangUtil.tr("music.no_tracks");
            int ew = this.font.width(empty);
            g.drawString(this.font, empty, (this.width - ew) / 2, this.height / 2 - 20, 0xFFAAAAAA, true);
        } else {
            int maxScroll = Math.max(0, tracks.size() - maxRows);
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

            int left = SIDE_PADDING;
            int right = this.width - SIDE_PADDING;

            for (int i = 0; i < maxRows; i++) {
                int idx = i + scrollOffset;
                if (idx >= tracks.size()) break;

                int y = LIST_TOP + i * ROW_HEIGHT;
                boolean hovered = mouseX >= left && mouseX <= right
                        && mouseY >= y && mouseY < y + ROW_HEIGHT;

                String prefix;
                int color;
                if (idx == currentIndex) {
                    if (player.isPlaying()) {
                        prefix = ">> ";
                        color = 0xFF55FFAA;
                    } else if (player.isPaused()) {
                        prefix = "|| ";
                        color = 0xFFFFAA55;
                    } else {
                        prefix = "   ";
                        color = 0xFF55FFAA;
                    }
                } else {
                    prefix = "   ";
                    color = hovered ? 0xFFFFFFFF : 0xFFBFBFBF;
                }

                if (hovered) {
                    g.fill(left, y, right, y + ROW_HEIGHT, 0x22FFFFFF);
                }

                String name = tracks.get(idx).getName().replaceAll("(?i)\\.mp3$", "");
                String line = String.format("%02d. %s%s", idx + 1, prefix, name);
                g.drawString(this.font, line, left + 4, y + 5, color, false);
            }

            // 滚动指示
            if (tracks.size() > maxRows) {
                String counter = (scrollOffset + 1) + "-"
                        + Math.min(tracks.size(), scrollOffset + maxRows)
                        + " / " + tracks.size();
                int cw = this.font.width(counter);
                g.drawString(this.font, counter, right - cw, listBottom + 2, 0xFF888888, false);
            }
        }

        // ===== 底部控制栏 =====
        drawControlBar(g, mouseX, mouseY);
    }

    /**
     * 液态玻璃背景：多层半透明叠加模拟高斯模糊毛玻璃效果。
     * <p>
     * Minecraft 没有可编程的后处理 blur，因此用 5 层递进的半透明矩形
     * 叠加出由外向内逐渐变深的渐变效果，视觉上近似磨砂玻璃。
     */
    private void drawLiquidGlassBackground(GuiGraphics g) {
        int w = this.width, h = this.height;

        // 第 1 层：最浅底色（让背后的游戏画面隐约可见）
        g.fill(0, 0, w, h, 0x99000000);

        // 第 2-5 层：向中心收缩的渐晕层（每层加深一点，模拟景深模糊）
        int shrink = 0;
        int[] layers = {0x0DFFFFFF, 0x0AFFFFFF, 0x08FFFFFF, 0x05FFFFFF};
        for (int alpha : layers) {
            shrink += 40;
            if (shrink * 2 < w && shrink * 2 < h) {
                g.fill(shrink, shrink, w - shrink, h - shrink, alpha);
            }
        }

        // 顶部高光条（模拟玻璃表面的环境光反射）
        g.fill(0, 0, w, 1, 0x33FFFFFF);
        g.fill(0, 1, w, 2, 0x1AFFFFFF);

        // 底部暗边
        g.fill(0, h - 1, w, h, 0x33000000);
    }

    /**
     * 绘制底部的播放控制栏（液态玻璃风格）
     */
    private void drawControlBar(GuiGraphics g, int mouseX, int mouseY) {
        int top = this.height - CONTROL_BAR_HEIGHT;

        // 控制栏：半透明毛玻璃
        g.fill(0, top, this.width, this.height, 0xAA000000);
        g.fill(0, top, this.width, this.height, 0x11FFFFFF);
        // 顶部高光细线
        g.fill(0, top, this.width, top + 1, 0x44FFFFFF);

        Mp3Player player = MusicPlayerManager.getPlayer();
        boolean hasTrack = MusicPlayerManager.getCurrentIndex() >= 0;

        // 1. 上一首按钮 (中心点靠左 40px)
        int centerY = top + CONTROL_BAR_HEIGHT / 2;
        int prevX = this.width / 2 - 60;
        boolean hoverPrev = isHovered(mouseX, mouseY, prevX - 10, centerY - 10, 20, 20);
        g.drawString(this.font, "<<", prevX - 4, centerY - 4, hoverPrev && hasTrack ? 0xFFFFFFFF : 0xFF888888, false);

        // 2. 播放/暂停按钮 (中心点)
        int playX = this.width / 2;
        boolean hoverPlay = isHovered(mouseX, mouseY, playX - 15, centerY - 15, 30, 30);
        String playIcon;
        int playColor;

        if (player.isPlaying()) {
            playIcon = "||"; // 暂停图标
            playColor = hoverPlay ? 0xFFFFAA55 : 0xFFFFAA55;
        } else if (player.isPaused()) {
            playIcon = ">";  // 播放图标
            playColor = hoverPlay ? 0xFF55FFAA : 0xFF55FFAA;
        } else {
            playIcon = ">";  // 停止状态
            playColor = hoverPlay && hasTrack ? 0xFFFFFFFF : 0xFF888888;
        }

        // 给播放按钮画个圆圈背景
        if (hasTrack) {
            g.fill(playX - 12, centerY - 12, playX + 12, centerY + 12, hoverPlay ? 0x33FFFFFF : 0x11FFFFFF);
        }
        int pw = this.font.width(playIcon);
        g.drawString(this.font, playIcon, playX - pw / 2, centerY - 4, playColor, true);

        // 3. 下一首按钮 (中心点靠右 40px)
        int nextX = this.width / 2 + 60;
        boolean hoverNext = isHovered(mouseX, mouseY, nextX - 10, centerY - 10, 20, 20);
        g.drawString(this.font, ">>", nextX - 4, centerY - 4, hoverNext && hasTrack ? 0xFFFFFFFF : 0xFF888888, false);

        // 4. 进度时间 (显示在左侧)
        if (hasTrack) {
            long ms = player.getPositionMs();
            long sec = (ms / 1000) % 60;
            long min = (ms / 1000) / 60;
            String timeStr = String.format("%02d:%02d", min, sec);
            g.drawString(this.font, timeStr, SIDE_PADDING, centerY - 4, 0xFF55FFAA, false);
        }
    }

    private boolean isHovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawCorner(GuiGraphics g, int x, int y, int dx, int dy) {
        int len = 14;
        int color = 0x66FFFFFF;
        g.fill(x, y, x + dx * len, y + dy, color);
        g.fill(x, y, x + dx, y + dy * len, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int centerY = this.height - CONTROL_BAR_HEIGHT / 2;
        boolean hasTrack = MusicPlayerManager.getCurrentIndex() >= 0;

        // 如果点击了底部控制栏
        if (my >= this.height - CONTROL_BAR_HEIGHT) {
            if (hasTrack) {
                // 判断点击的是哪个区域
                if (mx >= this.width / 2 - 70 && mx <= this.width / 2 - 50) {
                    // 上一首
                    MusicPlayerManager.prev();
                    return true;
                } else if (mx >= this.width / 2 - 15 && mx <= this.width / 2 + 15) {
                    // 播放/暂停
                    MusicPlayerManager.playPause();
                    return true;
                } else if (mx >= this.width / 2 + 50 && mx <= this.width / 2 + 70) {
                    // 下一首
                    MusicPlayerManager.next();
                    return true;
                }
            }
            return true; // 点空白处也拦截，防止穿透
        }

        // ===== 列表区域点击 =====
        List<File> tracks = MusicLibrary.getTracks();
        if (tracks.isEmpty()) return super.mouseClicked(mx, my, button);

        int listBottom = this.height - CONTROL_BAR_HEIGHT - 10;
        int maxRows = (listBottom - LIST_TOP) / ROW_HEIGHT;

        int left = SIDE_PADDING;
        int right = this.width - SIDE_PADDING;

        if (mx >= left && mx <= right && my >= LIST_TOP && my < LIST_TOP + maxRows * ROW_HEIGHT) {
            int i = (int) ((my - LIST_TOP) / ROW_HEIGHT);
            int idx = i + scrollOffset;
            if (idx >= 0 && idx < tracks.size()) {
                MusicPlayerManager.playIndex(idx);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOffset -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}