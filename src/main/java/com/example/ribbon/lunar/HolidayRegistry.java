package com.example.ribbon.lunar;

import com.example.ribbon.lunar.rules.EasterBasedRule;
import com.example.ribbon.lunar.rules.FixedDateRule;
import com.example.ribbon.lunar.rules.NthWeekdayRule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class HolidayRegistry {

    private static final List<HolidayRule> RULES = new ArrayList<>();

    static {
        // ===== 中国公历节日 =====
        RULES.add(new FixedDateRule("china_new_year", 1, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("china_labor", 5, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("china_national", 10, 1, LunarDateInfo.FestivalTier.MAJOR));

        // ===== 美国 =====
        RULES.add(new FixedDateRule("us_new_year", 1, 1, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new NthWeekdayRule("us_mlk", 1, DayOfWeek.MONDAY, 3, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new NthWeekdayRule("us_presidents", 2, DayOfWeek.MONDAY, 3, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new NthWeekdayRule("us_memorial", 5, DayOfWeek.MONDAY, 5, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("us_independence", 7, 4, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new NthWeekdayRule("us_labor", 9, DayOfWeek.MONDAY, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new NthWeekdayRule("us_thanksgiving", 11, DayOfWeek.THURSDAY, 4, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new FixedDateRule("us_christmas", 12, 25, LunarDateInfo.FestivalTier.MAJOR));

        // ===== 英国 =====
        RULES.add(new FixedDateRule("uk_new_year", 1, 1, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new EasterBasedRule("uk_good_friday", -2, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new EasterBasedRule("uk_easter_monday", 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new NthWeekdayRule("uk_spring_bank", 5, DayOfWeek.MONDAY, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new NthWeekdayRule("uk_summer_bank", 8, DayOfWeek.MONDAY, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("uk_christmas", 12, 25, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new FixedDateRule("uk_boxing", 12, 26, LunarDateInfo.FestivalTier.MINOR));

        // ===== 日本 =====
        RULES.add(new FixedDateRule("jp_new_year", 1, 1, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new NthWeekdayRule("jp_coming_of_age", 1, DayOfWeek.MONDAY, 2, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_national_foundation", 2, 11, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_showa", 4, 29, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_constitution", 5, 3, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_greenery", 5, 4, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_children", 5, 5, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_culture", 11, 3, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("jp_labor_thanks", 11, 23, LunarDateInfo.FestivalTier.MINOR));

        // ===== 韩国 =====
        RULES.add(new FixedDateRule("kr_new_year", 1, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("kr_independence", 3, 1, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new FixedDateRule("kr_liberation", 8, 15, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new FixedDateRule("kr_national_foundation", 10, 3, LunarDateInfo.FestivalTier.MAJOR));
        RULES.add(new FixedDateRule("kr_hangul", 10, 9, LunarDateInfo.FestivalTier.MINOR));

        // ===== 国际 =====
        RULES.add(new FixedDateRule("valentine", 2, 14, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_womens", 3, 8, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("april_fools", 4, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_labor", 5, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_red_cross", 5, 8, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_nurse", 5, 12, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_children", 6, 1, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_environment", 6, 5, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_peace", 9, 21, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("halloween", 10, 31, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new FixedDateRule("intl_human_rights", 12, 10, LunarDateInfo.FestivalTier.MINOR));
        RULES.add(new EasterBasedRule("easter", 0, LunarDateInfo.FestivalTier.MAJOR));
    }

    private HolidayRegistry() {}

    public static List<HolidayRule> getRules() {
        return RULES;
    }

    /** 返回所有命中的节日 id，按重要程度降序 */
    public static List<HolidayRule> matchAll(LocalDate date, LunarDateInfo lunarInfo) {
        List<HolidayRule> result = new ArrayList<>();
        for (HolidayRule rule : RULES) {
            if (rule.matches(date, lunarInfo)) {
                result.add(rule);
            }
        }
        result.sort((a, b) -> Integer.compare(b.getTier().ordinal(), a.getTier().ordinal()));
        return result;
    }

    /** 返回最高重要程度的节日，没有返回 null */
    public static HolidayRule matchPrimary(LocalDate date, LunarDateInfo lunarInfo) {
        List<HolidayRule> all = matchAll(date, lunarInfo);
        return all.isEmpty() ? null : all.get(0);
    }
}