package com.example.ribbon.lunar;

import com.example.ribbon.LangUtil;

public final class LunarDateInfo {

    public final int solarYear;
    public final int solarMonth;
    public final int solarDay;

    public final int lunarYear;
    public final int lunarMonth;
    public final int lunarDay;
    public final boolean isLeapMonth;

    public final String solarTerm;

    /** 节日 id（翻译键后缀），无节日为 null */
    public final String festivalId;

    public final FestivalTier festivalTier;

    public LunarDateInfo(int solarYear, int solarMonth, int solarDay,
                         int lunarYear, int lunarMonth, int lunarDay,
                         boolean isLeapMonth, String solarTerm,
                         String festivalId, FestivalTier festivalTier) {
        this.solarYear = solarYear;
        this.solarMonth = solarMonth;
        this.solarDay = solarDay;
        this.lunarYear = lunarYear;
        this.lunarMonth = lunarMonth;
        this.lunarDay = lunarDay;
        this.isLeapMonth = isLeapMonth;
        this.solarTerm = solarTerm;
        this.festivalId = festivalId;
        this.festivalTier = festivalTier;
    }

    public enum FestivalTier {
        NONE, MINOR, MAJOR, GRAND
    }

    public String formatLunarDate() {
        return "农历" + monthName(lunarMonth) + dayName(lunarDay);
    }

    public String getFestivalDisplayName() {
        return festivalId == null ? null : LangUtil.trHoliday(festivalId);
    }

    public String formatSuffix() {
        StringBuilder sb = new StringBuilder();
        String f = getFestivalDisplayName();
        if (f != null) sb.append(" · ").append(f);
        if (solarTerm != null) sb.append(" · ").append(solarTerm);
        return sb.toString();
    }

    private static String monthName(int m) {
        String[] names = {"正月", "二月", "三月", "四月", "五月", "六月",
                "七月", "八月", "九月", "十月", "冬月", "腊月"};
        return (m >= 1 && m <= 12) ? names[m - 1] : "?月";
    }

    private static String dayName(int d) {
        String[] dayNames = {"初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
                "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
                "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"};
        return (d >= 1 && d <= 30) ? dayNames[d - 1] : "?日";
    }
}