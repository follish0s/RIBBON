/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.optimization;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;

/**
 * 实体渲染剔除策略：距离分级 + 重要实体白名单 + 高压自适应收缩。
 *
 * <p>对标 SodiumExtras 的 Entity Distance Culling：用平方距离避免 sqrt，
 * 配置值走 {@link RuntimeConfig} 的 volatile 快照避免热路径查表，
 * 白名单先于距离判定（早退）。整条路径零对象分配。</p>
 *
 * <p><b>保守原则</b>：白名单内的实体一律渲染；异常、未识别实体一律放行。
 * 只在「视锥已判定可见」之后追加剔除，绝不改变原版视锥结果。</p>
 */
public final class EntityCullingPolicy {

    /** 高压时距离收缩到该比例（平方系数）。0.64 ≈ 距离 × 0.8。 */
    private static final double HIGH_PRESSURE_RANGE_SQ_SCALE = 0.64;

    private EntityCullingPolicy() {
    }

    /**
     * 重要实体白名单：这些对象被剔除会造成明显的功能/视觉错误。
     */
    public static boolean isProtected(Entity entity) {
        if (!RuntimeConfig.entityCullingWhitelistEnabled) return false;
        if (entity instanceof Player) return true;                 // 自己 / 其他玩家
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return true; // Boss
        if (entity.hasCustomName()) return true;                   // 命名牌实体
        if (entity.isVehicle() || entity.isPassenger()) return true; // 坐骑 / 载具上的实体
        if (entity instanceof LivingEntity living && living.isBaby()) return true; // 幼年生物
        return false;
    }

    /**
     * 距离剔除判定。
     *
     * @return true 表示应当跳过该实体的渲染
     */
    public static boolean shouldCullByDistance(Entity entity, double camX, double camY, double camZ) {
        if (!RuntimeConfig.entityDistanceCullingEnabled) return false;
        if (isProtected(entity)) return false;

        double maxDistSq = RuntimeConfig.entityDistanceCullingRangeSq;
        if (RuntimeConfig.adaptiveCullingDistanceEnabled
                && AdaptivePerformanceManager.isUnderHighPressure()) {
            maxDistSq *= HIGH_PRESSURE_RANGE_SQ_SCALE;
        }
        // 堆内存压力叠加收缩（EMA 平滑，不会与帧时间维度互相抖动）
        maxDistSq *= MemoryPressureManager.rangeScale();

        // 不调用 entity.position()（部分实现会 new Vec3），直接取坐标字段
        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        return dx * dx + dy * dy + dz * dz > maxDistSq;
    }

    /** 粒子剔除距离：高压时同样收紧，降低粒子风暴时的远处粒子数量。 */
    public static int effectiveParticleRangeSq() {
        int base = RuntimeConfig.particleCullingRangeSq;
        if (RuntimeConfig.adaptiveCullingDistanceEnabled
                && AdaptivePerformanceManager.isUnderHighPressure()) {
            base = (int) (base * HIGH_PRESSURE_RANGE_SQ_SCALE);
        }
        return (int) (base * MemoryPressureManager.rangeScale());
    }
}
