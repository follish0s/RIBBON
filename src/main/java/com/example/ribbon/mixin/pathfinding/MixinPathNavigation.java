package com.example.ribbon.mixin.pathfinding;

import com.example.ribbon.pathfinding.AsyncPathfindingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
public abstract class MixinPathNavigation {

    @Shadow @Final protected Mob mob;

    @Shadow public abstract Path createPath(BlockPos target, int accuracy);

    // 1. 在寻路开始时，检查缓存
    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void ribbon$checkCacheFirst(BlockPos target, int accuracy, CallbackInfoReturnable<Path> cir) {
        AsyncPathfindingManager manager = AsyncPathfindingManager.getInstance();
        if (!manager.isEnabled()) return;

        // 如果命中缓存，直接返回，省掉主线程的 A* 计算
        Path cached = manager.getCachedPath(mob, target, accuracy);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }

        // 如果没命中，且该怪物是“高频寻路者”，就扔个任务去后台预热
        if (manager.markRequestAndCheckHot(mob)) {
            // 注意：这里在 HEAD 调用，此时还没生成快照，我们需要在 RETURN 时提交
            // 所以这里只做标记，真正的提交在下面的 RETURN 注入里
        }
    }

    // 2. 在寻路完成时，把结果存入缓存，并提交异步预热任务
    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void ribbon$onPathFound(BlockPos target, int accuracy, CallbackInfoReturnable<Path> cir) {
        AsyncPathfindingManager manager = AsyncPathfindingManager.getInstance();
        if (!manager.isEnabled()) return;

        Path result = cir.getReturnValue();
        if (result != null) {
            // 存入缓存
            manager.cachePath(mob, target, accuracy, result);
        }

        // 如果是高频寻路者，发起异步预计算
        if (manager.markRequestAndCheckHot(mob)) {
            manager.submitAsync(mob, (PathNavigation) (Object) this, target, accuracy);
        }
    }
}