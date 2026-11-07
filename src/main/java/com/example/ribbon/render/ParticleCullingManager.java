package com.example.ribbon.render;

import com.example.ribbon.optimization.EntityCullingPolicy;
import com.example.ribbon.optimization.RuntimeConfig;

/**
 * 粒子剔除判定。
 *
 * <p>只做距离平方比较，零对象分配 —— 这条路径每生成一个粒子都会走一次。
 * 距离阈值来自 {@link RuntimeConfig} 的 volatile 平方快照，避免热路径调用
 * {@code ModConfigSpec.get()} 与重复做乘法。</p>
 */
public final class ParticleCullingManager {

    private static volatile long culled = 0;

    private ParticleCullingManager() {}

    /**
     * @return true 表示应当丢弃该粒子
     */
    public static boolean shouldCull(double px, double py, double pz,
                                     double camX, double camY, double camZ) {
        if (!RuntimeConfig.particleCullingEnabled) return false;
        double dx = px - camX;
        double dy = py - camY;
        double dz = pz - camZ;
        if (dx * dx + dy * dy + dz * dz > EntityCullingPolicy.effectiveParticleRangeSq()) {
            culled++;
            return true;
        }
        return false;
    }

    public static long getCulledCount() {
        return culled;
    }
}
