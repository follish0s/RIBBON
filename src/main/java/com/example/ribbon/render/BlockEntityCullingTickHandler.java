package com.example.ribbon.render;

import com.example.ribbon.ShadowRendererDetector;
import com.example.ribbon.optimization.CullingOptimizer;
import com.example.ribbon.optimization.RuntimeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT, modid = "ribbon")
public final class BlockEntityCullingTickHandler {

    /** 扫描半径（区块）。8 = 玩家周围 17×17 区块 */
    private static final int CHUNK_RADIUS = 8;
    /** 基础节流：每 3 tick 才考虑扫描一次（叠加静止降频后移动时为每 3 tick） */
    private static final int BASE_SCAN_INTERVAL = 3;

    /**
     * 复用集合：原来每 3 tick 都 new 一个 ArrayList 并装填 17×17 区块的全部方块实体，
     * 高峰期每次分配数千个元素、随后只消费前 8 个就丢弃，属于纯 GC 浪费。
     * 只在主线程访问（ClientTickEvent），复用安全。
     */
    private static final List<BlockEntity> SCAN_BUFFER = new ArrayList<>(512);

    private static int tickCounter = 0;

    private BlockEntityCullingTickHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!RuntimeConfig.blockEntityCullingEnabled) return;
        if (ShadowRendererDetector.isShadowPassActive()) return;

        tickCounter++;
        if (tickCounter < BASE_SCAN_INTERVAL) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        // 与实体剔除共用同一个幂等门控；相机静止时进一步降频
        if (!CullingOptimizer.shouldScan(cameraPos, level.getGameTime())) return;

        int centerX = mc.player.chunkPosition().x();
        int centerZ = mc.player.chunkPosition().z();

        SCAN_BUFFER.clear();
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz);
                if (chunk != null) {
                    SCAN_BUFFER.addAll(chunk.getBlockEntities().values());
                }
            }
        }

        BlockEntityCullingManager.submitBatch(SCAN_BUFFER, cameraPos, level);
    }
}
