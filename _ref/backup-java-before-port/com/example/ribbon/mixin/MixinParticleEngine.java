package com.example.ribbon.mixin;

import com.example.ribbon.optimization.EntityCullingPolicy;
import com.example.ribbon.optimization.ParticleSpawnBudget;
import com.example.ribbon.optimization.RuntimeConfig;
import com.example.ribbon.render.ParticleCullingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在粒子「生成」阶段剔除远处粒子，而不是等到 render 阶段。
 *
 * <p>旧实现注入 {@code Particle.render} —— 那是抽象方法，注入无效且该 mixin 从未
 * 注册到 {@code ribbon.mixins.json}，等于整套粒子剔除都是死代码。改为注入
 * {@link ParticleEngine#add(Particle)}：被剔除的粒子连 tick / render 队列都进不去，
 * 收益比逐粒子渲染剔除大得多。</p>
 *
 * <p>{@code require = 0}：即使其它 mod 改写了 add 方法，也只是静默不生效，
 * 绝不会像之前那样造成 Critical injection failure。</p>
 */
@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void ribbon$cullParticleOnAdd(Particle particle, CallbackInfo ci) {
        boolean distanceEnabled = RuntimeConfig.particleCullingEnabled;
        boolean budgetEnabled = RuntimeConfig.particleSpawnBudgetEnabled;
        if (!distanceEnabled && !budgetEnabled) return;
        if (particle == null) return;

        // 预算限流放在最前面：粒子风暴时不必再为每个粒子算距离
        if (budgetEnabled && !ParticleSpawnBudget.tryAcquire()) {
            ci.cancel();
            return;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) return;

            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            if (camera == null) return;

            // Particle 的 x/y/z 是 protected，用已公开的包围盒中心取坐标（零额外分配）
            AABB box = particle.getBoundingBox();
            if (box == null) return;
            double px = (box.minX + box.maxX) * 0.5;
            double py = (box.minY + box.maxY) * 0.5;
            double pz = (box.minZ + box.maxZ) * 0.5;

            if (ParticleCullingManager.shouldCull(px, py, pz, camera.x, camera.y, camera.z)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // 保守放行：任何异常都不阻断原版粒子生成
        }
    }
}
