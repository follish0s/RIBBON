package com.example.ribbon.render;

import com.example.ribbon.optimization.CullingOptimizer;
import com.example.ribbon.optimization.RuntimeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方块实体遮挡剔除管理器。
 * 主线程 raycast + 结果缓存，参考 EntityCullingManager 的设计。
 * 缓存有 TTL 清扫与硬上限，避免长时间游玩内存无界增长。
 */
public final class BlockEntityCullingManager {

    private static final Map<Long, CachedResult> CACHE = new ConcurrentHashMap<>();
    private static final int BASE_BATCH_SIZE = 8;
    private static final long CACHE_TTL_MS = 200;

    private BlockEntityCullingManager() {}

    public static void submitBatch(Iterable<BlockEntity> blockEntities, Vec3 cameraPos, Level level) {
        if (!RuntimeConfig.blockEntityCullingEnabled) {
            if (!CACHE.isEmpty()) CACHE.clear();
            return;
        }

        long now = System.currentTimeMillis();
        if (CullingOptimizer.shouldSweep()) sweepExpired(now);
        if (CACHE.size() > CullingOptimizer.maxCacheEntries()) CACHE.clear();

        double maxDistSq = RuntimeConfig.blockEntityCullingRangeSq;
        int batchSize = CullingOptimizer.scaledBatchSize(BASE_BATCH_SIZE);
        int processed = 0;

        for (BlockEntity be : blockEntities) {
            if (processed >= batchSize) break;
            if (be == null || be.isRemoved()) continue;

            long key = be.getBlockPos().asLong();
            CachedResult cached = CACHE.get(key);
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) continue;

            BlockPos pos = be.getBlockPos();
            // 零分配平方距离判定（不 new Vec3）
            double dx = (pos.getX() + 0.5) - cameraPos.x;
            double dy = (pos.getY() + 0.5) - cameraPos.y;
            double dz = (pos.getZ() + 0.5) - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
                CACHE.put(key, new CachedResult(true, now));
                continue;
            }

            Vec3 bePos = Vec3.atCenterOf(pos);
            boolean visible = traceVisibility(level, cameraPos, bePos);
            CACHE.put(key, new CachedResult(visible, now));
            processed++;
        }
    }

    /** 移除 TTL 过期条目。 */
    private static void sweepExpired(long now) {
        for (Map.Entry<Long, CachedResult> entry : CACHE.entrySet()) {
            if (now - entry.getValue().timestamp >= CACHE_TTL_MS) {
                CACHE.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public static boolean isVisible(BlockEntity be) {
        if (!RuntimeConfig.blockEntityCullingEnabled) return true;
        CachedResult cached = CACHE.get(be.getBlockPos().asLong());
        if (cached == null) return true;
        return cached.visible;
    }

    private static boolean traceVisibility(Level level, Vec3 cameraPos, Vec3 targetPos) {
        try {
            BlockHitResult hit = level.clip(new ClipContext(
                    cameraPos, targetPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));
            return hit.getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            return true;
        }
    }

    public static void onBlockEntityRemoved(BlockPos pos) {
        CACHE.remove(pos.asLong());
    }

    public static void clear() {
        CACHE.clear();
    }

    private record CachedResult(boolean visible, long timestamp) {}
}
