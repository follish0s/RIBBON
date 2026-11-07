package com.example.ribbon.mixin;

import com.example.ribbon.ShadowRendererDetector;
import com.example.ribbon.entityculling.EntityCullingManager;
import com.example.ribbon.optimization.EntityCullingPolicy;
import com.example.ribbon.optimization.RuntimeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void ribbon$occlusionCulling(
            Entity entity,
            Frustum frustum,
            double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {

        // 视锥体结果优先，绝不覆盖
        Boolean frustumResult = cir.getReturnValue();
        if (frustumResult == null || !frustumResult) return;

        // 影子渲染阶段跳过所有剔除
        if (ShadowRendererDetector.isShadowPassActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entity == mc.player) return;

        // ===== 掉落物距离剔除（零分配：不 new Vec3，距离平方来自 volatile 快照） =====
        if (RuntimeConfig.itemEntityCullingEnabled && entity instanceof ItemEntity) {
            net.minecraft.world.phys.Vec3 pos = entity.position();
            double dx = pos.x - camX;
            double dy = pos.y - camY;
            double dz = pos.z - camZ;
            if (dx * dx + dy * dy + dz * dz > RuntimeConfig.itemEntityMaxDistanceSq) {
                cir.setReturnValue(false);
                return;
            }
        }

        // ===== 通用实体距离剔除（含重要实体白名单，高压时自动收紧） =====
        if (EntityCullingPolicy.shouldCullByDistance(entity, camX, camY, camZ)) {
            cir.setReturnValue(false);
            return;
        }

        // ===== 通用实体遮挡剔除（白名单实体不参与） =====
        if (!RuntimeConfig.entityCullingEnabled) return;
        if (EntityCullingPolicy.isProtected(entity)) return;
        if (!EntityCullingManager.isVisible(entity)) {
            cir.setReturnValue(false);
        }
    }
}