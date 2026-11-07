package com.example.ribbon.lunar;

import com.example.ribbon.Config;
import com.example.ribbon.LangUtil;
import net.minecraft.client.Minecraft;
import com.example.ribbon.compat.GuiGraphics;

import java.util.List;

/**
 * HUD 显示距离下一个节日还有几天。
 */
public final class FestivalCountdownHandler {

    private FestivalCountdownHandler() {}

    public static void onRender(com.example.ribbon.compat.GuiGraphics g) {
        if (!Config.isFestivalCountdownEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || com.example.ribbon.compat.LegacyRenderState.isGuiHidden(mc)) return;

        List<FestivalPreview.Entry> upcoming = FestivalPreview.getUpcoming(1);
        if (upcoming.isEmpty()) return;

        FestivalPreview.Entry next = upcoming.get(0);
        if (next.daysAway == 0) return; // 今天就是节日，让徽章显示就好

        String name = LangUtil.trHoliday(next.festivalId);
        String text = "距 " + name + " " + next.daysAway + " 天";
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = mc.font.width(text);
        int th = mc.font.lineHeight;
        int m = 4;

        // 默认放在左上角（FPS 悬浮窗下方 12px 处，避免重叠）
        int x = m;
        int y = m + 14;

        g.drawString(mc.font, text, x, y, 0xFFFFFFFF, true);
    }
}