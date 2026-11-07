package com.example.ribbon.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public class RibbonCompatMixinPlugin implements IMixinConfigPlugin {

    // 从第一步诊断中提取的冲突 Mixin 全限定类名
    private static final Set<String> BLOCKED_MIXINS = new HashSet<>(Arrays.asList(
            "net.irisshaders.iris.mixin.sodium.MixinShaderChunkRenderer",
            "net.irisshaders.iris.mixin.sodium.MixinRenderSectionManager"
            // ... 根据实际诊断结果补充完整
    ));

    @Override
    public void onLoad(String mixinPackage) {
        // 可选：打印拦截日志便于调试
        System.out.println("[Ribbon Compat] Mixin defense plugin loaded. Blocking " + BLOCKED_MIXINS.size() + " incompatible mixins.");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (BLOCKED_MIXINS.contains(mixinClassName)) {
            System.out.println("[Ribbon Compat] BLOCKED incompatible mixin: " + mixinClassName + " -> " + targetClassName);
            return false;
        }
        return true;
    }

    // 以下方法保持默认空实现即可
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}