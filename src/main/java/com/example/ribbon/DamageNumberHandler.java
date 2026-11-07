package com.example.ribbon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class DamageNumberHandler {

    private static final List<DamageNumber> DAMAGE_NUMBERS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    private static final int MAX_DAMAGE_NUMBERS = 30;

    private static final int COLOR_NORMAL = 0xFFFFFF;
    private static final int COLOR_FIRE = 0xFF4500;
    private static final int COLOR_FREEZE = 0x00FFFF;
    private static final int COLOR_LIGHTNING = 0xFFFF00;
    private static final int COLOR_MAGIC = 0x800080;
    private static final int COLOR_POISON = 0x00FF00;
    private static final int COLOR_WITHER = 0x555555;

    private static final Set<String> SPECIAL_WEAPONS = new HashSet<>();
    private static boolean initialized = false;

    // 🚨 新增：特殊武器伤害数字的生成频率限制（每秒 5 次 = 200毫秒 1 次）
    private static long lastSpecialNumberSpawnTime = 0;
    private static final long SPECIAL_NUMBER_COOLDOWN_MS = 200;

    private static void initSpecialWeapons() {
        if (initialized) return;
        initialized = true;

        if (ModList.get().isLoaded("avaritia")) {
            SPECIAL_WEAPONS.add("avaritia:infinity_sword");
            SPECIAL_WEAPONS.add("avaritia:infinity_axe");
            SPECIAL_WEAPONS.add("avaritia:infinity_bow");
            SPECIAL_WEAPONS.add("avaritia:infinity_trident");
        }
        if (ModList.get().isLoaded("manaita_plus") || ModList.get().isLoaded("manaita_plus_legacy")) {
            SPECIAL_WEAPONS.add("manaita_plus:manaita_sword");
            SPECIAL_WEAPONS.add("manaita_plus_legacy:manaita_sword");
            SPECIAL_WEAPONS.add("manaita_plus:manaita_axe");
        }
        if (ModList.get().isLoaded("lolipickaxe")) {
            SPECIAL_WEAPONS.add("lolipickaxe:loli_pickaxe");
            SPECIAL_WEAPONS.add("lolipickaxe:loli_sword");
        }
        if (ModList.get().isLoaded("projecte")) {
            SPECIAL_WEAPONS.add("projecte:red_matter_sword");
            SPECIAL_WEAPONS.add("projecte:red_matter_axe");
            SPECIAL_WEAPONS.add("projecte:red_katar");
            SPECIAL_WEAPONS.add("projecte:dark_matter_sword");
        }
        if (ModList.get().isLoaded("enigmaticlegacy")) {
            SPECIAL_WEAPONS.add("enigmaticlegacy:the_acknowledgment");
        }
        if (ModList.get().isLoaded("arcaneworld")) {
            SPECIAL_WEAPONS.add("arcaneworld:annivire");
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!Config.isDamageNumbersEnabled()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;

        initSpecialWeapons();

        ItemStack mainHand = player.getMainHandItem();
        boolean isSpecial = false;
        if (!mainHand.isEmpty()) {
            String id = mainHand.getItem().builtInRegistryHolder().key().location().toString();
            if (SPECIAL_WEAPONS.contains(id)) {
                isSpecial = true;
            }
        }

        float damage = event.getNewDamage();
        if (damage <= 0 && !isSpecial) return;
        if (isSpecial && damage <= 0) damage = Float.MAX_VALUE;

        long currentTime = System.currentTimeMillis();

        // 🚨 核心机制：如果是特殊武器，进行频率限制
        if (isSpecial) {
            // 如果距离上次生成还没到 200 毫秒，直接拦截，不生成新的 ∞
            if (currentTime - lastSpecialNumberSpawnTime < SPECIAL_NUMBER_COOLDOWN_MS) {
                return;
            }
            // 更新上次生成时间
            lastSpecialNumberSpawnTime = currentTime;
        }

        int color = isSpecial ? -1 : determineColor(event.getSource());
        LivingEntity target = event.getEntity();
        long mergeWindow = Config.getDamageNumberMergeWindow();

        DamageNumber existing = null;
        for (DamageNumber dn : DAMAGE_NUMBERS) {
            if (dn.targetId == target.getId() && currentTime - dn.spawnTime < mergeWindow) {
                existing = dn;
                break;
            }
        }

        if (existing != null) {
            if (isSpecial) {
                existing.damage = Float.MAX_VALUE;
                existing.isSpecial = true;
            } else if (!existing.isSpecial) {
                existing.damage += damage;
            }
            existing.spawnTime = currentTime;
            existing.triggerPop();
        } else {
            if (DAMAGE_NUMBERS.size() >= MAX_DAMAGE_NUMBERS) {
                DAMAGE_NUMBERS.remove(0);
            }

            double x = target.getX();
            double y = target.getY() + target.getBbHeight() + 0.3;
            double z = target.getZ();
            DAMAGE_NUMBERS.add(new DamageNumber(damage, color, x, y, z, currentTime, target.getId(), isSpecial));
        }
    }

    private static int determineColor(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return COLOR_FIRE;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return COLOR_LIGHTNING;
        if (source.is(DamageTypeTags.IS_FREEZING)) return COLOR_FREEZE;
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) return COLOR_WITHER;

        String msgId = source.getMsgId();
        if (msgId.equals("magic") || msgId.equals("indirectMagic") || msgId.equals("thrown")) return COLOR_MAGIC;
        if (msgId.equals("poison")) return COLOR_POISON;

        return COLOR_NORMAL;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!Config.isDamageNumbersEnabled()) return;

        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long currentTime = System.currentTimeMillis();
        long lifetime = Config.getDamageNumberLifetime();

        DAMAGE_NUMBERS.removeIf(dn -> currentTime - dn.spawnTime > lifetime);

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        for (DamageNumber dn : DAMAGE_NUMBERS) {
            dn.render(gui, mc, currentTime, screenWidth, screenHeight, lifetime);
        }
    }

    private static class DamageNumber {
        float damage;
        int color;
        double worldX, worldY, worldZ;
        long spawnTime;
        double offsetX, offsetZ;
        int targetId;
        boolean isSpecial;
        float popProgress;

        DamageNumber(float damage, int color, double worldX, double worldY, double worldZ, long spawnTime, int targetId, boolean isSpecial) {
            this.damage = damage;
            this.color = color;
            this.worldX = worldX;
            this.worldY = worldY;
            this.worldZ = worldZ;
            this.spawnTime = spawnTime;
            this.targetId = targetId;
            this.isSpecial = isSpecial;
            this.offsetX = (RANDOM.nextDouble() - 0.5) * 0.8;
            this.offsetZ = (RANDOM.nextDouble() - 0.5) * 0.8;
            this.popProgress = 0f;
        }

        void triggerPop() {
            this.popProgress = 0f;
        }

        void render(GuiGraphics gui, Minecraft mc, long currentTime, int screenWidth, int screenHeight, long lifetime) {
            float t = (currentTime - spawnTime) / 1000.0f;
            float lifeRatio = Math.min(t / (lifetime / 1000.0f), 1.0f);

            // 回归正常的弹出动画
            popProgress = Math.min((currentTime - spawnTime) / 200.0f, 1.0f);
            float popScale = 1.0f;
            if (popProgress < 1.0f) {
                popScale = 1.0f + 0.5f * (float) Math.sin(popProgress * Math.PI);
            }

            double initialVy = 1.5;
            double gravity = 4.0;
            double yOffset = initialVy * t - 0.5 * gravity * t * t;

            double currentX = worldX + offsetX;
            double currentY = worldY + yOffset;
            double currentZ = worldZ + offsetZ;

            var camera = mc.gameRenderer.getMainCamera();
            Vec3 camPos = camera.getPosition();
            float yawRad = (float) Math.toRadians(camera.getYRot());
            float pitchRad = (float) Math.toRadians(camera.getXRot());

            double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
            double fy = -Math.sin(pitchRad);
            double fz =  Math.cos(yawRad) * Math.cos(pitchRad);

            double rx = Math.cos(yawRad);
            double rz = Math.sin(yawRad);

            double ux = fy * rz;
            double uy = fz * rx - fx * rz;
            double uz = -fy * rx;

            double dx = currentX - camPos.x;
            double dy = currentY - camPos.y;
            double dz = currentZ - camPos.z;

            double forward = dx * fx + dy * fy + dz * fz;
            double rightC  = dx * rx + dz * rz;
            double upC     = dx * ux + dy * uy + dz * uz;

            if (forward < 0.5) return;

            double fovRad = Math.toRadians(mc.options.fov().get() / 2.0);
            double projScale = (screenHeight / 2.0) / Math.tan(fovRad);

            int screenX = (int) (screenWidth / 2.0 + (rightC / forward) * projScale);
            int screenY = (int) (screenHeight / 2.0 - (upC / forward) * projScale);

            float alpha = 1.0f - lifeRatio;
            if (alpha <= 0) return;

            int renderColor;
            if (isSpecial) {
                // 星空色闪烁
                long tick = currentTime / 60;
                Random starRand = new Random(spawnTime + tick);
                float randVal = starRand.nextFloat();

                int baseColor;
                if (randVal < 0.35f) baseColor = 0x00E5FF; // 霓虹亮青
                else if (randVal < 0.7f) baseColor = 0xFF00FF; // 亮紫红
                else baseColor = 0xFFFFFF; // 纯白

                renderColor = (baseColor & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
            } else {
                renderColor = color;
                renderColor = (renderColor & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
            }

            String text = formatDamage();
            float baseScale = (float) Config.getDamageNumberScale();
            float finalScale = baseScale * popScale * (isSpecial ? 1.2f : 1.0f);

            var pose = gui.pose();
            pose.pushPose();
            pose.translate(screenX, screenY, 0);
            pose.scale(finalScale, finalScale, 1.0f);
            gui.drawString(mc.font, text, 0, 0, renderColor, true);
            pose.popPose();
        }

        private String formatDamage() {
            if (isSpecial) return "∞";
            if (damage >= 99999F || Float.isInfinite(damage) || Float.isNaN(damage)) return "∞";
            if (damage >= 10000F) return String.format("%.1fW", damage / 10000F);
            if (damage == (int) damage) return String.valueOf((int) damage);
            else return String.format("%.1f", damage);
        }
    }
}