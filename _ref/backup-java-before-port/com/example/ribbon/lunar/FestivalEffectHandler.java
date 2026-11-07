package com.example.ribbon.lunar;

import com.example.ribbon.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Random;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class FestivalEffectHandler {

    private static final Random RANDOM = new Random();
    private static int tickCounter = 0;

    private FestivalEffectHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.isFestivalEffectEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        tickCounter++;
        if (tickCounter < 4) return;
        tickCounter = 0;

        LunarDateInfo info = LunarDateProvider.today();
        if (info.festivalTier != LunarDateInfo.FestivalTier.GRAND
                && info.festivalTier != LunarDateInfo.FestivalTier.MAJOR) {
            return;
        }

        ParticleOptions particle = pickParticle(info);
        if (particle == null) return;

        Vec3 pos = mc.player.position();
        int count = info.festivalTier == LunarDateInfo.FestivalTier.GRAND ? 3 : 1;

        for (int i = 0; i < count; i++) {
            double dx = (RANDOM.nextDouble() - 0.5) * 6;
            double dy = RANDOM.nextDouble() * 2 + 0.5;
            double dz = (RANDOM.nextDouble() - 0.5) * 6;
            double vx = (RANDOM.nextDouble() - 0.5) * 0.05;
            double vy = 0.05 + RANDOM.nextDouble() * 0.05;
            double vz = (RANDOM.nextDouble() - 0.5) * 0.05;

            mc.level.addParticle(particle,
                    pos.x + dx, pos.y + dy, pos.z + dz,
                    vx, vy, vz);
        }
    }

    private static ParticleOptions pickParticle(LunarDateInfo info) {
        String id = info.festivalId;
        if (id == null) return null;

        return switch (id) {
            case "spring_festival", "chinese_new_year_eve" -> ParticleTypes.FIREWORK;
            case "mid_autumn" -> ParticleTypes.END_ROD;
            case "lantern_festival" -> ParticleTypes.FLAME;
            case "dragon_boat", "qixi" -> ParticleTypes.HAPPY_VILLAGER;
            case "qingming" -> ParticleTypes.CLOUD;
            case "double_ninth" -> ParticleTypes.CHERRY_LEAVES;
            case "us_independence", "us_thanksgiving", "us_christmas", "uk_christmas" -> ParticleTypes.FIREWORK;
            case "valentine" -> ParticleTypes.HEART;
            case "halloween" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "easter" -> ParticleTypes.HAPPY_VILLAGER;
            default -> null;
        };
    }
}