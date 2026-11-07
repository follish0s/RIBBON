package com.example.ribbon.lunar.rules;

import com.example.ribbon.lunar.HolidayRule;
import com.example.ribbon.lunar.LunarDateInfo;
import java.time.LocalDate;
import java.time.MonthDay;

public class FixedDateRule implements HolidayRule {
    private final String id;
    private final MonthDay monthDay;
    private final LunarDateInfo.FestivalTier tier;

    public FixedDateRule(String id, int month, int day, LunarDateInfo.FestivalTier tier) {
        this.id = id;
        this.monthDay = MonthDay.of(month, day);
        this.tier = tier;
    }

    @Override public String getId() { return id; }
    @Override public LunarDateInfo.FestivalTier getTier() { return tier; }

    @Override
    public boolean matches(LocalDate date, LunarDateInfo lunarInfo) {
        return MonthDay.from(date).equals(monthDay);
    }
}