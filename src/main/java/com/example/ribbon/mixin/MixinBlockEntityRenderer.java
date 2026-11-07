package com.example.ribbon.mixin;

import com.example.ribbon.ShadowRendererDetector;
import com.example.ribbon.render.BlockEntityCullingManager;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BlockEntityRenderer 是接口，mixin 必须声明为 interface，
 * 且注入方法必须是 default 方法。
 */
@Mixin(BlockEntityRenderer.class)
public interface MixinBlockEntityRenderer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    default void ribbon$cullBlockEntity(BlockEntity blockEntity, Vec3 cameraPos,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (ShadowRendererDetector.isShadowPassActive()) return;

        if (!BlockEntityCullingManager.isVisible(blockEntity)) {
            cir.setReturnValue(false);
        }
    }
}