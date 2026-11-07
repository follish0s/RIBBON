package com.example.ribbon.lunar.rules;

import com.example.ribbon.lunar.HolidayRule;
import com.example.ribbon.lunar.LunarDateInfo;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class NthWeekdayRule implements HolidayRule {
    private final String id;
    private final int month;
    private final DayOfWeek dayOfWeek;
    private final int ordinal; // 第几个，例如 4 表示第4个
    private final LunarDateInfo.FestivalTier tier;

    public NthWeekdayRule(String id, int month, DayOfWeek dayOfWeek, int ordinal,
                          LunarDateInfo.FestivalTier tier) {
        this.id = id;
        this.month = month;
        this.dayOfWeek = dayOfWeek;
        this.ordinal = ordinal;
        this.tier = tier;
    }

    @Override public String getId() { return id; }
    @Override public LunarDateInfo.FestivalTier getTier() { return tier; }

    @Override
    public boolean matches(LocalDate date, LunarDateInfo lunarInfo) {
        if (date.getMonthValue() != month) return false;
        // 使用 TemporalAdjusters 精确计算第 N 个星期几
        LocalDate target = date.with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek));
        return date.equals(target);
    }
}