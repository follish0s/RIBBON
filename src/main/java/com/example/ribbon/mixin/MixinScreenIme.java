/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.mixin;

import com.example.ribbon.ime.ImeCompatManager;
import com.example.ribbon.optimization.RuntimeConfig;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 全局输入法按键守卫：仅在「有输入框聚焦」时生效。
 *
 * <p>{@code require = 0}：注入失败只降级为功能无效，绝不阻塞启动（项目铁律：
 * 不新增硬失败 mixin）。关闭总开关后零行为变化。</p>
 */
@Mixin(Screen.class)
public class MixinScreenIme {

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void ribbon$imeGuard(int keyCode, int scanCode, int modifiers,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (!RuntimeConfig.imeFixEnabled) {
            return;
        }
        Screen self = (Screen) (Object) this;
        if (!ImeCompatManager.isTextFieldFocused(self.getFocused())) {
            return;
        }
        if (ImeCompatManager.shouldProtectKey(keyCode)
                || ImeCompatManager.shouldSwallowTypingKey(keyCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
