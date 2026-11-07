package com.example.ribbon.lunar;

import java.time.LocalDate;

public interface HolidayRule {
    String getId();
    boolean matches(LocalDate date, LunarDateInfo lunarInfo);
    LunarDateInfo.FestivalTier getTier();
    /** 是否需要农历信息（大多数公历节日不需要） */
    default boolean needsLunar() { return false; }
}