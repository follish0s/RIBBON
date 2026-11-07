package com.example.ribbon.dynamiclight;

import com.example.ribbon.Config;
import com.example.ribbon.optimization.RuntimeConfig;
import com.example.ribbon.optimization.ShaderStateCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

public final class DynamicLightManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/DynamicLight");

    private static final DynamicLightState STATE = new DynamicLightState();

    private static float smoothedBrightness = 0f;
    private static double smoothedX = 0, smoothedY = 0, smoothedZ = 0;

    private static double cachedRadius = -1;
    private static double cachedRadiusSq = -1;

    private static int tickCounter = 0;
    private static boolean shadersActive = false;
    private static boolean loggedAutoDisable = false;

    /** 非方块光源物品 → 发光等级 */
    private static final Map<String, Integer> ITEM_LIGHT_MAP = Map.ofEntries(
            Map.entry("minecraft:lava_bucket", 15),
            Map.entry("minecraft:torch", 14),
            Map.entry("minecraft:soul_torch", 10),
            Map.entry("minecraft:lantern", 15),
            Map.entry("minecraft:soul_lantern", 10),
            Map.entry("minecraft:glow_berries", 14),
            Map.entry("minecraft:blaze_rod", 10),
            Map.entry("minecraft:blaze_powder", 8),
            Map.entry("minecraft:magma_cream", 6),
            Map.entry("minecraft:glow_ink_sac", 6),
            Map.entry("minecraft:glowstone_dust", 6),
            Map.entry("minecraft:spectral_arrow", 6)
    );

    /** Item → 光照等级缓存：注册表查询与 Identifier.toString() 只做一次。 */
    private static final Map<Item, Integer> ITEM_LIGHT_CACHE = new IdentityHashMap<>();
    private static final int ITEM_LIGHT_CACHE_MAX = 512;

    private DynamicLightManager() {}

    public static DynamicLightState getState() {
        return STATE;
    }

    public static void onClientTick() {
        if (!Config.isDynamicLightEnabled()) {
            STATE.clear();
            smoothedBrightness = 0f;
            return;
        }

        // 光影检测（短路）
        if (Config.isDynamicLightAutoDisableWithShaders() && ShaderStateCache.isIrisPresent()) {
            boolean current = ShaderStateCache.areShadersEnabled();
            if (current) {
                if (!shadersActive) {
                    STATE.clear();
                    smoothedBrightness = 0f;
                    if (!loggedAutoDisable) {
                        LOGGER.info("[Ribbon] 检测到光影已启用，动态光源自动禁用");
                        loggedAutoDisable = true;
                    }
                }
                shadersActive = true;
                return;
            } else {
                if (shadersActive) {
                    LOGGER.info("[Ribbon] 光影已关闭，动态光源恢复");
                    loggedAutoDisable = false;
                }
                shadersActive = false;
            }
        }

        tickCounter++;
        int interval = Config.getDynamicLightUpdateInterval();
        if (tickCounter < interval) return;
        tickCounter = 0;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            STATE.clear();
            smoothedBrightness = 0f;
            return;
        }

        int targetBrightness = computeBrightness(player);

        double tx = player.getX() + Config.getDynamicLightOffsetX();
        double ty = player.getY() + Config.getDynamicLightOffsetY();
        double tz = player.getZ() + Config.getDynamicLightOffsetZ();

        if (Config.isDynamicLightSmoothFade()) {
            float speed = (float) Config.getDynamicLightSmoothSpeed();
            smoothedBrightness = Mth.lerp(speed, smoothedBrightness, targetBrightness);
            smoothedX = Mth.lerp(speed, smoothedX, tx);
            smoothedY = Mth.lerp(speed, smoothedY, ty);
            smoothedZ = Mth.lerp(speed, smoothedZ, tz);
        } else {
            smoothedBrightness = targetBrightness;
            smoothedX = tx;
            smoothedY = ty;
            smoothedZ = tz;
        }

        if (smoothedBrightness <= 0.5f) {
            STATE.clear();
            return;
        }

        STATE.update(smoothedX, smoothedY, smoothedZ, Math.round(smoothedBrightness));
    }

    public static double getCachedRadius() {
        double r = Config.getDynamicLightRadius();
        if (r != cachedRadius) {
            cachedRadius = r;
            cachedRadiusSq = r * r;
        }
        return cachedRadius;
    }

    public static double getCachedRadiusSq() {
        getCachedRadius();
        return cachedRadiusSq;
    }

    private static int computeBrightness(Player player) {
        int max = 0;

        if (Config.isDynamicLightPlayerLight()) {
            max = Math.max(max, 8);
        }

        if (Config.isDynamicLightItemLight()) {
            int itemLight = getItemLightLevel(player);
            if (itemLight >= Config.getDynamicLightItemMinLight()) {
                max = Math.max(max, itemLight);
            }
        }

        return Math.min(max, Config.getDynamicLightIntensity());
    }

    private static int getItemLightLevel(Player player) {
        int m = getStackLight(player.getMainHandItem());
        int o = getStackLight(player.getOffhandItem());
        return Math.max(m, o);
    }

    private static int getStackLight(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        Item item = stack.getItem();
        if (RuntimeConfig.itemLightCacheEnabled) {
            Integer cached = ITEM_LIGHT_CACHE.get(item);
            if (cached != null) return cached;
        }

        int light = computeStackLight(item);

        if (RuntimeConfig.itemLightCacheEnabled) {
            if (ITEM_LIGHT_CACHE.size() >= ITEM_LIGHT_CACHE_MAX) {
                ITEM_LIGHT_CACHE.clear();
            }
            ITEM_LIGHT_CACHE.put(item, light);
        }
        return light;
    }

    private static int computeStackLight(Item item) {
        // 1) 方块类物品：查方块自身的光照
        Block b = Block.byItem(item);
        if (b != Blocks.AIR) {
            BlockState state = b.defaultBlockState();
            int light = state.getLightEmission();
            if (light > 0) return light;
        }

        // 2) 非方块物品：用注册名匹配白名单
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key != null) {
            Integer v = ITEM_LIGHT_MAP.get(key.toString());
            if (v != null) return v;
        }

        return 0;
    }

    public static void clear() {
        STATE.clear();
        smoothedBrightness = 0f;
    }
}