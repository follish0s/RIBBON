package com.example.ribbon.dynamiclight;

/**
 * 动态光源当前状态。
 * 每 tick 由 DynamicLightManager 更新。
 */
public final class DynamicLightState {

    public volatile double x, y, z;
    public volatile int brightness;
    public volatile boolean active;

    public DynamicLightState() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.brightness = 0;
        this.active = false;
    }

    public void update(double x, double y, double z, int brightness) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.brightness = brightness;
        this.active = brightness > 0;
    }

    public void clear() {
        this.active = false;
        this.brightness = 0;
    }

    public void clearIfTooFar(double px, double py, double pz, double maxRange) {
        if (!active) return;
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        if (dx * dx + dy * dy + dz * dz > maxRange * maxRange) {
            clear();
        }
    }
}