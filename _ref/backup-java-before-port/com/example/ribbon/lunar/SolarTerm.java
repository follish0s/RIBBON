package com.example.ribbon.lunar;

import java.time.LocalDate;

/**
 * 24 节气计算。
 * 使用通用寿星公式的简化版本，精度到天，误差 ±1 天。
 */
public final class SolarTerm {

    private static final String[] NAMES = {
            "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
            "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
            "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
            "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    };

    /** 每个节气的基准日期（20 世纪，对应 1900 年的 Y*D+C 公式） */
    private static final double[] C_20TH = {
            6.11, 20.84, 4.6295, 19.4599, 6.3826, 21.4155,
            5.59, 20.888, 6.318, 21.86, 6.5, 22.20,
            7.928, 23.65, 8.35, 23.95, 8.44, 23.822,
            9.098, 24.218, 8.218, 23.08, 7.9, 22.60
    };

    /** 21 世纪对应的 C 值 */
    private static final double[] C_21TH = {
            5.4055, 20.12, 3.87, 18.73, 5.63, 20.646,
            4.81, 20.1, 5.52, 21.04, 5.678, 21.37,
            7.108, 22.83, 7.5, 23.13, 7.646, 23.042,
            8.318, 23.438, 7.438, 22.36, 7.18, 21.94
    };

    private SolarTerm() {}

    /**
     * 判断给定日期是否是某个节气。
     * 返回节气名，否则返回 null。
     */
    public static String getTerm(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if (year < 1900 || year > 2100) return null;

        int index = (month - 1) * 2;
        // 每月有两个节气：index 和 index + 1
        if (day == computeDay(year, index)) return NAMES[index];
        if (day == computeDay(year, index + 1)) return NAMES[index + 1];
        return null;
    }

    /**
     * 计算某年某个节气的公历日。
     */
    public static int computeDay(int year, int termIndex) {
        double c;
        int y;

        if (year >= 2000) {
            c = C_21TH[termIndex];
            y = year - 2000;
        } else {
            c = C_20TH[termIndex];
            y = year - 1900;
        }

        int term = termIndex / 2;       // 月份 0-11
        boolean isOdd = (termIndex % 2) != 0;

        // Y * D + C - L 的简化版
        double d = isOdd ? 0.5 : 0;
        int day = (int) Math.floor(y * 0.2422 + c) - (y / 4);
        // 修正：针对特殊年份的例外
        day += adjust(year, termIndex);

        return day;
    }

    /** 特殊年份的修正表 */
    private static int adjust(int year, int termIndex) {
        // 这些年份计算出的节气日期需要 ±1 天修正
        // 简化处理：只做最常见的几个修正
        if (year == 2084 && termIndex == 3) return 1;
        if (year == 1911 && termIndex == 4) return 1;
        if (year == 2008 && termIndex == 4) return 1;
        return 0;
    }

    /** 获取节气的中文名 */
    public static String name(int index) {
        return (index >= 0 && index < 24) ? NAMES[index] : null;
    }
}