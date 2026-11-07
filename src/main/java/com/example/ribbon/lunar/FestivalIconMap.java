package com.example.ribbon.lunar;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 节日 id → 图标物品映射。
 * 用原版物品作为节日图标，避免自己画纹理。
 */
public final class FestivalIconMap {

    private FestivalIconMap() {}

    public static ItemStack getIcon(String festivalId) {
        if (festivalId == null) return ItemStack.EMPTY;

        return switch (festivalId) {
            // 中国节日
            case "spring_festival", "chinese_new_year_eve" -> new ItemStack(Items.LANTERN);
            case "lantern_festival" -> new ItemStack(Items.CANDLE);
            case "qingming" -> new ItemStack(Items.WHITE_TULIP);
            case "dragon_boat" -> new ItemStack(Items.DRIED_KELP);
            case "qixi" -> new ItemStack(Items.AMETHYST_SHARD);
            case "zhongyuan" -> new ItemStack(Items.SOUL_LANTERN);
            case "mid_autumn" -> new ItemStack(Items.PUMPKIN_PIE);
            case "double_ninth" -> new ItemStack(Items.HONEY_BOTTLE);
            case "laba" -> new ItemStack(Items.BEETROOT_SOUP);
            case "xiaonian" -> new ItemStack(Items.SUGAR);
            case "china_new_year" -> new ItemStack(Items.CAKE);
            case "china_labor" -> new ItemStack(Items.IRON_INGOT);
            case "china_national" -> new ItemStack(Items.RED_BANNER);

            // 国际节日
            case "valentine" -> new ItemStack(Items.PINK_TULIP);
            case "halloween" -> new ItemStack(Items.CARVED_PUMPKIN);
            case "easter" -> new ItemStack(Items.EGG);
            case "april_fools" -> new ItemStack(Items.TROPICAL_FISH);
            case "intl_womens" -> new ItemStack(Items.PINK_DYE);
            case "intl_labor" -> new ItemStack(Items.GOLDEN_PICKAXE);
            case "intl_children" -> new ItemStack(Items.SLIME_BALL);
            case "intl_red_cross" -> new ItemStack(Items.RED_DYE);
            case "intl_nurse" -> new ItemStack(Items.POTION);
            case "intl_environment" -> new ItemStack(Items.OAK_SAPLING);
            case "intl_peace" -> new ItemStack(Items.WHITE_DYE);
            case "intl_human_rights" -> new ItemStack(Items.BOOK);

            // 美国
            case "us_new_year" -> new ItemStack(Items.CAKE);
            case "us_mlk" -> new ItemStack(Items.BOOK);
            case "us_presidents" -> new ItemStack(Items.GOLDEN_HELMET);
            case "us_memorial" -> new ItemStack(Items.POPPY);
            case "us_independence" -> new ItemStack(Items.FIREWORK_ROCKET);
            case "us_labor" -> new ItemStack(Items.IRON_PICKAXE);
            case "us_thanksgiving" -> new ItemStack(Items.COOKED_CHICKEN);
            case "us_christmas" -> new ItemStack(Items.SNOWBALL);

            // 英国
            case "uk_new_year" -> new ItemStack(Items.CAKE);
            case "uk_good_friday" -> new ItemStack(Items.BREAD);
            case "uk_easter_monday" -> new ItemStack(Items.EGG);
            case "uk_spring_bank" -> new ItemStack(Items.SUNFLOWER);
            case "uk_summer_bank" -> new ItemStack(Items.SUNFLOWER);
            case "uk_christmas" -> new ItemStack(Items.SNOWBALL);
            case "uk_boxing" -> new ItemStack(Items.CHEST);

            // 日本
            case "jp_new_year" -> new ItemStack(Items.CAKE);
            case "jp_coming_of_age" -> new ItemStack(Items.GOLDEN_HELMET);
            case "jp_national_foundation" -> new ItemStack(Items.WHITE_BANNER);
            case "jp_showa" -> new ItemStack(Items.SUNFLOWER);
            case "jp_constitution" -> new ItemStack(Items.BOOK);
            case "jp_greenery" -> new ItemStack(Items.OAK_SAPLING);
            case "jp_children" -> new ItemStack(Items.PAPER);
            case "jp_culture" -> new ItemStack(Items.BOOK);
            case "jp_labor_thanks" -> new ItemStack(Items.COOKED_BEEF);

            // 韩国
            case "kr_new_year" -> new ItemStack(Items.CAKE);
            case "kr_independence" -> new ItemStack(Items.WHITE_BANNER);
            case "kr_liberation" -> new ItemStack(Items.RED_BANNER);
            case "kr_national_foundation" -> new ItemStack(Items.WHITE_BANNER);
            case "kr_hangul" -> new ItemStack(Items.BOOK);

            default -> ItemStack.EMPTY;
        };
    }

    /** 节日 id → 徽章背景色 */
    public static int getBadgeColor(String festivalId) {
        if (festivalId == null) return 0x80000000;

        return switch (festivalId) {
            case "spring_festival", "chinese_new_year_eve", "china_national",
                 "us_independence", "kr_liberation" -> 0xC0CC2222; // 红
            case "mid_autumn", "uk_spring_bank", "uk_summer_bank",
                 "jp_showa" -> 0xC0CCAA22; // 金
            case "lantern_festival", "zhongyuan", "halloween" -> 0xC0AA44CC; // 紫
            case "christmas", "us_christmas", "uk_christmas" -> 0xC0228833; // 绿
            case "easter", "intl_environment", "jp_greenery",
                 "double_ninth" -> 0xC0448822; // 草绿
            case "valentine", "qixi", "intl_womens" -> 0xC0CC4488; // 粉
            case "china_new_year", "us_new_year", "uk_new_year",
                 "jp_new_year", "kr_new_year" -> 0xC0CC9922; // 暖黄
            default -> 0xA0303030; // 默认深灰
        };
    }
}