package com.example.ribbon.entityculling;

import com.example.ribbon.optimization.CullingOptimizer;
import com.example.ribbon.optimization.RuntimeConfig;
import com.example.ribbon.ShadowRendererDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * 每 tick 提交一批实体做遮挡检测。
 */
public final class EntityCullingTickHandler {

    private EntityCullingTickHandler() {}

    public static void onClientTick() {
        if (!RuntimeConfig.entityCullingEnabled) return;

        // 影子渲染阶段不提交任务
        if (ShadowRendererDetector.isShadowPassActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // F3 调试屏打开时不剔除，方便调试
        if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        // 相机静止时降频扫描，减少主线程 raycast；移动时立即恢复每 tick
        if (!CullingOptimizer.shouldScan(cameraPos, mc.level.getGameTime())) return;

        EntityCullingManager.submitBatch(mc.level.entitiesForRendering(), cameraPos, mc.level);
    }
}