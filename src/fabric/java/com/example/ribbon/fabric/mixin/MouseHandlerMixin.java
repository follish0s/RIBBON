package com.example.ribbon.fabric.mixin;

import com.example.ribbon.ClickEffectHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric 侧鼠标按键入口：等价于 NeoForge 的 {@code InputEvent.MouseButton.Post}。
 *
 * <p>只声明在 ribbon-fabric.mixins.json（fabric.mod.json），NeoForge 不加载本类，
 * 因此不会与 NeoForge 侧的事件订阅双重生效。26.2 两个加载器都用官方映射，
 * 无需 refmap。</p>
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V", at = @At("TAIL"))
    private void ribbon$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        ClickEffectHandler.onMouseButton(action, info.input());
    }
}
