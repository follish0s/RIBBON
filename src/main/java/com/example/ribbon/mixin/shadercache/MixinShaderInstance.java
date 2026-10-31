package com.example.ribbon.mixin.shadercache;

import com.example.ribbon.shadercache.ShaderSourceCache;
import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(ShaderInstance.class)
public class MixinShaderInstance {

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private static void ribbon$cacheShaderSource(ResourceProvider resourceProvider, Program.Type type, String name, CallbackInfoReturnable<Program> cir) {
        // 由于 Iris 接管后，原版这里极少被调用。
        // 但如果是原版光影或部分兼容层，这里能起作用。
        // 由于 1.21.1 ShaderInstance 的底层改动，直接用源码替换风险极高，所以我们这里只做日志探测。
        // 真正的应用点在下方配置。
    }
}