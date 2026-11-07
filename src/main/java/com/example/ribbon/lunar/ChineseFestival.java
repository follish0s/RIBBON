package com.example.ribbon.lunar;

import java.time.LocalDate;

/**
 * 中国传统节日匹配。
 * 返回节日 id（翻译键后缀），由 LangUtil.trHoliday 翻译显示。
 */
public final class ChineseFestival {

    private ChineseFestival() {}

    public static Result match(LunarDateInfo lunar, LocalDate date) {
        int m = lunar.lunarMonth;
        int d = lunar.lunarDay;
        boolean leap = lunar.isLeapMonth;

        if (!leap) {
            if (m == 1 && d == 1) return new Result("spring_festival", LunarDateInfo.FestivalTier.GRAND);
            if (m == 1 && d == 15) return new Result("lantern_festival", LunarDateInfo.FestivalTier.MAJOR);
            if (m == 5 && d == 5) return new Result("dragon_boat", LunarDateInfo.FestivalTier.MAJOR);
            if (m == 7 && d == 7) return new Result("qixi", LunarDateInfo.FestivalTier.MINOR);
            if (m == 7 && d == 15) return new Result("zhongyuan", LunarDateInfo.FestivalTier.MINOR);
            if (m == 8 && d == 15) return new Result("mid_autumn", LunarDateInfo.FestivalTier.MAJOR);
            if (m == 9 && d == 9) return new Result("double_ninth", LunarDateInfo.FestivalTier.MINOR);
            if (m == 12 && d == 8) return new Result("laba", LunarDateInfo.FestivalTier.MINOR);
            if (m == 12 && d == 23) return new Result("xiaonian", LunarDateInfo.FestivalTier.MINOR);
        }

        if (!leap && m == 12) {
            int lastDay = LunarCalendar.daysInLunarMonth(lunar.lunarYear, 12);
            if (d == lastDay) return new Result("chinese_new_year_eve", LunarDateInfo.FestivalTier.GRAND);
        }

        int sm = date.getMonthValue();
        int sd = date.getDayOfMonth();
        if (sm == 4 && (sd == 4 || sd == 5) && "清明".equals(lunar.solarTerm)) {
            return new Result("qingming", LunarDateInfo.FestivalTier.MAJOR);
        }

        return null;
    }

    public static final class Result {
        public final String id;
        public final LunarDateInfo.FestivalTier tier;

        public Result(String id, LunarDateInfo.FestivalTier tier) {
            this.id = id;
            this.tier = tier;
        }
    }
}