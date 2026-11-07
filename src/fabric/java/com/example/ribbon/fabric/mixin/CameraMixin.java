package com.example.ribbon.fabric.mixin;

import com.example.ribbon.ZoomHandler;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric 侧 FOV 缩放入口：注入到 {@code Camera#modifyFovBasedOnDeathOrFluid} 的 TAIL。
 *
 * <p>NeoForge 在该方法的末尾（patch 代码）调用 {@code ClientHooks.getFieldOfView}
 * 触发 ComputeFovModifierEvent —— 本注入点与之完全同一位置，世界 FOV 与 HUD FOV
 * 两条路径都会生效。只声明在 ribbon-fabric.mixins.json，NeoForge 不加载本类。</p>
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Inject(method = "modifyFovBasedOnDeathOrFluid(F)F", at = @At("TAIL"), cancellable = true)
    private void ribbon$adjustZoom(float fov, CallbackInfoReturnable<Float> cir) {
        Entity cameraEntity = ((Camera) (Object) this).entity();
        cir.setReturnValue(ZoomHandler.adjustFov(cir.getReturnValue(), cameraEntity));
    }
}
