package com.example.ribbon.lunar;

import com.example.ribbon.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 节日当天在屏幕角落显示物品图标徽章。
 */
@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class FestivalBadgeHandler {

    private static final int ICON_SIZE = 16;
    private static final int PADDING = 4;

    private FestivalBadgeHandler() {}

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        if (!Config.isFestivalBadgeEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        LunarDateInfo info = LunarDateProvider.today();
        if (info.festivalId == null) return;
        if (info.festivalTier == LunarDateInfo.FestivalTier.NONE) return;

        ItemStack icon = FestivalIconMap.getIcon(info.festivalId);
        if (icon.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int badgeW = ICON_SIZE + PADDING * 2;
        int badgeH = ICON_SIZE + PADDING * 2;

        int bx, by;
        switch (Config.getFestivalBadgeCorner()) {
            case TOP_LEFT     -> { bx = PADDING;              by = PADDING; }
            case TOP_RIGHT    -> { bx = sw - badgeW - PADDING; by = PADDING; }
            case BOTTOM_RIGHT -> { bx = sw - badgeW - PADDING; by = sh - badgeH - PADDING - 40; }
            default           -> { bx = PADDING;              by = sh - badgeH - PADDING - 40; } // BOTTOM_LEFT
        }

        // 背景（圆角感用两个矩形模拟）
        int bgColor = FestivalIconMap.getBadgeColor(info.festivalId);
        g.fill(bx, by, bx + badgeW, by + badgeH, bgColor);
        // 顶部高光线
        g.fill(bx, by, bx + badgeW, by + 1, 0x40FFFFFF);
        // 底部暗线
        g.fill(bx, by + badgeH - 1, bx + badgeW, by + badgeH, 0x30000000);

        // 物品图标
        g.renderItem(icon, bx + PADDING, by + PADDING);
    }
}