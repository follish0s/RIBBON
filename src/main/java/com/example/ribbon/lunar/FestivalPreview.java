package com.example.ribbon.lunar;

import com.example.ribbon.Config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 未来节日预告。
 * 每天计算一次，缓存在静态字段。
 */
public final class FestivalPreview {

    private static LocalDate cachedDate = null;
    private static List<Entry> cachedList = null;

    private FestivalPreview() {}

    /**
     * 获取从今天起的未来 N 个节日。
     */
    public static List<Entry> getUpcoming(int count) {
        LocalDate today = LocalDate.now();
        if (today.equals(cachedDate) && cachedList != null) {
            return cachedList.size() >= count ? cachedList.subList(0, count) : cachedList;
        }

        List<Entry> result = new ArrayList<>();
        LocalDate date = today;
        Config.HolidayScope scope = Config.getHolidayScope();

        for (int i = 0; i < 400 && result.size() < count; i++) {
            LunarDateInfo info = computeUncached(date, scope);
            if (info.festivalId != null
                    && info.festivalTier != LunarDateInfo.FestivalTier.NONE) {
                long daysAway = date.toEpochDay() - today.toEpochDay();
                result.add(new Entry(date, info.festivalId, info.festivalTier, daysAway));
            }
            date = date.plusDays(1);
        }

        cachedDate = today;
        cachedList = result;
        return result;
    }

    /** 不碰 LunarDateProvider 缓存的独立计算 */
    private static LunarDateInfo computeUncached(LocalDate date, Config.HolidayScope scope) {
        LunarDateInfo raw = LunarCalendar.toLunar(date);
        String solarTerm = SolarTerm.getTerm(date);

        String festivalId = null;
        LunarDateInfo.FestivalTier tier = LunarDateInfo.FestivalTier.NONE;

        ChineseFestival.Result chineseFest = ChineseFestival.match(raw, date);
        if (chineseFest != null) {
            festivalId = chineseFest.id;
            tier = chineseFest.tier;
        } else if (scope != Config.HolidayScope.CHINA_ONLY) {
            HolidayRule rule = HolidayRegistry.matchPrimary(date, raw);
            if (rule != null) {
                festivalId = rule.getId();
                tier = rule.getTier();
            }
        }

        return new LunarDateInfo(
                raw.solarYear, raw.solarMonth, raw.solarDay,
                raw.lunarYear, raw.lunarMonth, raw.lunarDay, raw.isLeapMonth,
                solarTerm, festivalId, tier
        );
    }

    /** 单条节日预告 */
    public static final class Entry {
        public final LocalDate date;
        public final String festivalId;
        public final LunarDateInfo.FestivalTier tier;
        public final long daysAway;

        public Entry(LocalDate date, String festivalId,
                     LunarDateInfo.FestivalTier tier, long daysAway) {
            this.date = date;
            this.festivalId = festivalId;
            this.tier = tier;
            this.daysAway = daysAway;
        }

        /** 用于 GUI 显示的文本 */
        public String format() {
            String name = com.example.ribbon.LangUtil.trHoliday(festivalId);
            if (daysAway == 0) {
                return name + " (今天)";
            } else if (daysAway == 1) {
                return name + " (明天)";
            } else {
                return name + " (" + daysAway + " 天后, " + date.getMonthValue()
                        + "/" + date.getDayOfMonth() + ")";
            }
        }
    }
}