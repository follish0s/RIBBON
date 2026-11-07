package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class HitParticleHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!Config.isHitParticlesEnabled()) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Entity target = event.getEntity();
        DamageSource source = event.getSource();

        double x = target.getX();
        double y = target.getY() + target.getBbHeight() * 0.5;
        double z = target.getZ();

        int intensity = Config.getHitParticleIntensity();

        // 暴击
        boolean critical = mc.player != null
                && mc.player.fallDistance > 0.0F
                && !mc.player.onGround();
        if (critical) {
            spawn(mc, ParticleTypes.CRIT, x, y, z, 4 * intensity, 0.3);
        }

        ParticleOptions type = pickParticle(source);
        if (type != null) {
            spawn(mc, type, x, y, z, 3 * intensity, 0.25);
        }
    }

    private static ParticleOptions pickParticle(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return ParticleTypes.FLAME;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return ParticleTypes.ELECTRIC_SPARK;

        String msgId = source.getMsgId();
        if (msgId.equals("magic") || msgId.equals("indirectMagic")) return ParticleTypes.WITCH;
        if (msgId.equals("thrown") || msgId.equals("arrow") || msgId.equals("trident"))
            return ParticleTypes.CRIT;
        if (msgId.equals("freeze") || msgId.equals("snowball")) return ParticleTypes.SNOWFLAKE;

        return ParticleTypes.DAMAGE_INDICATOR;
    }

    private static void spawn(Minecraft mc, ParticleOptions type,
                              double x, double y, double z, int count, double spread) {
        for (int i = 0; i < count; i++) {
            double vx = (mc.level.random.nextDouble() - 0.5) * spread;
            double vy = (mc.level.random.nextDouble() - 0.5) * spread + 0.1;
            double vz = (mc.level.random.nextDouble() - 0.5) * spread;
            mc.level.addParticle(type, x, y, z, vx, vy, vz);
        }
    }
}