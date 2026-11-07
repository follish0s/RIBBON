package com.example.ribbon.lunar.rules;

import com.example.ribbon.lunar.EasterCalculator;
import com.example.ribbon.lunar.HolidayRule;
import com.example.ribbon.lunar.LunarDateInfo;
import java.time.LocalDate;

public class EasterBasedRule implements HolidayRule {
    private final String id;
    private final int offsetDays;
    private final LunarDateInfo.FestivalTier tier;

    public EasterBasedRule(String id, int offsetDays, LunarDateInfo.FestivalTier tier) {
        this.id = id;
        this.offsetDays = offsetDays;
        this.tier = tier;
    }

    @Override public String getId() { return id; }
    @Override public LunarDateInfo.FestivalTier getTier() { return tier; }

    @Override
    public boolean matches(LocalDate date, LunarDateInfo lunarInfo) {
        LocalDate easter = EasterCalculator.getEasterDate(date.getYear());
        if (easter == null) return false;
        return date.equals(easter.plusDays(offsetDays));
    }
}