package com.example.ribbon.compat;

/** 1.21.1 {@code com.example.ribbon.compat.FastColor.ARGB32/ABGR32} 在 26.2 已移除。 */
public final class FastColor {
    private FastColor() {
    }

    public static final class ABGR32 {
        private ABGR32() {
        }

        public static int color(int alpha, int red, int green, int blue) {
            return (alpha << 24) | (blue << 16) | (green << 8) | red;
        }

        public static int alpha(int value) {
            return value >>> 24;
        }

        public static int red(int value) {
            return value & 0xFF;
        }

        public static int green(int value) {
            return (value >>> 8) & 0xFF;
        }

        public static int blue(int value) {
            return (value >>> 16) & 0xFF;
        }
    }

    public static final class ARGB32 {
        private ARGB32() {
        }

        public static int color(int alpha, int red, int green, int blue) {
            return (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        public static int alpha(int value) {
            return value >>> 24;
        }

        public static int red(int value) {
            return (value >>> 16) & 0xFF;
        }

        public static int green(int value) {
            return (value >>> 8) & 0xFF;
        }

        public static int blue(int value) {
            return value & 0xFF;
        }
    }
}
