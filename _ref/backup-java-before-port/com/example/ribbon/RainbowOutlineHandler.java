package com.example.ribbon;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class RainbowOutlineHandler {

    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        if (!Config.RAINBOW_BLOCK_OUTLINE.get()) return;
        event.setCanceled(true);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        Camera camera = event.getCamera();
        BlockHitResult target = event.getTarget();

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockState state = level.getBlockState(target.getBlockPos());
        VoxelShape shape = state.getShape(level, target.getBlockPos());
        if (shape.isEmpty()) return;

        // 3秒一个循环
        float time = (System.currentTimeMillis() % 3000) / 3000.0F;
        float r, g, b;

        switch (Config.OUTLINE_COLOR_MODE.get()) {
            case BLUE_PURPLE:
                r = Mth.sin(time * (float) Math.PI * 2) * 0.5F + 0.5F;
                g = 0.0F;
                b = 1.0F;
                break;
            case GREEN_PURPLE:
                r = Mth.sin(time * (float) Math.PI * 2) * 0.5F + 0.5F;
                g = Mth.sin((time + 1.0F / 3.0F) * (float) Math.PI * 2) * 0.5F + 0.5F;
                b = 1.0F - g;
                break;
            case BLUE_GREEN:
                // 蓝绿渐变：红=0，绿与蓝在 0-1 之间互补反向呼吸
                r = 0.0F;
                g = Mth.sin(time * (float) Math.PI * 2) * 0.5F + 0.5F;
                b = 1.0F - g;
                break;
            case PURPLE_YELLOW:
                // 紫黄渐变：红=1，绿与蓝互补（蓝=1绿=0是紫，蓝=0绿=1是黄）
                r = 1.0F;
                g = Mth.sin(time * (float) Math.PI * 2) * 0.5F + 0.5F;
                b = 1.0F - g;
                break;
            case PURPLE_GREEN_YELLOW:
                // 紫绿黄三色平滑循环
                float cycle = time * 3.0F; // 将 0~1 的 time 放大到 0~3
                int phase = (int) cycle % 3; // 当前处于哪个阶段（0, 1, 2）
                float t = cycle - (int) cycle; // 当前阶段的进度（0.0~1.0）
                float smoothT = (1.0F - Mth.cos(t * (float) Math.PI)) * 0.5F; // 平滑插值，防止突变

                float[] c1, c2;
                if (phase == 0) {
                    c1 = new float[]{0.8F, 0.2F, 1.0F}; // 紫
                    c2 = new float[]{0.2F, 1.0F, 0.2F}; // 绿
                } else if (phase == 1) {
                    c1 = new float[]{0.2F, 1.0F, 0.2F}; // 绿
                    c2 = new float[]{1.0F, 1.0F, 0.2F}; // 黄
                } else {
                    c1 = new float[]{1.0F, 1.0F, 0.2F}; // 黄
                    c2 = new float[]{0.8F, 0.2F, 1.0F}; // 紫
                }

                r = Mth.lerp(smoothT, c1[0], c2[0]);
                g = Mth.lerp(smoothT, c1[1], c2[1]);
                b = Mth.lerp(smoothT, c1[2], c2[2]);
                break;
            case RAINBOW:
            default:
                r = Mth.sin(time * (float) Math.PI * 2) * 0.5F + 0.5F;
                g = Mth.sin((time + 1.0F / 3.0F) * (float) Math.PI * 2) * 0.5F + 0.5F;
                b = Mth.sin((time + 2.0F / 3.0F) * (float) Math.PI * 2) * 0.5F + 0.5F;
                break;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableCull();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        Vec3 cameraPos = camera.getPosition();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        renderShape(poseStack, consumer, shape,
                target.getBlockPos().getX(), target.getBlockPos().getY(), target.getBlockPos().getZ(),
                r, g, b, 1.0F);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderShape(PoseStack poseStack, VertexConsumer consumer, VoxelShape shape,
                                    double x, double y, double z, float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float f = (float) (x1 + x);
            float f1 = (float) (y1 + y);
            float f2 = (float) (z1 + z);
            float f3 = (float) (x2 + x);
            float f4 = (float) (y2 + y);
            float f5 = (float) (z2 + z);

            VertexConsumer vc1 = consumer.addVertex(matrix, f, f1, f2);
            vc1.setColor(r, g, b, a);
            vc1.setNormal(1, 0, 0);

            VertexConsumer vc2 = consumer.addVertex(matrix, f3, f4, f5);
            vc2.setColor(r, g, b, a);
            vc2.setNormal(1, 0, 0);
        });
    }
}