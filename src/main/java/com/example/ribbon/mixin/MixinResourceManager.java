/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.Config;
import com.example.ribbon.util.TextureThreadPool;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ResourceManager.class)
public interface MixinResourceManager {

    /**
     * 这是一个安全的注入示例。在资源加载时如果开启了异步，就使用后台线程池。
     * Javadoc Note: This hook intercepts the file reading stage, NOT the GL upload stage.
     */
    // 注：1.21.1 中具体的 Resource 读取方法可能略有不同，这里展示思路，请根据实际编译报错微调方法名。
    // @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    // default void ribbon$asyncGetResource(CallbackInfoReturnable<...> cir) {
    //     if (!Config.isFastTextureLoadingEnabled()) return;
    //     if (TextureThreadPool.isMemoryPressureHigh()) return; // 内存压力大，走原版同步
    //     // 这里可以使用 CompletableFuture.supplyAsync(..., TextureThreadPool.get())
    // }
}