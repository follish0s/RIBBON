package com.example.ribbon.mixin;

import com.example.ribbon.Config;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SpriteLoader.class)
public class MixinSpriteLoader {

    @Inject(method = "loadAndStitch", at = @At("HEAD"))
    private static void ribbon$onStitch(ResourceManager resourceManager, ResourceLocation resourceLocation,
                                        int mipLevel, Executor executor,
                                        CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
        if (!Config.isFastTextureLoadingEnabled()) return;

        // 关键：只处理 "minecraft" 命名空间下的原版纹理，绝对不碰其他模组的，否则紫黑方块警告
        if (!resourceLocation.getNamespace().equals("minecraft")) {
            return;
        }

        if (Config.isTextureDebugLogEnabled()) {
            org.slf4j.LoggerFactory.getLogger("Ribbon/Stitch")
                    .info("[Ribbon] 开始异步拼贴原版贴图集: {} (Mipmap级别: {})", resourceLocation, mipLevel);
        }
    }
}