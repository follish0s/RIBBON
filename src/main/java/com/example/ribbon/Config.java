package com.example.ribbon;

import com.example.ribbon.optimization.RuntimeConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Ribbon.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_OVERLAY;
    public static final ModConfigSpec.EnumValue<OverlayCorner> OVERLAY_CORNER;
    public static final ModConfigSpec.DoubleValue OVERLAY_SCALE;

    public static final ModConfigSpec.BooleanValue RAINBOW_BLOCK_OUTLINE;
    public static final ModConfigSpec.EnumValue<OutlineColorMode> OUTLINE_COLOR_MODE;

    public static final ModConfigSpec.BooleanValue RAINBOW_CROSSHAIR;
    public static final ModConfigSpec.BooleanValue HOTBAR_HIGHLIGHT;
    public static final ModConfigSpec.BooleanValue DAMAGE_NUMBERS;
    public static final ModConfigSpec.DoubleValue DAMAGE_NUMBER_SCALE;
    public static final ModConfigSpec.IntValue DAMAGE_NUMBER_LIFETIME;
    public static final ModConfigSpec.IntValue DAMAGE_NUMBER_MERGE_WINDOW;

    public static final ModConfigSpec.BooleanValue BIOME_TITLE;
    public static final ModConfigSpec.IntValue BIOME_TITLE_DURATION;

    // ===== 液态玻璃 GUI 主题 =====
    public static final ModConfigSpec.BooleanValue LIQUID_GLASS;
    public static final ModConfigSpec.IntValue LIQUID_GLASS_BLUR;

    public static final ModConfigSpec.EnumValue<ClickEffectType> CLICK_EFFECT_TYPE;

    public static final ModConfigSpec.BooleanValue RAINBOW_HEALTH;
    public static final ModConfigSpec.EnumValue<HealthColorMode> HEALTH_COLOR_MODE;
    public static final ModConfigSpec.ConfigValue<String> HEALTH_CUSTOM_COLOR;
    public static final ModConfigSpec.DoubleValue HEALTH_RAINBOW_SPEED;
    public static final ModConfigSpec.BooleanValue HEALTH_LOW_SHAKE;
    public static final ModConfigSpec.IntValue HEALTH_LOW_SHAKE_THRESHOLD;

    public static final ModConfigSpec.BooleanValue ZOOM_ENABLED;
    public static final ModConfigSpec.DoubleValue ZOOM_FOV_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ZOOM_SMOOTH;

    public static final ModConfigSpec.EnumValue<Language> LANGUAGE;

    public static final ModConfigSpec.BooleanValue SHADER_COMPATIBILITY_WARNING;
    public static final ModConfigSpec.BooleanValue SHOW_ADVANCED_DEBUG;

    public static final ModConfigSpec.BooleanValue HIT_PARTICLES;
    public static final ModConfigSpec.IntValue HIT_PARTICLE_INTENSITY;

    public static final ModConfigSpec.BooleanValue CLICK_EFFECT_COVER_UI;
    public static final ModConfigSpec.BooleanValue CLICK_EFFECT_UI_ONLY_ON_WIDGETS;
    public static final ModConfigSpec.BooleanValue CLICK_EFFECT_MUTE_VANILLA;

    public static final ModConfigSpec.BooleanValue ENTITY_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue ENTITY_CULLING_RANGE;
    public static final ModConfigSpec.BooleanValue PARTICLE_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue PARTICLE_CULLING_RANGE;
    public static final ModConfigSpec.BooleanValue BLOCK_ENTITY_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue BLOCK_ENTITY_CULLING_RANGE;
    public static final ModConfigSpec.BooleanValue ITEM_ENTITY_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue ITEM_ENTITY_MAX_DISTANCE;

    // ===== 极速材质加载配置 =====
    public static final ModConfigSpec.BooleanValue FAST_TEXTURE_LOADING;
    public static final ModConfigSpec.IntValue TEXTURE_LOADING_THREADS;
    public static final ModConfigSpec.BooleanValue TEXTURE_DEBUG_LOG;

    // ===== 光影源码缓存（声明移到此处） =====
    public static final ModConfigSpec.BooleanValue SHADER_CACHE_ENABLED;

    // ===== 异步寻路配置 =====
    public static final ModConfigSpec.BooleanValue ASYNC_PATHFINDING_ENABLED;
    public static final ModConfigSpec.IntValue ASYNC_PATHFINDING_THREADS;
    public static final ModConfigSpec.IntValue ASYNC_PATHFINDING_CACHE_TICKS;
    public static final ModConfigSpec.IntValue ASYNC_PATHFINDING_HOT_THRESHOLD;
    public static final ModConfigSpec.IntValue ASYNC_PATHFINDING_MAX_IN_FLIGHT;
    public static final ModConfigSpec.IntValue ASYNC_PATHFINDING_SNAPSHOT_RANGE;

    public static final ModConfigSpec.BooleanValue LUNAR_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<OverlayCorner> LUNAR_HUD_CORNER;
    public static final ModConfigSpec.DoubleValue LUNAR_HUD_SCALE;
    public static final ModConfigSpec.BooleanValue LUNAR_SHOW_TERM;
    public static final ModConfigSpec.BooleanValue LUNAR_SHOW_FESTIVAL;
    public static final ModConfigSpec.BooleanValue LUNAR_FESTIVAL_TITLE;
    public static final ModConfigSpec.BooleanValue LUNAR_FESTIVAL_EFFECT;
    public static final ModConfigSpec.EnumValue<HolidayScope> LUNAR_HOLIDAY_SCOPE;
    public static final ModConfigSpec.BooleanValue LUNAR_FESTIVAL_BADGE;
    public static final ModConfigSpec.EnumValue<OverlayCorner> LUNAR_FESTIVAL_BADGE_CORNER;
    public static final ModConfigSpec.BooleanValue LUNAR_FESTIVAL_COUNTDOWN;

    public static final ModConfigSpec.BooleanValue LIGHT_LEVEL_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<OverlayCorner> LIGHT_LEVEL_HUD_CORNER;
    public static final ModConfigSpec.DoubleValue LIGHT_LEVEL_HUD_SCALE;

    // ===== 输入法（IME）兼容修复 =====
    public static final ModConfigSpec.BooleanValue IME_FIX_ENABLED;
    public static final ModConfigSpec.BooleanValue IME_COMPOSITION_GUARD;

    // ===== GUI 防抖 / 失焦暂停 =====
    public static final ModConfigSpec.BooleanValue GUI_SEARCH_DEBOUNCE_ENABLED;
    public static final ModConfigSpec.BooleanValue PAUSE_WHEN_UNFOCUSED;

    // ===== 悬浮层增强 / 药水计时 / 夜视 / 窗口标题 FPS =====
    public static final ModConfigSpec.BooleanValue OVERLAY_ENHANCED;
    public static final ModConfigSpec.BooleanValue POTION_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<OverlayCorner> POTION_HUD_CORNER;
    public static final ModConfigSpec.DoubleValue POTION_HUD_SCALE;
    public static final ModConfigSpec.BooleanValue NIGHT_VISION_ENABLED;
    public static final ModConfigSpec.IntValue NIGHT_VISION_STRENGTH;
    public static final ModConfigSpec.BooleanValue SHOW_FPS_IN_TITLE;

    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_ENABLED;
    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_AUTO_DISABLE_WITH_SHADERS;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_RADIUS;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_INTENSITY;
    public static final ModConfigSpec.DoubleValue DYNAMIC_LIGHT_OFFSET_X;
    public static final ModConfigSpec.DoubleValue DYNAMIC_LIGHT_OFFSET_Y;
    public static final ModConfigSpec.DoubleValue DYNAMIC_LIGHT_OFFSET_Z;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_UPDATE_INTERVAL;
    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_SMOOTH_FADE;
    public static final ModConfigSpec.DoubleValue DYNAMIC_LIGHT_SMOOTH_SPEED;
    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_PLAYER_LIGHT;
    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_ITEM_LIGHT;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_ITEM_MIN_LIGHT;

    public static final ModConfigSpec.BooleanValue MUSIC_PLAYER_ENABLED;
    public static final ModConfigSpec.BooleanValue MUSIC_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<OverlayCorner> MUSIC_HUD_CORNER;

    // ===== Info HUD =====
    public static final ModConfigSpec.BooleanValue INFO_HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<OverlayCorner> INFO_HUD_CORNER;
    public static final ModConfigSpec.DoubleValue INFO_HUD_SCALE;

    // ===== 内存深度优化系统 =====
    public static final ModConfigSpec.BooleanValue MEMORY_OPTIMIZATION_ENABLED;
    public static final ModConfigSpec.BooleanValue MEMORY_SHAPE_DEDUP;
    public static final ModConfigSpec.BooleanValue MEMORY_PROPERTY_MAP_DEDUP;
    public static final ModConfigSpec.BooleanValue MEMORY_MODEL_INTERNING;
    public static final ModConfigSpec.BooleanValue MEMORY_MULTIPART_PREDICATE;
    public static final ModConfigSpec.BooleanValue MEMORY_CHUNK_NBT_TRIM;
    public static final ModConfigSpec.BooleanValue MEMORY_SHOW_IN_HUD;

    // ===== 零 GC 战斗碰撞优化（对象池已并入 Vec3Pool/MixinEntityHit，无需独立开关） =====

    // ===== FSR =====
    // 用枚举取代原 FSR_ENABLED 布尔开关：OFF=关闭, FSR1=空间(1.0), FSR2=时域(2.2)
    public static final ModConfigSpec.EnumValue<FsrMode> FSR_MODE;
    public static final ModConfigSpec.DoubleValue FSR_RENDER_SCALE;
    public static final ModConfigSpec.DoubleValue FSR_SHARPNESS;
    public static final ModConfigSpec.DoubleValue FSR_HISTORY_WEIGHT;

    // ===== 实验性功能总闸 =====
    // 远景 LOD 已降级为实验性功能：默认关闭，且必须显式打开本总闸才会生效。
    public static final ModConfigSpec.BooleanValue EXPERIMENTAL_FEATURES_ENABLED;

    // ===== 自适应性能 / 剔除精简开销 =====
    public static final ModConfigSpec.BooleanValue ADAPTIVE_PERFORMANCE_ENABLED;
    public static final ModConfigSpec.IntValue ADAPTIVE_TARGET_FPS;
    public static final ModConfigSpec.BooleanValue SMART_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue CULLING_CACHE_MAX_ENTRIES;
    public static final ModConfigSpec.BooleanValue ENTITY_DISTANCE_CULLING_ENABLED;
    public static final ModConfigSpec.IntValue ENTITY_DISTANCE_CULLING_RANGE;
    public static final ModConfigSpec.BooleanValue ENTITY_CULLING_WHITELIST;
    public static final ModConfigSpec.BooleanValue PARTICLE_SPAWN_BUDGET_ENABLED;
    public static final ModConfigSpec.IntValue PARTICLE_SPAWN_BUDGET_PER_FRAME;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_CULLING_DISTANCE;

    // ===== HUD 节流 / 光影状态缓存 / 内存压力 / 物品光缓存 =====
    public static final ModConfigSpec.BooleanValue HUD_TEXT_CACHE_ENABLED;
    public static final ModConfigSpec.IntValue HUD_REFRESH_INTERVAL_MS;
    public static final ModConfigSpec.BooleanValue SHADER_STATE_CACHE_ENABLED;
    public static final ModConfigSpec.BooleanValue MEMORY_PRESSURE_ADAPTIVE;
    public static final ModConfigSpec.IntValue MEMORY_PRESSURE_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ITEM_LIGHT_CACHE_ENABLED;

    // ===== 远景 LOD（实验性，默认关闭） =====
    public static final ModConfigSpec.BooleanValue LOD_ENABLED;
    public static final ModConfigSpec.IntValue LOD_RADIUS_CHUNKS;
    public static final ModConfigSpec.IntValue LOD_WORKER_THREADS;
    public static final ModConfigSpec.IntValue LOD_REQUEST_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue LOD_WATER_SURFACE;
    public static final ModConfigSpec.BooleanValue LOD_FRUSTUM_CULLING;
    public static final ModConfigSpec.DoubleValue LOD_VERTICAL_BIAS;
    public static final ModConfigSpec.IntValue LOD_VIEW_DISTANCE_BLOCKS;
    public static final ModConfigSpec.EnumValue<LodQuality> LOD_QUALITY;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("fps_tps_overlay");
        SHOW_OVERLAY = BUILDER.comment("Show the FPS/TPS overlay on screen").define("showOverlay", true);
        OVERLAY_CORNER = BUILDER.comment("Which corner to draw the overlay in").defineEnum("overlayCorner", OverlayCorner.TOP_LEFT);
        OVERLAY_SCALE = BUILDER.comment("Text size multiplier").defineInRange("overlayScale", 1.0, 0.5, 3.0);
        OVERLAY_ENHANCED = BUILDER.comment("增强悬浮层：在 FPS/TPS 下方追加帧时间曲线、内存占用、实体/粒子数量",
                        "帧时间条：绿色 ≤17ms、橙色 ≤33ms、红色 >33ms（对应 60/30 FPS）")
                .define("overlayEnhanced", true);
        BUILDER.pop();

        BUILDER.push("potion_hud");
        POTION_HUD_ENABLED = BUILDER.comment("药水效果计时 HUD：屏幕角落显示当前效果与剩余时间",
                        "有益效果绿色、有害红色、中性黄色；F3 打开时自动隐藏")
                .define("enabled", true);
        POTION_HUD_CORNER = BUILDER.comment("药水 HUD 显示位置").defineEnum("corner", OverlayCorner.TOP_RIGHT);
        POTION_HUD_SCALE = BUILDER.comment("药水 HUD 缩放").defineInRange("scale", 1.0, 0.5, 3.0);
        BUILDER.pop();

        BUILDER.push("night_vision");
        NIGHT_VISION_ENABLED = BUILDER.comment("夜视增强（Fullbright）：提升光图亮度，黑暗环境不再伸手不见五指",
                        "非药水夜视：不改变游戏逻辑，只调亮画面；开启光影时由光影接管，本项可能无效果")
                .define("enabled", false);
        NIGHT_VISION_STRENGTH = BUILDER.comment("夜视强度（%）：100 = 完全夜视，80 = 推荐值").defineInRange("strength", 80, 10, 100);
        BUILDER.pop();

        BUILDER.push("music_player");
        MUSIC_PLAYER_ENABLED = BUILDER.comment("启用 MP3 音乐播放器", "音乐文件放在 .minecraft/ribbon_music/ 目录下").define("enabled", true);
        MUSIC_HUD_ENABLED = BUILDER.comment("在 HUD 上显示当前播放的音乐").define("showHud", true);
        MUSIC_HUD_CORNER = BUILDER.comment("音乐 HUD 显示位置").defineEnum("hudCorner", OverlayCorner.BOTTOM_RIGHT);
        BUILDER.pop();

        BUILDER.push("render_settings");
        RAINBOW_BLOCK_OUTLINE = BUILDER.comment("Enable custom block outline").define("rainbowBlockOutline", true);
        OUTLINE_COLOR_MODE = BUILDER.comment("The color mode of the block outline").defineEnum("outlineColorMode", OutlineColorMode.RAINBOW);
        BUILDER.pop();

        BUILDER.push("gui_settings");
        RAINBOW_CROSSHAIR = BUILDER.comment("Enable rainbow crosshair").define("rainbowCrosshair", false);
        HOTBAR_HIGHLIGHT = BUILDER.comment("Highlight the selected hotbar slot").define("hotbarHighlight", false);
        DAMAGE_NUMBERS = BUILDER.comment("Show floating damage numbers").define("damageNumbers", false);
        DAMAGE_NUMBER_SCALE = BUILDER.comment("Damage number size multiplier").defineInRange("damageNumberScale", 2.0, 0.5, 5.0);
        DAMAGE_NUMBER_LIFETIME = BUILDER.comment("How long damage numbers stay on screen (ms)").defineInRange("damageNumberLifetime", 1500, 500, 5000);
        DAMAGE_NUMBER_MERGE_WINDOW = BUILDER.comment("Merge hits within this time window (ms)").defineInRange("damageNumberMergeWindow", 1000, 100, 3000);
        BIOME_TITLE = BUILDER.comment("Show an RPG-style title when entering a new biome").define("biomeTitle", true);
        BIOME_TITLE_DURATION = BUILDER.comment("How long the biome title stays on screen (ms)").defineInRange("biomeTitleDuration", 3000, 1000, 10000);
        LIQUID_GLASS = BUILDER.comment("液态玻璃风格的视频设置界面",
                        "背景增强模糊 + 中性暗化 + 半透明圆角玻璃底板 + 漂移镜面高光",
                        "关闭后恢复 Embeddium 原版外观")
                .define("liquidGlass", true);
        LIQUID_GLASS_BLUR = BUILDER.comment("玻璃背景模糊强度（0=不模糊；≥6 时双重模糊增强毛玻璃感，上限 10）")
                .defineInRange("liquidGlassBlur", 8, 0, 10);
        CLICK_EFFECT_TYPE = BUILDER.comment("Which click effect to show").defineEnum("clickEffectType", ClickEffectType.RIPPLE);
        BUILDER.pop();

        BUILDER.push("rainbow_health");
        RAINBOW_HEALTH = BUILDER.comment("Render the player health bar in custom colors").define("enabled", false);
        HEALTH_COLOR_MODE = BUILDER.comment("Color mode: RAINBOW or CUSTOM").defineEnum("colorMode", HealthColorMode.RAINBOW);
        HEALTH_CUSTOM_COLOR = BUILDER.comment("Hex color for CUSTOM mode, e.g. #FF5555").define("customColor", "#FF5555");
        HEALTH_RAINBOW_SPEED = BUILDER.comment("Rainbow animation speed multiplier").defineInRange("rainbowSpeed", 1.0, 0.1, 5.0);
        HEALTH_LOW_SHAKE = BUILDER.comment("Shake the health bar when health is low").define("lowShake", true);
        HEALTH_LOW_SHAKE_THRESHOLD = BUILDER.comment("Health percentage below which shaking starts").defineInRange("lowShakeThreshold", 30, 1, 99);
        BUILDER.pop();

        BUILDER.push("zoom");
        ZOOM_ENABLED = BUILDER.comment("Enable OptiFine-style zoom").define("enabled", true);
        ZOOM_FOV_MULTIPLIER = BUILDER.comment("FOV multiplier while zooming").defineInRange("fovMultiplier", 0.25, 0.1, 0.9);
        ZOOM_SMOOTH = BUILDER.comment("Smoothly transition in/out of zoom").define("smooth", true);
        BUILDER.pop();

        BUILDER.push("misc");
        LANGUAGE = BUILDER.comment("Language used by the Ribbon GUI (AUTO follows the game language)").defineEnum("language", Language.AUTO);
        SHOW_FPS_IN_TITLE = BUILDER.comment("在窗口标题栏显示当前 FPS（离开世界时自动恢复原标题）").define("showFpsInTitle", true);
        SHADER_COMPATIBILITY_WARNING = BUILDER.comment("检测到不兼容光影时是否拦截并提示", "支持检测 Canvas 和 Vulkan 格式的光影包").define("shaderCompatibilityWarning", true);
        SHOW_ADVANCED_DEBUG = BUILDER.comment("Show advanced system and mod info in the F3 debug screen").define("showAdvancedDebug", true);
        BUILDER.pop();

        BUILDER.push("ime");
        IME_FIX_ENABLED = BUILDER.comment("输入法（IME）兼容修复",
                        "放行中文等全部可见 Unicode、合并补充平面字符的代理对（生僻字/emoji 不乱码）",
                        "输入框聚焦时吞掉拼音字母键，避免误触游戏全局快捷键与按钮")
                .define("imeFixEnabled", true);
        IME_COMPOSITION_GUARD = BUILDER.comment("组合输入保护",
                        "检测到 IME 信号后的短暂窗口内保护 ESC / ENTER / TAB / SPACE，避免选字时误关界面或误触发按钮",
                        "仅在真正出现非 ASCII 输入 / IME 伪按键时生效，正常按 ESC 退出界面不受影响")
                .define("imeCompositionGuard", true);
        BUILDER.pop();

        BUILDER.push("effects");
        HIT_PARTICLES = BUILDER.comment("Show extra particles when you hit an entity").define("hitParticles", true);        HIT_PARTICLE_INTENSITY = BUILDER.comment("Multiplier for the number of hit particles").defineInRange("hitParticleIntensity", 3, 1, 10);
        CLICK_EFFECT_COVER_UI = BUILDER.comment("GUI 内点击时也触发 Ribbon 特效").define("coverUi", true);
        CLICK_EFFECT_UI_ONLY_ON_WIDGETS = BUILDER.comment("仅在点击到按钮/控件时才触发特效").define("uiOnlyOnWidgets", false);
        CLICK_EFFECT_MUTE_VANILLA = BUILDER.comment("覆盖时取消原版按钮点击音效").define("muteVanilla", false);
        BUILDER.pop();

        BUILDER.push("performance");
        ENTITY_CULLING_ENABLED = BUILDER.comment("启用异步路径追踪实体剔除", "被方块遮挡的实体将跳过渲染").define("entityCullingEnabled", true);
        ENTITY_CULLING_RANGE = BUILDER.comment("实体剔除的最大检测距离（格）").defineInRange("entityCullingRange", 64, 16, 128);
        PARTICLE_CULLING_ENABLED = BUILDER.comment("剔除远处的粒子").define("particleCullingEnabled", true);
        PARTICLE_CULLING_RANGE = BUILDER.comment("粒子的最大渲染距离（格）").defineInRange("particleCullingRange", 32, 8, 64);
        BLOCK_ENTITY_CULLING_ENABLED = BUILDER.comment("剔除被方块遮挡的方块实体（箱子、熔炉等）").define("blockEntityCullingEnabled", true);
        BLOCK_ENTITY_CULLING_RANGE = BUILDER.comment("方块实体遮挡检测最大距离（格）").defineInRange("blockEntityCullingRange", 64, 16, 128);
        ITEM_ENTITY_CULLING_ENABLED = BUILDER.comment("剔除远处的掉落物").define("itemEntityCullingEnabled", true);
        ITEM_ENTITY_MAX_DISTANCE = BUILDER.comment("掉落物的最大渲染距离（格）").defineInRange("itemEntityMaxDistance", 64, 16, 128);

        FAST_TEXTURE_LOADING = BUILDER.comment("启用极速材质加载（异步解析）", "注意：可能与部分优化模组（如 ModernFix）冲突").define("fastTextureLoading", false);
        TEXTURE_LOADING_THREADS = BUILDER.comment("材质加载并发线程数", "过高可能导致内存溢出崩溃，建议 2-4").defineInRange("textureLoadingThreads", 2, 1, 8);
        TEXTURE_DEBUG_LOG = BUILDER.comment("输出材质加载调试日志（会刷屏）").define("textureDebugLog", false);

        // ===== 光影源码缓存（必须在 performance 块内、SPEC.build() 之前） =====
        SHADER_CACHE_ENABLED = BUILDER.comment("启用光影源码缓存",
                        "二次加载光影时会跳过源码解析，大幅减少卡顿")
                .define("shaderCacheEnabled", true);

        // 🚨 异步寻路配置
        ASYNC_PATHFINDING_ENABLED = BUILDER.comment("启用异步寻路预计算").define("asyncPathfinding", true);
        ASYNC_PATHFINDING_THREADS = BUILDER.comment("寻路线程数").defineInRange("asyncPathfindingThreads", 2, 1, 8);
        ASYNC_PATHFINDING_CACHE_TICKS = BUILDER.comment("缓存存活时间（tick）").defineInRange("asyncPathfindingCacheTicks", 40, 10, 200);
        ASYNC_PATHFINDING_HOT_THRESHOLD = BUILDER.comment("高频寻路判定阈值").defineInRange("asyncPathfindingHotThreshold", 3, 1, 20);
        ASYNC_PATHFINDING_MAX_IN_FLIGHT = BUILDER.comment("最大在途异步任务数").defineInRange("asyncPathfindingMaxInFlight", 100, 10, 500);
        ASYNC_PATHFINDING_SNAPSHOT_RANGE = BUILDER.comment("快照区域半径（区块）", "太高可能占用内存").defineInRange("asyncPathfindingSnapshotRange", 3, 1, 8);

        // ===== 新增低风险优化（默认开启，可单独关闭）=====
        HUD_TEXT_CACHE_ENABLED = BUILDER.comment("HUD 文本节流缓存",
                        "FPS/TPS、信息 HUD、药水 HUD 的文本按间隔重建，绘制仍每帧进行，减少每帧字符串/组件分配")
                .define("hudTextCacheEnabled", true);
        HUD_REFRESH_INTERVAL_MS = BUILDER.comment("HUD 文本刷新间隔（毫秒）；堆内存高压时自动加倍")
                .defineInRange("hudRefreshIntervalMs", 100, 50, 500);
        SHADER_STATE_CACHE_ENABLED = BUILDER.comment("缓存光影状态检测结果",
                        "避免动态光源每 tick、LOD 每帧反射查询 Iris；状态切换最多滞后 250ms")
                .define("shaderStateCacheEnabled", true);
        MEMORY_PRESSURE_ADAPTIVE = BUILDER.comment("堆内存压力自适应",
                        "堆占用超阈值时自动收紧粒子预算、实体/粒子剔除距离与 HUD 刷新频率，回落后自动放开")
                .define("memoryPressureAdaptive", true);
        MEMORY_PRESSURE_THRESHOLD = BUILDER.comment("触发内存压力的堆占用百分比").defineInRange("memoryPressureThreshold", 85, 50, 95);
        ITEM_LIGHT_CACHE_ENABLED = BUILDER.comment("缓存动态光源的手持物品光照等级",
                        "避免每轮更新重复查注册表与拼接 ResourceLocation 字符串")
                .define("itemLightCacheEnabled", true);
        GUI_SEARCH_DEBOUNCE_ENABLED = BUILDER.comment("设置界面搜索防抖",
                        "连续输入时合并 fuzzySearch 与界面重建，静默 80ms 后只重建一次")
                .define("guiSearchDebounceEnabled", true);
        PAUSE_WHEN_UNFOCUSED = BUILDER.comment("窗口失焦时暂停非必要的客户端后台工作",
                        "HUD 绘制、文本重建、内存压力采样在失焦时跳过，回焦立即恢复")
                .define("pauseWhenUnfocused", true);

        BUILDER.pop();

        BUILDER.push("lunar");
        LUNAR_HUD_ENABLED = BUILDER.comment("在 HUD 上显示农历日期").define("lunarHudEnabled", true);
        LUNAR_HUD_CORNER = BUILDER.comment("农历 HUD 显示位置").defineEnum("lunarHudCorner", OverlayCorner.BOTTOM_LEFT);
        LUNAR_HUD_SCALE = BUILDER.comment("农历 HUD 文字缩放").defineInRange("lunarHudScale", 1.0, 0.5, 3.0);
        LUNAR_SHOW_TERM = BUILDER.comment("显示 24 节气").define("showSolarTerm", true);
        LUNAR_SHOW_FESTIVAL = BUILDER.comment("显示传统节日").define("showFestival", true);
        LUNAR_FESTIVAL_TITLE = BUILDER.comment("节日当天进入世界时弹出标题").define("festivalTitle", true);
        LUNAR_FESTIVAL_EFFECT = BUILDER.comment("重大节日当天在玩家周围生成节日粒子").define("festivalEffect", true);
        LUNAR_HOLIDAY_SCOPE = BUILDER.comment("节日显示范围", "CHINA_ONLY = 仅中国节日", "INTERNATIONAL = 中国 + 国际节日", "ALL = 所有国家节日").defineEnum("holidayScope", HolidayScope.INTERNATIONAL);
        LUNAR_FESTIVAL_BADGE = BUILDER.comment("节日当天在屏幕角落显示物品图标徽章").define("festivalBadge", true);
        LUNAR_FESTIVAL_BADGE_CORNER = BUILDER.comment("节日徽章显示位置").defineEnum("festivalBadgeCorner", OverlayCorner.TOP_RIGHT);
        LUNAR_FESTIVAL_COUNTDOWN = BUILDER.comment("HUD 显示距离下一个节日还有几天").define("festivalCountdown", false);
        BUILDER.pop();

        BUILDER.push("hud_extras");
        LIGHT_LEVEL_HUD_ENABLED = BUILDER.comment("显示光照等级 HUD").define("lightLevelHudEnabled", false);
        LIGHT_LEVEL_HUD_CORNER = BUILDER.comment("光照等级 HUD 位置").defineEnum("lightLevelHudCorner", OverlayCorner.TOP_LEFT);
        LIGHT_LEVEL_HUD_SCALE = BUILDER.comment("光照等级 HUD 缩放").defineInRange("lightLevelHudScale", 1.0, 0.5, 3.0);
        BUILDER.pop();

        BUILDER.push("dynamic_light");
        DYNAMIC_LIGHT_ENABLED = BUILDER.comment("启用动态光源").define("enabled", true);
        DYNAMIC_LIGHT_AUTO_DISABLE_WITH_SHADERS = BUILDER.comment("开启光影时自动禁用动态光源").define("autoDisableWithShaders", true);
        DYNAMIC_LIGHT_RADIUS = BUILDER.comment("光源半径（格）").defineInRange("radius", 12, 4, 24);
        DYNAMIC_LIGHT_INTENSITY = BUILDER.comment("最大亮度等级").defineInRange("intensity", 14, 1, 15);
        DYNAMIC_LIGHT_OFFSET_X = BUILDER.comment("X 偏移").defineInRange("offsetX", 0.0, -4.0, 4.0);
        DYNAMIC_LIGHT_OFFSET_Y = BUILDER.comment("Y 偏移").defineInRange("offsetY", 1.0, -4.0, 4.0);
        DYNAMIC_LIGHT_OFFSET_Z = BUILDER.comment("Z 偏移").defineInRange("offsetZ", 0.0, -4.0, 4.0);
        DYNAMIC_LIGHT_UPDATE_INTERVAL = BUILDER.comment("更新频率（tick）").defineInRange("updateInterval", 2, 1, 20);
        DYNAMIC_LIGHT_SMOOTH_FADE = BUILDER.comment("平滑过渡").define("smoothFade", true);
        DYNAMIC_LIGHT_SMOOTH_SPEED = BUILDER.comment("平滑速度").defineInRange("smoothSpeed", 0.25, 0.05, 1.0);
        DYNAMIC_LIGHT_PLAYER_LIGHT = BUILDER.comment("玩家自身发光").define("playerLight", false);
        DYNAMIC_LIGHT_ITEM_LIGHT = BUILDER.comment("手持物品发光").define("itemLight", true);
        DYNAMIC_LIGHT_ITEM_MIN_LIGHT = BUILDER.comment("最低物品发光阈值").defineInRange("itemMinLight", 8, 1, 15);
        BUILDER.pop();

        BUILDER.push("info_hud");
        INFO_HUD_ENABLED = BUILDER.comment("启用综合信息 HUD（坐标/群系/CPS/装备耐久）").define("enabled", false);
        INFO_HUD_CORNER = BUILDER.comment("信息 HUD 显示位置").defineEnum("corner", OverlayCorner.TOP_LEFT);
        INFO_HUD_SCALE = BUILDER.comment("信息 HUD 文字缩放").defineInRange("scale", 1.0, 0.5, 3.0);
        BUILDER.pop();

        BUILDER.push("memory");
        MEMORY_OPTIMIZATION_ENABLED = BUILDER.comment("启用内存深度优化系统（对标 FerriteCore）", "通过 Mixin 注入对形状、属性表、模型定位符、多部件谓词做全局去重").define("enabled", true);
        MEMORY_SHAPE_DEDUP = BUILDER.comment("碰撞箱/渲染形状全局去重").define("shapeDedup", true);
        MEMORY_PROPERTY_MAP_DEDUP = BUILDER.comment("BlockState 属性表去重").define("propertyMapDedup", true);
        MEMORY_MODEL_INTERNING = BUILDER.comment("ModelResourceLocation 字符串驻留").define("modelInterning", true);
        MEMORY_MULTIPART_PREDICATE = BUILDER.comment("多部件模型谓词缓存").define("multipartPredicate", true);
        MEMORY_CHUNK_NBT_TRIM = BUILDER.comment("区块加载后清空待处理方块实体 NBT (高风险，默认关闭)").define("chunkNbtTrim", false);
        MEMORY_SHOW_IN_HUD = BUILDER.comment("在综合信息 HUD 中显示内存优化统计").define("showInHud", true);
        BUILDER.pop();

        BUILDER.push("fsr");
        FSR_MODE = BUILDER.comment("FSR 超分辨率模式", "OFF = 关闭", "FSR1 = AMD FSR 1.0（EASU 空间上采样 + RCAS 锐化）", "FSR2 = FSR 2.2 时域管线（需计算运动矢量，需 OpenGL 4.6+）").defineEnum("mode", FsrMode.OFF);
        FSR_RENDER_SCALE = BUILDER.comment("内部渲染分辨率缩放比例").defineInRange("renderScale", 0.75, 0.5, 1.0);
        FSR_SHARPNESS = BUILDER.comment("RCAS 锐化强度（FSR1/FSR2 通用）").defineInRange("sharpness", 0.2, 0.0, 1.0);
        FSR_HISTORY_WEIGHT = BUILDER.comment("FSR 2.2 历史帧累积权重", "越高越平滑稳定（推荐 0.9），过低会闪烁、过高会拖影").defineInRange("historyWeight", 0.9, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("optimization");

        // ===== 自适应性能调度（默认开启，纯削峰不改画质） =====
        ADAPTIVE_PERFORMANCE_ENABLED = BUILDER.comment("帧时间驱动的自适应负载调度：掉帧时临时收紧区块重建/剔除批处理预算，帧率恢复后自动放开",
                        "不改变渲染质量，只削掉区块大面积更新时的帧时间尖峰")
                .define("adaptivePerformance", true);
        ADAPTIVE_TARGET_FPS = BUILDER.comment("自适应调度的目标帧率，实际帧时间超过该值对应的预算时开始收紧",
                        "建议设为显示器刷新率或略低")
                .defineInRange("adaptiveTargetFps", 60, 30, 240);

        // ===== 剔除精简开销（默认开启） =====
        SMART_CULLING_ENABLED = BUILDER.comment("相机静止时降低遮挡检测扫描频率，并定期清理剔除缓存",
                        "减少主线程 raycast 与 ConcurrentHashMap 长期增长带来的内存占用")
                .define("smartCulling", true);
        CULLING_CACHE_MAX_ENTRIES = BUILDER.comment("实体/方块实体剔除缓存的条目硬上限，超出时清空重建")
                .defineInRange("cullingCacheMaxEntries", 4096, 256, 65536);
        // 粒子剔除复用已有的 particleCullingEnabled / particleCullingRange 开关，
        // 只是生效点从「渲染期」前移到「生成期」，无需新增重复配置项。

        // ===== 实体渲染距离分级剔除 =====
        ENTITY_DISTANCE_CULLING_ENABLED = BUILDER.comment("按实体到相机的距离剔除远处实体渲染",
                        "与遮挡剔除互补：遮挡剔除处理被方块挡住的，距离剔除处理视野内但太远的")
                .define("entityDistanceCulling", true);
        ENTITY_DISTANCE_CULLING_RANGE = BUILDER.comment("实体最大渲染距离（格）",
                        "超过该距离的普通实体直接跳过渲染；重要实体（Boss/命名/载具/玩家）永不剔除")
                .defineInRange("entityDistanceCullingRange", 96, 32, 256);
        ENTITY_CULLING_WHITELIST = BUILDER.comment("重要实体白名单：Boss、命名实体、载具/坐骑、玩家一律不剔除",
                        "避免把末影龙、拴绳动物、坐船玩家等关键对象误剔除")
                .define("entityCullingWhitelist", true);

        // ===== 粒子生成预算 =====
        PARTICLE_SPAWN_BUDGET_ENABLED = BUILDER.comment("限制每帧可生成的粒子数",
                        "防止爆炸/大量方块破坏等粒子风暴打穿帧率；超出预算的粒子在生成阶段直接丢弃")
                .define("particleSpawnBudget", true);
        PARTICLE_SPAWN_BUDGET_PER_FRAME = BUILDER.comment("每帧粒子生成上限",
                        "原版上限是 ParticleEngine 的全局限制，这里做更保守的前置限流")
                .defineInRange("particleSpawnBudgetPerFrame", 512, 64, 4096);

        // ===== 高压时剔除距离自适应 =====
        ADAPTIVE_CULLING_DISTANCE = BUILDER.comment("帧时间高压时自动收紧实体/粒子剔除距离",
                        "帧率恢复后自动放开，只在卡顿时临时降低远处对象数量")
                .define("adaptiveCullingDistance", true);

        BUILDER.pop();

        BUILDER.push("experimental");
        EXPERIMENTAL_FEATURES_ENABLED = BUILDER.comment("实验性功能总闸（默认关闭）",
                        "远景 LOD 等尚未稳定的功能必须先打开本开关才会生效",
                        "关闭时实验性功能完全不启动：不注册 shader、不起后台线程、不读 .mca")
                .define("enabled", false);
        BUILDER.pop();

        BUILDER.push("lod");
        LOD_ENABLED = BUILDER.comment("【实验性·默认关闭】启用远景 LOD",
                        "需同时开启 实验性功能总闸（experimental.enabled）才会生效",
                        "已知限制：不接入 Iris/Neoculus 的 G-Buffer，光影下远景不接受延迟光照；与近景衔接仍可能有偏差",
                        "直接读取存档 .mca 的高度图生成远景地形，不 new LevelChunk、不碰原版区块管线")
                .define("enabled", false);
        LOD_RADIUS_CHUNKS = BUILDER.comment("LOD 覆盖半径（区块）", "玩家周围该半径范围内、且超出原版区块渲染范围的远景区域会被 LOD 接管").defineInRange("radiusChunks", 8, 2, 1024);
        LOD_WORKER_THREADS = BUILDER.comment("LOD 构建线程数", "0 = 自动（CPU 核心数 - 2，至少 1）").defineInRange("workerThreads", 0, 0, 16);
        LOD_REQUEST_INTERVAL_TICKS = BUILDER.comment("LOD 请求节流间隔（tick）", "每 N tick 重新计算一次玩家周围需要构建的 LOD 区块集合").defineInRange("requestIntervalTicks", 5, 1, 100);
        LOD_WATER_SURFACE = BUILDER.comment("渲染水面近似", "把高度低于海平面(63)的区块按水面着色渲染（近似，不含半透明排序）").define("waterSurface", true);
        LOD_FRUSTUM_CULLING = BUILDER.comment("LOD 视锥剔除", "提交前用原版视锥剔除不可见的 LOD 网格").define("frustumCulling", true);
        LOD_VERTICAL_BIAS = BUILDER.comment("LOD 垂直偏移（格）", "正值整体抬高、负值下压，用于对齐远景与近景地形").defineInRange("verticalBias", 0.0, -16.0, 16.0);
        LOD_VIEW_DISTANCE_BLOCKS = BUILDER.comment("LOD 视距上限（格）", "超出该距离的 LOD 区块在提交前直接跳过（CPU 侧远切），避免被 GPU Far Plane 硬件裁剪导致地平线硬切/穿插").defineInRange("viewDistanceBlocks", 4096, 256, 65536);
        LOD_QUALITY = BUILDER.comment("LOD 配色质量",
                        "LOW = 纯色顶点色（最省，远景为纯 MapColor 平涂）",
                        "MEDIUM = 纯色 + 方向光照（顶面亮/侧面暗，有立体感，默认）",
                        "HIGH = 采样原版 terrain atlas 纹理（糊化版），颜色与原版一致")
                .defineEnum("quality", LodQuality.MEDIUM);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 配置就绪/重载后刷新热路径快照（SPEC.save() 不会触发本事件，GUI 保存由 RibbonOptions 显式刷新）
        RuntimeConfig.refresh();
        // 配置值已可用后才做设置页自检：更早调用会因 ConfigValue.get() 未就绪而误报
        if (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading) {
            RibbonOptions.selfCheck();
        }
    }

    // ================= 各种 get 方法 =================
    public static boolean isShaderCompatibilityWarningEnabled() { try { return SHADER_COMPATIBILITY_WARNING.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isShowAdvancedDebug() { try { return SHOW_ADVANCED_DEBUG.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isHitParticlesEnabled() { try { return HIT_PARTICLES.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isClickEffectCoverUi() { try { return CLICK_EFFECT_COVER_UI.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isClickEffectUiOnlyOnWidgets() { try { return CLICK_EFFECT_UI_ONLY_ON_WIDGETS.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean isClickEffectMuteVanilla() { try { return CLICK_EFFECT_MUTE_VANILLA.get(); } catch (IllegalStateException e) { return false; } }

    public static boolean isLiquidGlassEnabled() { try { return LIQUID_GLASS.get(); } catch (IllegalStateException e) { return true; } }
    public static int getLiquidGlassBlur() { try { return LIQUID_GLASS_BLUR.get(); } catch (IllegalStateException e) { return 8; } }

    public static boolean isOverlayEnhanced() { try { return OVERLAY_ENHANCED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isPotionHudEnabled() { try { return POTION_HUD_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static OverlayCorner getPotionHudCorner() { try { return POTION_HUD_CORNER.get(); } catch (IllegalStateException e) { return OverlayCorner.TOP_RIGHT; } }
    public static double getPotionHudScale() { try { return POTION_HUD_SCALE.get(); } catch (IllegalStateException e) { return 1.0; } }
    public static boolean isNightVisionEnabled() { try { return NIGHT_VISION_ENABLED.get(); } catch (IllegalStateException e) { return false; } }
    public static int getNightVisionStrength() { try { return NIGHT_VISION_STRENGTH.get(); } catch (IllegalStateException e) { return 80; } }
    public static boolean isShowFpsInTitle() { try { return SHOW_FPS_IN_TITLE.get(); } catch (IllegalStateException e) { return true; } }

    public static boolean isHudTextCacheEnabled() { try { return HUD_TEXT_CACHE_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getHudRefreshIntervalMs() { try { return HUD_REFRESH_INTERVAL_MS.get(); } catch (IllegalStateException e) { return 100; } }
    public static boolean isShaderStateCacheEnabled() { try { return SHADER_STATE_CACHE_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isMemoryPressureAdaptive() { try { return MEMORY_PRESSURE_ADAPTIVE.get(); } catch (IllegalStateException e) { return true; } }
    public static int getMemoryPressureThreshold() { try { return MEMORY_PRESSURE_THRESHOLD.get(); } catch (IllegalStateException e) { return 85; } }
    public static boolean isItemLightCacheEnabled() { try { return ITEM_LIGHT_CACHE_ENABLED.get(); } catch (IllegalStateException e) { return true; } }

    public static boolean isImeFixEnabled() { try { return IME_FIX_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isImeCompositionGuardEnabled() { try { return IME_COMPOSITION_GUARD.get(); } catch (IllegalStateException e) { return true; } }

    public static boolean isGuiSearchDebounceEnabled() { try { return GUI_SEARCH_DEBOUNCE_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isPauseWhenUnfocused() { try { return PAUSE_WHEN_UNFOCUSED.get(); } catch (IllegalStateException e) { return true; } }

    public static ClickEffectType getClickEffectType() {
        try { return CLICK_EFFECT_TYPE.get(); }
        catch (IllegalStateException e) { return ClickEffectType.RIPPLE; }
    }

    public static boolean isEntityCullingEnabled() { try { return ENTITY_CULLING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getEntityCullingRange() { try { return ENTITY_CULLING_RANGE.get(); } catch (IllegalStateException e) { return 64; } }
    public static boolean isParticleCullingEnabled() { try { return PARTICLE_CULLING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getParticleCullingRange() { try { return PARTICLE_CULLING_RANGE.get(); } catch (IllegalStateException e) { return 32; } }
    public static boolean isBlockEntityCullingEnabled() { try { return BLOCK_ENTITY_CULLING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getBlockEntityCullingRange() { try { return BLOCK_ENTITY_CULLING_RANGE.get(); } catch (IllegalStateException e) { return 64; } }
    public static boolean isItemEntityCullingEnabled() { try { return ITEM_ENTITY_CULLING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getItemEntityMaxDistance() { try { return ITEM_ENTITY_MAX_DISTANCE.get(); } catch (IllegalStateException e) { return 64; } }

    public static boolean isFastTextureLoadingEnabled() { try { return FAST_TEXTURE_LOADING.get(); } catch (IllegalStateException e) { return false; } }
    public static int getTextureLoadingThreads() { try { return TEXTURE_LOADING_THREADS.get(); } catch (IllegalStateException e) { return 2; } }
    public static boolean isTextureDebugLogEnabled() { try { return TEXTURE_DEBUG_LOG.get(); } catch (IllegalStateException e) { return false; } }

    // ===== 光影源码缓存 Getter =====
    public static boolean isShaderCacheEnabled() { try { return SHADER_CACHE_ENABLED.get(); } catch (IllegalStateException e) { return true; } }

    // ===== 异步寻路 Getter =====
    public static boolean isAsyncPathfindingEnabled() { try { return ASYNC_PATHFINDING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getAsyncPathfindingThreads() { try { return ASYNC_PATHFINDING_THREADS.get(); } catch (IllegalStateException e) { return 2; } }
    public static int getAsyncPathfindingCacheTicks() { try { return ASYNC_PATHFINDING_CACHE_TICKS.get(); } catch (IllegalStateException e) { return 40; } }
    public static int getAsyncPathfindingHotThreshold() { try { return ASYNC_PATHFINDING_HOT_THRESHOLD.get(); } catch (IllegalStateException e) { return 3; } }
    public static int getAsyncPathfindingMaxInFlight() { try { return ASYNC_PATHFINDING_MAX_IN_FLIGHT.get(); } catch (IllegalStateException e) { return 100; } }
    public static int getAsyncPathfindingSnapshotRange() { try { return ASYNC_PATHFINDING_SNAPSHOT_RANGE.get(); } catch (IllegalStateException e) { return 3; } }

    public static boolean isLunarHudEnabled() { try { return LUNAR_HUD_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static OverlayCorner getLunarHudCorner() { try { return LUNAR_HUD_CORNER.get(); } catch (IllegalStateException e) { return OverlayCorner.BOTTOM_LEFT; } }
    public static double getLunarHudScale() { try { return LUNAR_HUD_SCALE.get(); } catch (IllegalStateException e) { return 1.0; } }
    public static boolean isShowSolarTerm() { try { return LUNAR_SHOW_TERM.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isShowFestival() { try { return LUNAR_SHOW_FESTIVAL.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isFestivalTitleEnabled() { try { return LUNAR_FESTIVAL_TITLE.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isFestivalEffectEnabled() { try { return LUNAR_FESTIVAL_EFFECT.get(); } catch (IllegalStateException e) { return true; } }
    public static HolidayScope getHolidayScope() { try { return LUNAR_HOLIDAY_SCOPE.get(); } catch (IllegalStateException e) { return HolidayScope.INTERNATIONAL; } }
    public static boolean isFestivalBadgeEnabled() { try { return LUNAR_FESTIVAL_BADGE.get(); } catch (IllegalStateException e) { return true; } }
    public static OverlayCorner getFestivalBadgeCorner() { try { return LUNAR_FESTIVAL_BADGE_CORNER.get(); } catch (IllegalStateException e) { return OverlayCorner.TOP_RIGHT; } }
    public static boolean isFestivalCountdownEnabled() { try { return LUNAR_FESTIVAL_COUNTDOWN.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean isLightLevelHudEnabled() { try { return LIGHT_LEVEL_HUD_ENABLED.get(); } catch (IllegalStateException e) { return false; } }
    public static OverlayCorner getLightLevelHudCorner() { try { return LIGHT_LEVEL_HUD_CORNER.get(); } catch (IllegalStateException e) { return OverlayCorner.TOP_LEFT; } }
    public static double getLightLevelHudScale() { try { return LIGHT_LEVEL_HUD_SCALE.get(); } catch (IllegalStateException e) { return 1.0; } }
    public static boolean isDynamicLightEnabled() { try { return DYNAMIC_LIGHT_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isDynamicLightAutoDisableWithShaders() { try { return DYNAMIC_LIGHT_AUTO_DISABLE_WITH_SHADERS.get(); } catch (IllegalStateException e) { return true; } }
    public static int getDynamicLightRadius() { try { return DYNAMIC_LIGHT_RADIUS.get(); } catch (IllegalStateException e) { return 12; } }
    public static int getDynamicLightIntensity() { try { return DYNAMIC_LIGHT_INTENSITY.get(); } catch (IllegalStateException e) { return 14; } }
    public static double getDynamicLightOffsetX() { try { return DYNAMIC_LIGHT_OFFSET_X.get(); } catch (IllegalStateException e) { return 0.0; } }
    public static double getDynamicLightOffsetY() { try { return DYNAMIC_LIGHT_OFFSET_Y.get(); } catch (IllegalStateException e) { return 1.0; } }
    public static double getDynamicLightOffsetZ() { try { return DYNAMIC_LIGHT_OFFSET_Z.get(); } catch (IllegalStateException e) { return 0.0; } }
    public static int getDynamicLightUpdateInterval() { try { return DYNAMIC_LIGHT_UPDATE_INTERVAL.get(); } catch (IllegalStateException e) { return 2; } }
    public static boolean isDynamicLightSmoothFade() { try { return DYNAMIC_LIGHT_SMOOTH_FADE.get(); } catch (IllegalStateException e) { return true; } }
    public static double getDynamicLightSmoothSpeed() { try { return DYNAMIC_LIGHT_SMOOTH_SPEED.get(); } catch (IllegalStateException e) { return 0.25; } }
    public static boolean isDynamicLightPlayerLight() { try { return DYNAMIC_LIGHT_PLAYER_LIGHT.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean isDynamicLightItemLight() { try { return DYNAMIC_LIGHT_ITEM_LIGHT.get(); } catch (IllegalStateException e) { return true; } }
    public static int getDynamicLightItemMinLight() { try { return DYNAMIC_LIGHT_ITEM_MIN_LIGHT.get(); } catch (IllegalStateException e) { return 8; } }
    public static boolean isDamageNumbersEnabled() { try { return DAMAGE_NUMBERS.get(); } catch (IllegalStateException e) { return false; } }
    public static double getDamageNumberScale() { try { return DAMAGE_NUMBER_SCALE.get(); } catch (IllegalStateException e) { return 2.0; } }
    public static int getDamageNumberLifetime() { try { return DAMAGE_NUMBER_LIFETIME.get(); } catch (IllegalStateException e) { return 1500; } }
    public static int getDamageNumberMergeWindow() { try { return DAMAGE_NUMBER_MERGE_WINDOW.get(); } catch (IllegalStateException e) { return 1000; } }
    public static boolean isMusicPlayerEnabled() { try { return MUSIC_PLAYER_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isMusicHudEnabled() { try { return MUSIC_HUD_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static OverlayCorner getMusicHudCorner() { try { return MUSIC_HUD_CORNER.get(); } catch (IllegalStateException e) { return OverlayCorner.BOTTOM_RIGHT; } }
    public static int getHitParticleIntensity() { try { return HIT_PARTICLE_INTENSITY.get(); } catch (IllegalStateException e) { return 3; } }

    public static boolean isInfoHudEnabled() { try { return INFO_HUD_ENABLED.get(); } catch (IllegalStateException e) { return false; } }
    public static OverlayCorner getInfoHudCorner() { try { return INFO_HUD_CORNER.get(); } catch (IllegalStateException e) { return OverlayCorner.TOP_LEFT; } }
    public static double getInfoHudScale() { try { return INFO_HUD_SCALE.get(); } catch (IllegalStateException e) { return 1.0; } }

    // ===== 内存优化 Getter =====
    public static boolean isMemoryOptimizationEnabled() { try { return MEMORY_OPTIMIZATION_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isShapeDedupEnabled() { try { return MEMORY_SHAPE_DEDUP.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isPropertyMapDedupEnabled() { try { return MEMORY_PROPERTY_MAP_DEDUP.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isModelInterningEnabled() { try { return MEMORY_MODEL_INTERNING.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isMultipartPredicateEnabled() { try { return MEMORY_MULTIPART_PREDICATE.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isMultipartPredicateDedupEnabled() { return isMultipartPredicateEnabled(); }
    public static boolean isChunkNbtTrimEnabled() { try { return MEMORY_CHUNK_NBT_TRIM.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean isMemoryShowInHudEnabled() { try { return MEMORY_SHOW_IN_HUD.get(); } catch (IllegalStateException e) { return true; } }

    // ===== FSR Getter =====
    public static FsrMode getFsrMode() { try { return FSR_MODE.get(); } catch (IllegalStateException e) { return FsrMode.OFF; } }
    // 仅 FSR1 模式时启用原 FSR 1.0 后处理管线（FsrPipeline 仍走该路径）
    public static boolean isFsrEnabled() { return getFsrMode() == FsrMode.FSR1; }
    public static boolean isFsr2Enabled() { return getFsrMode() == FsrMode.FSR2; }
    public static double getFsrRenderScale() { try { return FSR_RENDER_SCALE.get(); } catch (IllegalStateException e) { return 0.75; } }
    public static double getFsrSharpness() { try { return FSR_SHARPNESS.get(); } catch (IllegalStateException e) { return 0.2; } }
    public static double getFsrHistoryWeight() { try { return FSR_HISTORY_WEIGHT.get(); } catch (IllegalStateException e) { return 0.9; } }

    // ===== 实验性功能 / 通用优化 Getter =====
    public static boolean isExperimentalFeaturesEnabled() { try { return EXPERIMENTAL_FEATURES_ENABLED.get(); } catch (IllegalStateException e) { return false; } }
    public static boolean isAdaptivePerformanceEnabled() { try { return ADAPTIVE_PERFORMANCE_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getAdaptiveTargetFps() { try { return ADAPTIVE_TARGET_FPS.get(); } catch (IllegalStateException e) { return 60; } }
    public static boolean isSmartCullingEnabled() { try { return SMART_CULLING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getCullingCacheMaxEntries() { try { return CULLING_CACHE_MAX_ENTRIES.get(); } catch (IllegalStateException e) { return 4096; } }
    public static boolean isEntityDistanceCullingEnabled() { try { return ENTITY_DISTANCE_CULLING_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getEntityDistanceCullingRange() { try { return ENTITY_DISTANCE_CULLING_RANGE.get(); } catch (IllegalStateException e) { return 96; } }
    public static boolean isEntityCullingWhitelistEnabled() { try { return ENTITY_CULLING_WHITELIST.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isParticleSpawnBudgetEnabled() { try { return PARTICLE_SPAWN_BUDGET_ENABLED.get(); } catch (IllegalStateException e) { return true; } }
    public static int getParticleSpawnBudgetPerFrame() { try { return PARTICLE_SPAWN_BUDGET_PER_FRAME.get(); } catch (IllegalStateException e) { return 512; } }
    public static boolean isAdaptiveCullingDistanceEnabled() { try { return ADAPTIVE_CULLING_DISTANCE.get(); } catch (IllegalStateException e) { return true; } }

    // ===== 远景 LOD Getter（实验性：总闸 + 自身开关双重判定，兜底一律 false） =====
    public static boolean isLodEnabled() {
        try {
            return EXPERIMENTAL_FEATURES_ENABLED.get() && LOD_ENABLED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
    public static int getLodRadiusChunks() { try { return LOD_RADIUS_CHUNKS.get(); } catch (IllegalStateException e) { return 8; } }
    public static int getLodWorkerThreads() { try { return LOD_WORKER_THREADS.get(); } catch (IllegalStateException e) { return 0; } }
    public static int getLodRequestIntervalTicks() { try { return LOD_REQUEST_INTERVAL_TICKS.get(); } catch (IllegalStateException e) { return 5; } }
    public static boolean isLodWaterSurface() { try { return LOD_WATER_SURFACE.get(); } catch (IllegalStateException e) { return true; } }
    public static boolean isLodFrustumCulling() { try { return LOD_FRUSTUM_CULLING.get(); } catch (IllegalStateException e) { return true; } }
    public static double getLodVerticalBias() { try { return LOD_VERTICAL_BIAS.get(); } catch (IllegalStateException e) { return 0.0; } }
    public static int getLodViewDistanceBlocks() { try { return LOD_VIEW_DISTANCE_BLOCKS.get(); } catch (IllegalStateException e) { return 4096; } }
    public static LodQuality getLodQuality() { try { return LOD_QUALITY.get(); } catch (IllegalStateException e) { return LodQuality.MEDIUM; } }

    public enum OverlayCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public enum OutlineColorMode { RAINBOW, BLUE_PURPLE, GREEN_PURPLE, BLUE_GREEN, PURPLE_YELLOW, PURPLE_GREEN_YELLOW }
    public enum ClickEffectType { NONE, RIPPLE, RAIN, SAKURA, CARD, RAT, OX, TIGER, RABBIT, DRAGON, SNAKE, HORSE, GOAT, MONKEY, ROOSTER, DOG, PIG, SPRING, SUMMER, AUTUMN, WINTER }
    public enum HealthColorMode { RAINBOW, CUSTOM }
    public enum Language { AUTO, ENGLISH, CHINESE }
    public enum HolidayScope { CHINA_ONLY, INTERNATIONAL, ALL }
    public enum FsrMode { OFF, FSR1, FSR2 }
    public enum LodQuality { LOW, MEDIUM, HIGH }
}