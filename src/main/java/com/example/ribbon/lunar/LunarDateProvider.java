package com.example.ribbon.lunar;

import com.example.ribbon.Config;

import java.time.LocalDate;

public final class LunarDateProvider {

    private static LocalDate lastDate = null;
    private static LunarDateInfo lastResult = null;

    private LunarDateProvider() {}

    public static LunarDateInfo get(LocalDate date) {
        if (date == null) date = LocalDate.now();

        if (date.equals(lastDate) && lastResult != null) {
            return lastResult;
        }

        LunarDateInfo raw = LunarCalendar.toLunar(date);
        String solarTerm = SolarTerm.getTerm(date);

        // 优先级 1：中国农历节日
        String festivalId = null;
        LunarDateInfo.FestivalTier tier = LunarDateInfo.FestivalTier.NONE;

        ChineseFestival.Result chineseFest = ChineseFestival.match(raw, date);
        if (chineseFest != null) {
            festivalId = chineseFest.id;
            tier = chineseFest.tier;
        } else {
            // 优先级 2：其他国家 / 国际节日
            Config.HolidayScope scope = Config.getHolidayScope();
            if (scope != Config.HolidayScope.CHINA_ONLY) {
                HolidayRule rule = HolidayRegistry.matchPrimary(date, raw);
                if (rule != null) {
                    festivalId = rule.getId();
                    tier = rule.getTier();
                }
            }
        }

        LunarDateInfo result = new LunarDateInfo(
                raw.solarYear, raw.solarMonth, raw.solarDay,
                raw.lunarYear, raw.lunarMonth, raw.lunarDay, raw.isLeapMonth,
                solarTerm, festivalId, tier
        );

        lastDate = date;
        lastResult = result;
        return result;
    }

    public static LunarDateInfo today() {
        return get(LocalDate.now());
    }

    public static void invalidate() {
        lastDate = null;
        lastResult = null;
    }
}