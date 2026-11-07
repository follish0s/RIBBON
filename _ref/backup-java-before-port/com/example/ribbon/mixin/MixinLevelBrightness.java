package com.example.ribbon.mixin;

import com.example.ribbon.Config;
import com.example.ribbon.IrisReflection;
import com.example.ribbon.dynamiclight.DynamicLightManager;
import com.example.ribbon.dynamiclight.DynamicLightState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 BlockAndTintGetter#getBrightness(LightLayer, BlockPos)。
 * 注意：getBrightness 定义在接口的 default 方法里，不在 Level 类中。
 */
@Mixin(BlockAndTintGetter.class)
public interface MixinLevelBrightness {

    @Inject(
            method = "getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"),
            cancellable = true
    )
    default void ribbon$applyDynamicLight(
            LightLayer lightLayer,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {

        if (!Config.isDynamicLightEnabled()) return;
        if (lightLayer != LightLayer.BLOCK) return;

        if (Config.isDynamicLightAutoDisableWithShaders()
                && IrisReflection.isIrisPresent()
                && IrisReflection.areShadersEnabled()) {
            return;
        }

        DynamicLightState state = DynamicLightManager.getState();
        if (!state.active) return;

        double dx = pos.getX() + 0.5 - state.x;
        double dy = pos.getY() + 0.5 - state.y;
        double dz = pos.getZ() + 0.5 - state.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        double radiusSq = DynamicLightManager.getCachedRadiusSq();
        if (distSq > radiusSq) return;

        double factor = 1.0 - distSq / radiusSq;
        int dynamicLight = (int) Math.round(state.brightness * factor);

        int original = cir.getReturnValue();
        if (dynamicLight > original) {
            cir.setReturnValue(dynamicLight);
        }
    }
}