package com.example.ribbon.lunar;

import java.time.LocalDate;

public final class EasterCalculator {
    private EasterCalculator() {}

    public static LocalDate getEasterDate(int year) {
        if (year < 1583 || year > 2100) return null;

        int a = year % 19;
        int b = year % 4;
        int c = year % 7;
        int k = year / 100;
        int p = (13 + 8 * k) / 25;
        int q = k / 4;
        int M = (15 - p + k - q) % 30;
        int N = (4 + k - q) % 7;
        int d = (19 * a + M) % 30;
        int e = (2 * b + 4 * c + 6 * d + N) % 7;

        if (d == 29 && e == 6) return LocalDate.of(year, 4, 19);
        if (d == 28 && e == 6 && (11 * M + 11) % 30 < 10) return LocalDate.of(year, 4, 18);

        int h = 22 + d + e;
        if (h <= 31) return LocalDate.of(year, 3, h);
        return LocalDate.of(year, 4, h - 31);
    }
}