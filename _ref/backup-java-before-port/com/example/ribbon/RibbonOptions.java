package com.example.ribbon;

import com.example.ribbon.optimization.RuntimeConfig;
import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.api.options.OptionIdentifier;
import org.embeddedt.embeddium.api.options.control.ControlValueFormatter;
import org.embeddedt.embeddium.api.options.control.CyclingControl;
import org.embeddedt.embeddium.api.options.control.SliderControl;
import org.embeddedt.embeddium.api.options.control.TickBoxControl;
import org.embeddedt.embeddium.api.options.structure.Option;
import org.embeddedt.embeddium.api.options.structure.OptionGroup;
import org.embeddedt.embeddium.api.options.structure.OptionImpact;
import org.embeddedt.embeddium.api.options.structure.OptionImpl;
import org.embeddedt.embeddium.api.options.structure.OptionPage;
import org.embeddedt.embeddium.api.options.structure.OptionStorage;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class RibbonOptions {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final OptionStorage<Object> STORAGE = new OptionStorage<Object>() {
        private final Object data = new Object();
        @Override public Object getData() { return data; }
        @Override public void save() { Config.SPEC.save(); RuntimeConfig.refresh(); }
    };

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ribbon", path);
    }

    /**
     * 创建滑杆时自动把上限向下对齐到区间步长的整数倍。
     *
     * <p>Embeddium 的 {@link SliderControl} 构造器有硬断言
     * {@code (max - min) % interval == 0}，参数不整除时直接抛
     * {@code IllegalArgumentException}，且该异常发生在打开视频设置界面的
     * mouseClicked 回调里 → 整个游戏崩溃。这里统一收口，杜绝此类崩溃。</p>
     */
    private static SliderControl slider(Option<Integer> option, int min, int max, int interval,
                                        ControlValueFormatter formatter) {
        int safeInterval = Math.max(1, interval);
        int safeMax = Math.max(min + safeInterval, max);
        int alignedMax = min + Math.max(1, (safeMax - min) / safeInterval) * safeInterval;
        ControlValueFormatter safeFormatter = formatter != null
                ? formatter : v -> Component.literal(String.valueOf(v));
        return new SliderControl(option, min, alignedMax, safeInterval, safeFormatter);
    }

    /**
     * 单个页面构建失败只跳过该页，绝不让整个视频设置界面（以及游戏）崩掉。
     * 页面构建发生在 mouseClicked 回调中，任何未捕获异常都会变成 crash report。
     */
    private static void addPage(OptionGUIConstructionEvent event, String name, Supplier<OptionPage> factory) {
        try {
            event.addPage(factory.get());
        } catch (Throwable t) {
            LOGGER.error("[Ribbon] 构建设置页面 {} 失败，已跳过该页", name, t);
        }
    }

    public static void init() {
        OptionGUIConstructionEvent.BUS.addListener(event -> {
            addPage(event, "HUD", RibbonOptions::buildHudPage);
            addPage(event, "Visuals", RibbonOptions::buildVisualsPage);
            addPage(event, "Effects", RibbonOptions::buildEffectsPage);
            addPage(event, "Performance", RibbonOptions::buildPerformancePage);
            addPage(event, "Lunar", RibbonOptions::buildLunarPage);
            addPage(event, "Misc", RibbonOptions::buildMiscPage);
            addPage(event, "LOD", RibbonOptions::buildLodPage);
        });
    }

    private static volatile boolean selfChecked = false;

    /**
     * 配置加载完成后预构建所有设置页一次。
     *
     * <p>{@link OptionImpl} 在构造时就会执行 control 工厂（含 {@link SliderControl}
     * 的硬断言）以及 binding 的 getValue，因此这里能提前捕获「参数不整除」之类的
     * GUI 崩溃，写入启动日志，而不是等玩家点开视频设置才在 mouseClicked 回调里崩掉。
     * 必须在 {@code ModConfigEvent.Loading} 之后调用：更早时配置值尚未就绪，
     * {@code ConfigValue.get()} 会抛 IllegalStateException。</p>
     */
    public static void selfCheck() {
        if (selfChecked) return;
        selfChecked = true;
        java.util.List<Supplier<OptionPage>> pages = java.util.List.of(
                RibbonOptions::buildHudPage, RibbonOptions::buildVisualsPage,
                RibbonOptions::buildEffectsPage, RibbonOptions::buildPerformancePage,
                RibbonOptions::buildLunarPage, RibbonOptions::buildMiscPage,
                RibbonOptions::buildLodPage);
        int ok = 0;
        for (int i = 0; i < pages.size(); i++) {
            try {
                pages.get(i).get();
                ok++;
            } catch (Throwable t) {
                LOGGER.error("[Ribbon] 设置页自检失败 index={}", i, t);
            }
        }
        LOGGER.info("[Ribbon] 设置页自检完成 {}/{} 通过", ok, pages.size());
    }

    // ============ 页面 1：HUD ============
    private static OptionPage buildHudPage() {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("hud_group"));

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("show_overlay"))
                .setName(Component.literal(LangUtil.tr("option.show_overlay")))
                .setTooltip(Component.literal(LangUtil.tr("option.show_overlay.tooltip")))
                .setBinding((d, v) -> Config.SHOW_OVERLAY.set(v), d -> Config.SHOW_OVERLAY.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("overlay_corner"))
                .setName(Component.literal(LangUtil.tr("option.overlay_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.overlay_corner.tooltip")))
                .setBinding((d, v) -> Config.OVERLAY_CORNER.set(v), d -> Config.OVERLAY_CORNER.get())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("overlay_scale"))
                .setName(Component.literal(LangUtil.tr("option.overlay_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.overlay_scale.tooltip")))
                .setBinding((d, v) -> Config.OVERLAY_SCALE.set(v / 10.0),
                        d -> (int) Math.round(Config.OVERLAY_SCALE.get() * 10.0))
                .setControl(o -> slider(o, 5, 30, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("overlay_enhanced"))
                .setName(Component.literal(LangUtil.tr("option.overlay_enhanced")))
                .setTooltip(Component.literal(LangUtil.tr("option.overlay_enhanced.tooltip")))
                .setBinding((d, v) -> Config.OVERLAY_ENHANCED.set(v), d -> Config.isOverlayEnhanced())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 药水效果计时 HUD =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("potion_hud_enabled"))
                .setName(Component.literal(LangUtil.tr("option.potion_hud_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.potion_hud_enabled.tooltip")))
                .setBinding((d, v) -> Config.POTION_HUD_ENABLED.set(v), d -> Config.isPotionHudEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("potion_hud_corner"))
                .setName(Component.literal(LangUtil.tr("option.potion_hud_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.potion_hud_corner.tooltip")))
                .setBinding((d, v) -> Config.POTION_HUD_CORNER.set(v), d -> Config.getPotionHudCorner())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("potion_hud_scale"))
                .setName(Component.literal(LangUtil.tr("option.potion_hud_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.potion_hud_scale.tooltip")))
                .setBinding((d, v) -> Config.POTION_HUD_SCALE.set(v / 10.0),
                        d -> (int) Math.round(Config.getPotionHudScale() * 10.0))
                .setControl(o -> slider(o, 5, 30, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("rainbow_crosshair"))
                .setName(Component.literal(LangUtil.tr("option.rainbow_crosshair")))
                .setTooltip(Component.literal(LangUtil.tr("option.rainbow_crosshair.tooltip")))
                .setBinding((d, v) -> Config.RAINBOW_CROSSHAIR.set(v), d -> Config.RAINBOW_CROSSHAIR.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("hotbar_highlight"))
                .setName(Component.literal(LangUtil.tr("option.hotbar_highlight")))
                .setTooltip(Component.literal(LangUtil.tr("option.hotbar_highlight.tooltip")))
                .setBinding((d, v) -> Config.HOTBAR_HIGHLIGHT.set(v), d -> Config.HOTBAR_HIGHLIGHT.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("damage_numbers"))
                .setName(Component.literal(LangUtil.tr("option.damage_numbers")))
                .setTooltip(Component.literal(LangUtil.tr("option.damage_numbers.tooltip")))
                .setBinding((d, v) -> Config.DAMAGE_NUMBERS.set(v), d -> Config.DAMAGE_NUMBERS.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("damage_number_scale"))
                .setName(Component.literal(LangUtil.tr("option.damage_number_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.damage_number_scale.tooltip")))
                .setBinding((d, v) -> Config.DAMAGE_NUMBER_SCALE.set(v / 10.0),
                        d -> (int) Math.round(Config.DAMAGE_NUMBER_SCALE.get() * 10.0))
                .setControl(o -> slider(o, 5, 50, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("biome_title"))
                .setName(Component.literal(LangUtil.tr("option.biome_title")))
                .setTooltip(Component.literal(LangUtil.tr("option.biome_title.tooltip")))
                .setBinding((d, v) -> Config.BIOME_TITLE.set(v), d -> Config.BIOME_TITLE.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("biome_title_duration"))
                .setName(Component.literal(LangUtil.tr("option.biome_title_duration")))
                .setTooltip(Component.literal(LangUtil.tr("option.biome_title_duration.tooltip")))
                .setBinding((d, v) -> Config.BIOME_TITLE_DURATION.set(v), d -> Config.BIOME_TITLE_DURATION.get())
                .setControl(o -> slider(o, 1000, 10000, 500,
                        v -> Component.literal(String.format("%.1fs", v / 1000.0))))
                .setImpact(OptionImpact.LOW).build());

        // ===== 信息 HUD =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("info_hud_enabled"))
                .setName(Component.literal(LangUtil.tr("option.info_hud_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.info_hud_enabled.tooltip")))
                .setBinding((d, v) -> Config.INFO_HUD_ENABLED.set(v), d -> Config.isInfoHudEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("info_hud_corner"))
                .setName(Component.literal(LangUtil.tr("option.info_hud_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.info_hud_corner.tooltip")))
                .setBinding((d, v) -> Config.INFO_HUD_CORNER.set(v), d -> Config.getInfoHudCorner())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("info_hud_scale"))
                .setName(Component.literal(LangUtil.tr("option.info_hud_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.info_hud_scale.tooltip")))
                .setBinding((d, v) -> Config.INFO_HUD_SCALE.set(v / 10.0),
                        d -> (int) Math.round(Config.getInfoHudScale() * 10.0))
                .setControl(o -> slider(o, 5, 30, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_hud")),
                Component.literal(LangUtil.tr("ribbon.page.hud")),
                ImmutableList.of(group.build()));
    }

    // ============ 页面 2：视觉 ============
    private static OptionPage buildVisualsPage() {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("visuals_group"));

        // ===== 液态玻璃 GUI 主题 =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("liquid_glass"))
                .setName(Component.literal(LangUtil.tr("option.liquid_glass")))
                .setTooltip(Component.literal(LangUtil.tr("option.liquid_glass.tooltip")))
                .setBinding((d, v) -> Config.LIQUID_GLASS.set(v), d -> Config.isLiquidGlassEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("liquid_glass_blur"))
                .setName(Component.literal(LangUtil.tr("option.liquid_glass_blur")))
                .setTooltip(Component.literal(LangUtil.tr("option.liquid_glass_blur.tooltip")))
                .setBinding((d, v) -> Config.LIQUID_GLASS_BLUR.set(v), d -> Config.getLiquidGlassBlur())
                .setControl(o -> slider(o, 0, 10, 1, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        // ===== 夜视增强（Fullbright） =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("night_vision_enabled"))
                .setName(Component.literal(LangUtil.tr("option.night_vision_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.night_vision_enabled.tooltip")))
                .setBinding((d, v) -> Config.NIGHT_VISION_ENABLED.set(v), d -> Config.isNightVisionEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("night_vision_strength"))
                .setName(Component.literal(LangUtil.tr("option.night_vision_strength")))
                .setTooltip(Component.literal(LangUtil.tr("option.night_vision_strength.tooltip")))
                .setBinding((d, v) -> Config.NIGHT_VISION_STRENGTH.set(v), d -> Config.getNightVisionStrength())
                .setControl(o -> slider(o, 10, 100, 10, v -> Component.literal(v + "%")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("rainbow_block_outline"))
                .setName(Component.literal(LangUtil.tr("option.rainbow_block_outline")))
                .setTooltip(Component.literal(LangUtil.tr("option.rainbow_block_outline.tooltip")))
                .setBinding((d, v) -> Config.RAINBOW_BLOCK_OUTLINE.set(v), d -> Config.RAINBOW_BLOCK_OUTLINE.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OutlineColorMode.class, STORAGE)
                .setId(id("outline_color_mode"))
                .setName(Component.literal(LangUtil.tr("option.outline_color_mode")))
                .setTooltip(Component.literal(LangUtil.tr("option.outline_color_mode.tooltip")))
                .setBinding((d, v) -> Config.OUTLINE_COLOR_MODE.set(v), d -> Config.OUTLINE_COLOR_MODE.get())
                .setControl(o -> new CyclingControl<>(o, Config.OutlineColorMode.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("rainbow_health"))
                .setName(Component.literal(LangUtil.tr("option.rainbow_health")))
                .setTooltip(Component.literal(LangUtil.tr("option.rainbow_health.tooltip")))
                .setBinding((d, v) -> Config.RAINBOW_HEALTH.set(v), d -> Config.RAINBOW_HEALTH.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.HealthColorMode.class, STORAGE)
                .setId(id("health_color_mode"))
                .setName(Component.literal(LangUtil.tr("option.health_color_mode")))
                .setTooltip(Component.literal(LangUtil.tr("option.health_color_mode.tooltip")))
                .setBinding((d, v) -> Config.HEALTH_COLOR_MODE.set(v), d -> Config.HEALTH_COLOR_MODE.get())
                .setControl(o -> new CyclingControl<>(o, Config.HealthColorMode.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("health_rainbow_speed"))
                .setName(Component.literal(LangUtil.tr("option.health_rainbow_speed")))
                .setTooltip(Component.literal(LangUtil.tr("option.health_rainbow_speed.tooltip")))
                .setBinding((d, v) -> Config.HEALTH_RAINBOW_SPEED.set(v / 10.0),
                        d -> (int) Math.round(Config.HEALTH_RAINBOW_SPEED.get() * 10.0))
                .setControl(o -> slider(o, 1, 50, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("health_low_shake"))
                .setName(Component.literal(LangUtil.tr("option.health_low_shake")))
                .setTooltip(Component.literal(LangUtil.tr("option.health_low_shake.tooltip")))
                .setBinding((d, v) -> Config.HEALTH_LOW_SHAKE.set(v), d -> Config.HEALTH_LOW_SHAKE.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("health_low_shake_threshold"))
                .setName(Component.literal(LangUtil.tr("option.health_low_shake_threshold")))
                .setTooltip(Component.literal(LangUtil.tr("option.health_low_shake_threshold.tooltip")))
                .setBinding((d, v) -> Config.HEALTH_LOW_SHAKE_THRESHOLD.set(v),
                        d -> Config.HEALTH_LOW_SHAKE_THRESHOLD.get())
                .setControl(o -> slider(o, 5, 90, 5, v -> Component.literal(v + "%")))
                .setImpact(OptionImpact.LOW).build());

        // ===== 动态光源（并入视觉页：渲染观感的一部分） =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("light_level_hud"))
                .setName(Component.literal(LangUtil.tr("option.light_level_hud")))
                .setTooltip(Component.literal(LangUtil.tr("option.light_level_hud.tooltip")))
                .setBinding((d, v) -> Config.LIGHT_LEVEL_HUD_ENABLED.set(v),
                        d -> Config.isLightLevelHudEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("light_level_hud_corner"))
                .setName(Component.literal(LangUtil.tr("option.light_level_hud_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.light_level_hud_corner.tooltip")))
                .setBinding((d, v) -> Config.LIGHT_LEVEL_HUD_CORNER.set(v),
                        d -> Config.getLightLevelHudCorner())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("light_level_hud_scale"))
                .setName(Component.literal(LangUtil.tr("option.light_level_hud_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.light_level_hud_scale.tooltip")))
                .setBinding((d, v) -> Config.LIGHT_LEVEL_HUD_SCALE.set(v / 10.0),
                        d -> (int) Math.round(Config.getLightLevelHudScale() * 10.0))
                .setControl(o -> slider(o, 5, 30, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("dynamic_light_enabled"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_enabled.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_ENABLED.set(v),
                        d -> Config.isDynamicLightEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("dynamic_light_auto_disable"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_auto_disable")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_auto_disable.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_AUTO_DISABLE_WITH_SHADERS.set(v),
                        d -> Config.isDynamicLightAutoDisableWithShaders())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_radius"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_radius")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_radius.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_RADIUS.set(v),
                        d -> Config.getDynamicLightRadius())
                .setControl(o -> slider(o, 4, 24, 1, v -> Component.literal(v + "m")))
                .setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_intensity"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_intensity")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_intensity.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_INTENSITY.set(v),
                        d -> Config.getDynamicLightIntensity())
                .setControl(o -> slider(o, 1, 15, 1, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_offset_x"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_offset_x")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_offset_x.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_OFFSET_X.set(v / 10.0),
                        d -> (int) Math.round(Config.getDynamicLightOffsetX() * 10.0))
                .setControl(o -> slider(o, -40, 40, 1,
                        v -> Component.literal(String.format("%.1f", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_offset_y"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_offset_y")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_offset_y.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_OFFSET_Y.set(v / 10.0),
                        d -> (int) Math.round(Config.getDynamicLightOffsetY() * 10.0))
                .setControl(o -> slider(o, -40, 40, 1,
                        v -> Component.literal(String.format("%.1f", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_offset_z"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_offset_z")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_offset_z.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_OFFSET_Z.set(v / 10.0),
                        d -> (int) Math.round(Config.getDynamicLightOffsetZ() * 10.0))
                .setControl(o -> slider(o, -40, 40, 1,
                        v -> Component.literal(String.format("%.1f", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_update_interval"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_update_interval")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_update_interval.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_UPDATE_INTERVAL.set(v),
                        d -> Config.getDynamicLightUpdateInterval())
                .setControl(o -> slider(o, 1, 20, 1, v -> Component.literal(v + " ticks")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("dynamic_light_smooth"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_smooth")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_smooth.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_SMOOTH_FADE.set(v),
                        d -> Config.isDynamicLightSmoothFade())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_smooth_speed"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_smooth_speed")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_smooth_speed.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_SMOOTH_SPEED.set(v / 100.0),
                        d -> (int) Math.round(Config.getDynamicLightSmoothSpeed() * 100.0))
                .setControl(o -> slider(o, 5, 100, 5,
                        v -> Component.literal(String.format("%.2f", v / 100.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("dynamic_light_player_light"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_player_light")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_player_light.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_PLAYER_LIGHT.set(v),
                        d -> Config.isDynamicLightPlayerLight())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("dynamic_light_item_light"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_item_light")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_item_light.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_ITEM_LIGHT.set(v),
                        d -> Config.isDynamicLightItemLight())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("dynamic_light_item_min"))
                .setName(Component.literal(LangUtil.tr("option.dynamic_light_item_min")))
                .setTooltip(Component.literal(LangUtil.tr("option.dynamic_light_item_min.tooltip")))
                .setBinding((d, v) -> Config.DYNAMIC_LIGHT_ITEM_MIN_LIGHT.set(v),
                        d -> Config.getDynamicLightItemMinLight())
                .setControl(o -> slider(o, 1, 15, 1, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_visuals")),
                Component.literal(LangUtil.tr("ribbon.page.visuals")),
                ImmutableList.of(group.build()));
    }

    // ============ 页面 3：特效 ============
    private static OptionPage buildEffectsPage() {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("effects_group"));

        group.add(OptionImpl.createBuilder(Config.ClickEffectType.class, STORAGE)
                .setId(id("click_effect_type"))
                .setName(Component.literal(LangUtil.tr("option.click_effect_type")))
                .setTooltip(Component.literal(LangUtil.tr("option.click_effect_type.tooltip")))
                .setBinding((d, v) -> Config.CLICK_EFFECT_TYPE.set(v), d -> Config.CLICK_EFFECT_TYPE.get())
                .setControl(o -> new CyclingControl<>(o, Config.ClickEffectType.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("click_effect_cover_ui"))
                .setName(Component.literal(LangUtil.tr("option.click_effect_cover_ui")))
                .setTooltip(Component.literal(LangUtil.tr("option.click_effect_cover_ui.tooltip")))
                .setBinding((d, v) -> Config.CLICK_EFFECT_COVER_UI.set(v),
                        d -> Config.isClickEffectCoverUi())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("click_effect_ui_widgets_only"))
                .setName(Component.literal(LangUtil.tr("option.click_effect_ui_widgets_only")))
                .setTooltip(Component.literal(LangUtil.tr("option.click_effect_ui_widgets_only.tooltip")))
                .setBinding((d, v) -> Config.CLICK_EFFECT_UI_ONLY_ON_WIDGETS.set(v),
                        d -> Config.isClickEffectUiOnlyOnWidgets())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("click_effect_mute_vanilla"))
                .setName(Component.literal(LangUtil.tr("option.click_effect_mute_vanilla")))
                .setTooltip(Component.literal(LangUtil.tr("option.click_effect_mute_vanilla.tooltip")))
                .setBinding((d, v) -> Config.CLICK_EFFECT_MUTE_VANILLA.set(v),
                        d -> Config.isClickEffectMuteVanilla())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("hit_particles"))
                .setName(Component.literal(LangUtil.tr("option.hit_particles")))
                .setTooltip(Component.literal(LangUtil.tr("option.hit_particles.tooltip")))
                .setBinding((d, v) -> Config.HIT_PARTICLES.set(v), d -> Config.HIT_PARTICLES.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("hit_particle_intensity"))
                .setName(Component.literal(LangUtil.tr("option.hit_particle_intensity")))
                .setTooltip(Component.literal(LangUtil.tr("option.hit_particle_intensity.tooltip")))
                .setBinding((d, v) -> Config.HIT_PARTICLE_INTENSITY.set(v),
                        d -> Config.HIT_PARTICLE_INTENSITY.get())
                .setControl(o -> slider(o, 1, 10, 1, v -> Component.literal(v + "x")))
                .setImpact(OptionImpact.LOW).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_effects")),
                Component.literal(LangUtil.tr("ribbon.page.effects")),
                ImmutableList.of(group.build()));
    }

    // ============ 页面 4：性能 ============
    private static OptionPage buildPerformancePage() {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("performance_group"));

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("entity_culling"))
                .setName(Component.literal(LangUtil.tr("option.entity_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.entity_culling.tooltip")))
                .setBinding((d, v) -> Config.ENTITY_CULLING_ENABLED.set(v),
                        d -> Config.isEntityCullingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("entity_culling_range"))
                .setName(Component.literal(LangUtil.tr("option.entity_culling_range")))
                .setTooltip(Component.literal(LangUtil.tr("option.entity_culling_range.tooltip")))
                .setBinding((d, v) -> Config.ENTITY_CULLING_RANGE.set(v),
                        d -> Config.getEntityCullingRange())
                .setControl(o -> slider(o, 16, 128, 8, v -> Component.literal(v + "m")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("block_entity_culling"))
                .setName(Component.literal(LangUtil.tr("option.block_entity_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.block_entity_culling.tooltip")))
                .setBinding((d, v) -> Config.BLOCK_ENTITY_CULLING_ENABLED.set(v),
                        d -> Config.isBlockEntityCullingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("block_entity_culling_range"))
                .setName(Component.literal(LangUtil.tr("option.block_entity_culling_range")))
                .setTooltip(Component.literal(LangUtil.tr("option.block_entity_culling_range.tooltip")))
                .setBinding((d, v) -> Config.BLOCK_ENTITY_CULLING_RANGE.set(v),
                        d -> Config.getBlockEntityCullingRange())
                .setControl(o -> slider(o, 16, 128, 8, v -> Component.literal(v + "m")))
                .setImpact(OptionImpact.LOW).build());
        // ===== 异步寻路 =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("async_pathfinding_enabled"))
                .setName(Component.literal(LangUtil.tr("option.async_pathfinding_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.async_pathfinding_enabled.tooltip")))
                .setBinding((d, v) -> Config.ASYNC_PATHFINDING_ENABLED.set(v), d -> Config.isAsyncPathfindingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.HIGH).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("async_pathfinding_threads"))
                .setName(Component.literal(LangUtil.tr("option.async_pathfinding_threads")))
                .setTooltip(Component.literal(LangUtil.tr("option.async_pathfinding_threads.tooltip")))
                .setBinding((d, v) -> Config.ASYNC_PATHFINDING_THREADS.set(v), d -> Config.getAsyncPathfindingThreads())
                .setControl(o -> slider(o, 1, 8, 1, v -> Component.literal(v + " Threads")))
                .setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("async_pathfinding_cache_ticks"))
                .setName(Component.literal(LangUtil.tr("option.async_pathfinding_cache_ticks")))
                .setTooltip(Component.literal(LangUtil.tr("option.async_pathfinding_cache_ticks.tooltip")))
                .setBinding((d, v) -> Config.ASYNC_PATHFINDING_CACHE_TICKS.set(v), d -> Config.getAsyncPathfindingCacheTicks())
                .setControl(o -> slider(o, 10, 200, 10, v -> Component.literal(v + " ticks")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("async_pathfinding_hot_threshold"))
                .setName(Component.literal(LangUtil.tr("option.async_pathfinding_hot_threshold")))
                .setTooltip(Component.literal(LangUtil.tr("option.async_pathfinding_hot_threshold.tooltip")))
                .setBinding((d, v) -> Config.ASYNC_PATHFINDING_HOT_THRESHOLD.set(v), d -> Config.getAsyncPathfindingHotThreshold())
                .setControl(o -> slider(o, 1, 20, 1, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("async_pathfinding_max_in_flight"))
                .setName(Component.literal(LangUtil.tr("option.async_pathfinding_max_in_flight")))
                .setTooltip(Component.literal(LangUtil.tr("option.async_pathfinding_max_in_flight.tooltip")))
                .setBinding((d, v) -> Config.ASYNC_PATHFINDING_MAX_IN_FLIGHT.set(v), d -> Config.getAsyncPathfindingMaxInFlight())
                .setControl(o -> slider(o, 10, 500, 10, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("async_pathfinding_snapshot_range"))
                .setName(Component.literal(LangUtil.tr("option.async_pathfinding_snapshot_range")))
                .setTooltip(Component.literal(LangUtil.tr("option.async_pathfinding_snapshot_range.tooltip")))
                .setBinding((d, v) -> Config.ASYNC_PATHFINDING_SNAPSHOT_RANGE.set(v), d -> Config.getAsyncPathfindingSnapshotRange())
                .setControl(o -> slider(o, 1, 8, 1, v -> Component.literal(v + " chunks")))
                .setImpact(OptionImpact.LOW).build());
        // ===== 光影源码缓存 =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("shader_cache_enabled"))
                .setName(Component.literal(LangUtil.tr("option.shader_cache_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.shader_cache_enabled.tooltip")))
                .setBinding((d, v) -> Config.SHADER_CACHE_ENABLED.set(v), d -> Config.isShaderCacheEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("item_entity_culling"))
                .setName(Component.literal(LangUtil.tr("option.item_entity_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.item_entity_culling.tooltip")))
                .setBinding((d, v) -> Config.ITEM_ENTITY_CULLING_ENABLED.set(v),
                        d -> Config.isItemEntityCullingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("item_entity_max_distance"))
                .setName(Component.literal(LangUtil.tr("option.item_entity_max_distance")))
                .setTooltip(Component.literal(LangUtil.tr("option.item_entity_max_distance.tooltip")))
                .setBinding((d, v) -> Config.ITEM_ENTITY_MAX_DISTANCE.set(v),
                        d -> Config.getItemEntityMaxDistance())
                .setControl(o -> slider(o, 16, 128, 8, v -> Component.literal(v + "m")))
                .setImpact(OptionImpact.LOW).build());

        // ===== FSR 超分辨率模式（OFF / FSR1 / FSR2） =====
        group.add(OptionImpl.createBuilder(Config.FsrMode.class, STORAGE)
                .setId(id("fsr_mode"))
                .setName(Component.literal(LangUtil.tr("option.fsr_mode")))
                .setTooltip(Component.literal(LangUtil.tr("option.fsr_mode.tooltip")))
                .setBinding((d, v) -> Config.FSR_MODE.set(v), d -> Config.getFsrMode())
                .setControl(o -> new CyclingControl<>(o, Config.FsrMode.class))
                .setImpact(OptionImpact.HIGH).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("fsr_render_scale"))
                .setName(Component.literal(LangUtil.tr("option.fsr_render_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.fsr_render_scale.tooltip")))
                .setBinding((d, v) -> Config.FSR_RENDER_SCALE.set(v / 100.0), d -> (int)(Config.getFsrRenderScale() * 100))
                .setControl(o -> slider(o, 50, 100, 5, v -> Component.literal(v + "%")))
                .setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("fsr_sharpness"))
                .setName(Component.literal(LangUtil.tr("option.fsr_sharpness")))
                .setTooltip(Component.literal(LangUtil.tr("option.fsr_sharpness.tooltip")))
                .setBinding((d, v) -> Config.FSR_SHARPNESS.set(v / 100.0), d -> (int)(Config.getFsrSharpness() * 100))
                .setControl(o -> slider(o, 0, 100, 5, v -> Component.literal(v + "%")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("fsr_history_weight"))
                .setName(Component.literal(LangUtil.tr("option.fsr_history_weight")))
                .setTooltip(Component.literal(LangUtil.tr("option.fsr_history_weight.tooltip")))
                .setBinding((d, v) -> Config.FSR_HISTORY_WEIGHT.set(v / 100.0), d -> (int)(Config.getFsrHistoryWeight() * 100))
                .setControl(o -> slider(o, 0, 100, 5, v -> Component.literal(v + "%")))
                .setImpact(OptionImpact.MEDIUM).build());

        // ===== 极速材质加载配置 =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("fast_texture_loading"))
                .setName(Component.literal(LangUtil.tr("option.fast_texture_loading")))
                .setTooltip(Component.literal(LangUtil.tr("option.fast_texture_loading.tooltip")))
                .setBinding((d, v) -> Config.FAST_TEXTURE_LOADING.set(v),
                        d -> Config.isFastTextureLoadingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("texture_loading_threads"))
                .setName(Component.literal(LangUtil.tr("option.texture_loading_threads")))
                .setTooltip(Component.literal(LangUtil.tr("option.texture_loading_threads.tooltip")))
                .setBinding((d, v) -> {
                    Config.TEXTURE_LOADING_THREADS.set(v);
                    com.example.ribbon.util.TextureThreadPool.init();
                }, d -> Config.getTextureLoadingThreads())
                .setControl(o -> slider(o, 1, 8, 1, v -> Component.literal(v + " Threads")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("texture_debug_log"))
                .setName(Component.literal(LangUtil.tr("option.texture_debug_log")))
                .setTooltip(Component.literal(LangUtil.tr("option.texture_debug_log.tooltip")))
                .setBinding((d, v) -> Config.TEXTURE_DEBUG_LOG.set(v),
                        d -> Config.isTextureDebugLogEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 🚨 新增：内存深度优化 (FerriteCore-like) =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_optimization_enabled"))
                .setName(Component.literal(LangUtil.tr("option.memory_optimization_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_optimization_enabled.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_OPTIMIZATION_ENABLED.set(v), d -> Config.isMemoryOptimizationEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.HIGH).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_shape_dedup"))
                .setName(Component.literal(LangUtil.tr("option.memory_shape_dedup")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_shape_dedup.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_SHAPE_DEDUP.set(v), d -> Config.isShapeDedupEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_property_map_dedup"))
                .setName(Component.literal(LangUtil.tr("option.memory_property_map_dedup")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_property_map_dedup.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_PROPERTY_MAP_DEDUP.set(v), d -> Config.isPropertyMapDedupEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_model_interning"))
                .setName(Component.literal(LangUtil.tr("option.memory_model_interning")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_model_interning.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_MODEL_INTERNING.set(v), d -> Config.isModelInterningEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_multipart_predicate"))
                .setName(Component.literal(LangUtil.tr("option.memory_multipart_predicate")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_multipart_predicate.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_MULTIPART_PREDICATE.set(v), d -> Config.isMultipartPredicateEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_chunk_nbt_trim"))
                .setName(Component.literal(LangUtil.tr("option.memory_chunk_nbt_trim")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_chunk_nbt_trim.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_CHUNK_NBT_TRIM.set(v), d -> Config.isChunkNbtTrimEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_show_in_hud"))
                .setName(Component.literal(LangUtil.tr("option.memory_show_in_hud")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_show_in_hud.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_SHOW_IN_HUD.set(v), d -> Config.isMemoryShowInHudEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 自适应性能 / 剔除精简开销 =====
        OptionGroup.Builder adaptiveGroup = OptionGroup.createBuilder().setId(id("adaptive_group"));

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("adaptive_performance"))
                .setName(Component.literal(LangUtil.tr("option.adaptive_performance")))
                .setTooltip(Component.literal(LangUtil.tr("option.adaptive_performance.tooltip")))
                .setBinding((d, v) -> Config.ADAPTIVE_PERFORMANCE_ENABLED.set(v),
                        d -> Config.isAdaptivePerformanceEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("adaptive_target_fps"))
                .setName(Component.literal(LangUtil.tr("option.adaptive_target_fps")))
                .setTooltip(Component.literal(LangUtil.tr("option.adaptive_target_fps.tooltip")))
                .setBinding((d, v) -> Config.ADAPTIVE_TARGET_FPS.set(v), d -> Config.getAdaptiveTargetFps())
                .setControl(o -> slider(o, 30, 240, 5, v -> Component.literal(v + " FPS")))
                .setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("smart_culling"))
                .setName(Component.literal(LangUtil.tr("option.smart_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.smart_culling.tooltip")))
                .setBinding((d, v) -> Config.SMART_CULLING_ENABLED.set(v), d -> Config.isSmartCullingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("culling_cache_max_entries"))
                .setName(Component.literal(LangUtil.tr("option.culling_cache_max_entries")))
                .setTooltip(Component.literal(LangUtil.tr("option.culling_cache_max_entries.tooltip")))
                .setBinding((d, v) -> Config.CULLING_CACHE_MAX_ENTRIES.set(v), d -> Config.getCullingCacheMaxEntries())
                .setControl(o -> slider(o, 256, 16384, 256, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("particle_distance_culling"))
                .setName(Component.literal(LangUtil.tr("option.particle_distance_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.particle_distance_culling.tooltip")))
                .setBinding((d, v) -> Config.PARTICLE_CULLING_ENABLED.set(v),
                        d -> Config.isParticleCullingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("particle_culling_range"))
                .setName(Component.literal(LangUtil.tr("option.particle_culling_range")))
                .setTooltip(Component.literal(LangUtil.tr("option.particle_culling_range.tooltip")))
                .setBinding((d, v) -> Config.PARTICLE_CULLING_RANGE.set(v),
                        d -> Config.getParticleCullingRange())
                .setControl(o -> slider(o, 8, 64, 4, v -> Component.literal(v + "m")))
                .setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("entity_distance_culling"))
                .setName(Component.literal(LangUtil.tr("option.entity_distance_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.entity_distance_culling.tooltip")))
                .setBinding((d, v) -> Config.ENTITY_DISTANCE_CULLING_ENABLED.set(v),
                        d -> Config.isEntityDistanceCullingEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("entity_distance_culling_range"))
                .setName(Component.literal(LangUtil.tr("option.entity_distance_culling_range")))
                .setTooltip(Component.literal(LangUtil.tr("option.entity_distance_culling_range.tooltip")))
                .setBinding((d, v) -> Config.ENTITY_DISTANCE_CULLING_RANGE.set(v),
                        d -> Config.getEntityDistanceCullingRange())
                .setControl(o -> slider(o, 32, 256, 8, v -> Component.literal(v + "m")))
                .setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("entity_culling_whitelist"))
                .setName(Component.literal(LangUtil.tr("option.entity_culling_whitelist")))
                .setTooltip(Component.literal(LangUtil.tr("option.entity_culling_whitelist.tooltip")))
                .setBinding((d, v) -> Config.ENTITY_CULLING_WHITELIST.set(v),
                        d -> Config.isEntityCullingWhitelistEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("particle_spawn_budget"))
                .setName(Component.literal(LangUtil.tr("option.particle_spawn_budget")))
                .setTooltip(Component.literal(LangUtil.tr("option.particle_spawn_budget.tooltip")))
                .setBinding((d, v) -> Config.PARTICLE_SPAWN_BUDGET_ENABLED.set(v),
                        d -> Config.isParticleSpawnBudgetEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("particle_spawn_budget_per_frame"))
                .setName(Component.literal(LangUtil.tr("option.particle_spawn_budget_per_frame")))
                .setTooltip(Component.literal(LangUtil.tr("option.particle_spawn_budget_per_frame.tooltip")))
                .setBinding((d, v) -> Config.PARTICLE_SPAWN_BUDGET_PER_FRAME.set(v),
                        d -> Config.getParticleSpawnBudgetPerFrame())
                .setControl(o -> slider(o, 64, 4096, 64, v -> Component.literal(String.valueOf(v))))
                .setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("adaptive_culling_distance"))
                .setName(Component.literal(LangUtil.tr("option.adaptive_culling_distance")))
                .setTooltip(Component.literal(LangUtil.tr("option.adaptive_culling_distance.tooltip")))
                .setBinding((d, v) -> Config.ADAPTIVE_CULLING_DISTANCE.set(v),
                        d -> Config.isAdaptiveCullingDistanceEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 新增：HUD 文本节流 / 光影状态缓存 / 内存压力自适应 / 物品光缓存 =====
        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("hud_text_cache"))
                .setName(Component.literal(LangUtil.tr("option.hud_text_cache")))
                .setTooltip(Component.literal(LangUtil.tr("option.hud_text_cache.tooltip")))
                .setBinding((d, v) -> Config.HUD_TEXT_CACHE_ENABLED.set(v), d -> Config.isHudTextCacheEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("hud_refresh_interval"))
                .setName(Component.literal(LangUtil.tr("option.hud_refresh_interval")))
                .setTooltip(Component.literal(LangUtil.tr("option.hud_refresh_interval.tooltip")))
                .setBinding((d, v) -> Config.HUD_REFRESH_INTERVAL_MS.set(v), d -> Config.getHudRefreshIntervalMs())
                .setControl(o -> slider(o, 50, 500, 50, v -> Component.literal(v + " ms")))
                .setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("shader_state_cache"))
                .setName(Component.literal(LangUtil.tr("option.shader_state_cache")))
                .setTooltip(Component.literal(LangUtil.tr("option.shader_state_cache.tooltip")))
                .setBinding((d, v) -> Config.SHADER_STATE_CACHE_ENABLED.set(v), d -> Config.isShaderStateCacheEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("memory_pressure_adaptive"))
                .setName(Component.literal(LangUtil.tr("option.memory_pressure_adaptive")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_pressure_adaptive.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_PRESSURE_ADAPTIVE.set(v), d -> Config.isMemoryPressureAdaptive())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.MEDIUM).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("memory_pressure_threshold"))
                .setName(Component.literal(LangUtil.tr("option.memory_pressure_threshold")))
                .setTooltip(Component.literal(LangUtil.tr("option.memory_pressure_threshold.tooltip")))
                .setBinding((d, v) -> Config.MEMORY_PRESSURE_THRESHOLD.set(v), d -> Config.getMemoryPressureThreshold())
                .setControl(o -> slider(o, 50, 95, 5, v -> Component.literal(v + "%")))
                .setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("item_light_cache"))
                .setName(Component.literal(LangUtil.tr("option.item_light_cache")))
                .setTooltip(Component.literal(LangUtil.tr("option.item_light_cache.tooltip")))
                .setBinding((d, v) -> Config.ITEM_LIGHT_CACHE_ENABLED.set(v), d -> Config.isItemLightCacheEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("gui_search_debounce"))
                .setName(Component.literal(LangUtil.tr("option.gui_search_debounce")))
                .setTooltip(Component.literal(LangUtil.tr("option.gui_search_debounce.tooltip")))
                .setBinding((d, v) -> Config.GUI_SEARCH_DEBOUNCE_ENABLED.set(v), d -> Config.isGuiSearchDebounceEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        adaptiveGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("pause_when_unfocused"))
                .setName(Component.literal(LangUtil.tr("option.pause_when_unfocused")))
                .setTooltip(Component.literal(LangUtil.tr("option.pause_when_unfocused.tooltip")))
                .setBinding((d, v) -> Config.PAUSE_WHEN_UNFOCUSED.set(v), d -> Config.isPauseWhenUnfocused())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_performance")),
                Component.literal(LangUtil.tr("ribbon.page.performance")),
                ImmutableList.of(group.build(), adaptiveGroup.build()));
    }

    // ============ 页面 6：农历 ============
    private static OptionPage buildLunarPage() {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("lunar_group"));

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_hud_enabled"))
                .setName(Component.literal(LangUtil.tr("option.lunar_hud_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_hud_enabled.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_HUD_ENABLED.set(v),
                        d -> Config.isLunarHudEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("lunar_hud_corner"))
                .setName(Component.literal(LangUtil.tr("option.lunar_hud_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_hud_corner.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_HUD_CORNER.set(v),
                        d -> Config.getLunarHudCorner())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("lunar_hud_scale"))
                .setName(Component.literal(LangUtil.tr("option.lunar_hud_scale")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_hud_scale.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_HUD_SCALE.set(v / 10.0),
                        d -> (int) Math.round(Config.getLunarHudScale() * 10.0))
                .setControl(o -> slider(o, 5, 30, 1,
                        v -> Component.literal(String.format("%.1fx", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_show_term"))
                .setName(Component.literal(LangUtil.tr("option.lunar_show_term")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_show_term.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_SHOW_TERM.set(v),
                        d -> Config.isShowSolarTerm())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_show_festival"))
                .setName(Component.literal(LangUtil.tr("option.lunar_show_festival")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_show_festival.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_SHOW_FESTIVAL.set(v),
                        d -> Config.isShowFestival())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_festival_title"))
                .setName(Component.literal(LangUtil.tr("option.lunar_festival_title")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_festival_title.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_FESTIVAL_TITLE.set(v),
                        d -> Config.isFestivalTitleEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_festival_effect"))
                .setName(Component.literal(LangUtil.tr("option.lunar_festival_effect")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_festival_effect.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_FESTIVAL_EFFECT.set(v),
                        d -> Config.isFestivalEffectEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.HolidayScope.class, STORAGE)
                .setId(id("lunar_holiday_scope"))
                .setName(Component.literal(LangUtil.tr("option.lunar_holiday_scope")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_holiday_scope.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_HOLIDAY_SCOPE.set(v),
                        d -> Config.getHolidayScope())
                .setControl(o -> new CyclingControl<>(o, Config.HolidayScope.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_festival_badge"))
                .setName(Component.literal(LangUtil.tr("option.lunar_festival_badge")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_festival_badge.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_FESTIVAL_BADGE.set(v),
                        d -> Config.isFestivalBadgeEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("lunar_festival_badge_corner"))
                .setName(Component.literal(LangUtil.tr("option.lunar_festival_badge_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_festival_badge_corner.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_FESTIVAL_BADGE_CORNER.set(v),
                        d -> Config.getFestivalBadgeCorner())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lunar_festival_countdown"))
                .setName(Component.literal(LangUtil.tr("option.lunar_festival_countdown")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_festival_countdown.tooltip")))
                .setBinding((d, v) -> Config.LUNAR_FESTIVAL_COUNTDOWN.set(v),
                        d -> Config.isFestivalCountdownEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("lunar_upcoming_preview"))
                .setName(Component.literal(LangUtil.tr("option.lunar_upcoming_preview")))
                .setTooltip(Component.literal(LangUtil.tr("option.lunar_upcoming_preview.tooltip")))
                .setBinding((d, v) -> {}, d -> 0)
                .setControl(o -> slider(o, 0, 4, 1, v -> {
                    var list = com.example.ribbon.lunar.FestivalPreview.getUpcoming(5);
                    if (v >= list.size()) return Component.literal("-");
                    return Component.literal(list.get(v).format());
                }))
                .setImpact(OptionImpact.LOW).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_lunar")),
                Component.literal(LangUtil.tr("ribbon.page.lunar")),
                ImmutableList.of(group.build()));
    }

    // ============ 页面 9：杂项（含原"体验"与"音乐"选项） ============
    private static OptionPage buildMiscPage() {
        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("misc_group"));

        group.add(OptionImpl.createBuilder(Config.Language.class, STORAGE)
                .setId(id("language"))
                .setName(Component.literal(LangUtil.tr("option.language")))
                .setTooltip(Component.literal(LangUtil.tr("option.language.tooltip")))
                .setBinding((d, v) -> Config.LANGUAGE.set(v), d -> Config.LANGUAGE.get())
                .setControl(o -> new CyclingControl<>(o, Config.Language.class))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("shader_compatibility_warning"))
                .setName(Component.literal(LangUtil.tr("option.shader_compatibility_warning")))
                .setTooltip(Component.literal(LangUtil.tr("option.shader_compatibility_warning.tooltip")))
                .setBinding((d, v) -> Config.SHADER_COMPATIBILITY_WARNING.set(v),
                        d -> Config.isShaderCompatibilityWarningEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("show_fps_in_title"))
                .setName(Component.literal(LangUtil.tr("option.show_fps_in_title")))
                .setTooltip(Component.literal(LangUtil.tr("option.show_fps_in_title.tooltip")))
                .setBinding((d, v) -> Config.SHOW_FPS_IN_TITLE.set(v), d -> Config.isShowFpsInTitle())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 输入法（IME）兼容修复 =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("ime_fix_enabled"))
                .setName(Component.literal(LangUtil.tr("option.ime_fix_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.ime_fix_enabled.tooltip")))
                .setBinding((d, v) -> Config.IME_FIX_ENABLED.set(v), d -> Config.isImeFixEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("ime_composition_guard"))
                .setName(Component.literal(LangUtil.tr("option.ime_composition_guard")))
                .setTooltip(Component.literal(LangUtil.tr("option.ime_composition_guard.tooltip")))
                .setBinding((d, v) -> Config.IME_COMPOSITION_GUARD.set(v), d -> Config.isImeCompositionGuardEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("show_advanced_debug"))
                .setName(Component.literal(LangUtil.tr("option.show_advanced_debug")))
                .setTooltip(Component.literal(LangUtil.tr("option.show_advanced_debug.tooltip")))
                .setBinding((d, v) -> Config.SHOW_ADVANCED_DEBUG.set(v),
                        d -> Config.isShowAdvancedDebug())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 游戏体验（原 buildGameplayPage 合并而来） =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("zoom_enabled"))
                .setName(Component.literal(LangUtil.tr("option.zoom_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.zoom_enabled.tooltip")))
                .setBinding((d, v) -> Config.ZOOM_ENABLED.set(v), d -> Config.ZOOM_ENABLED.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("zoom_fov_multiplier"))
                .setName(Component.literal(LangUtil.tr("option.zoom_fov_multiplier")))
                .setTooltip(Component.literal(LangUtil.tr("option.zoom_fov_multiplier.tooltip")))
                .setBinding((d, v) -> Config.ZOOM_FOV_MULTIPLIER.set(v / 100.0),
                        d -> (int) Math.round(Config.ZOOM_FOV_MULTIPLIER.get() * 100.0))
                .setControl(o -> slider(o, 10, 90, 5,
                        v -> Component.literal(String.format("%.2fx", v / 100.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("zoom_smooth"))
                .setName(Component.literal(LangUtil.tr("option.zoom_smooth")))
                .setTooltip(Component.literal(LangUtil.tr("option.zoom_smooth.tooltip")))
                .setBinding((d, v) -> Config.ZOOM_SMOOTH.set(v), d -> Config.ZOOM_SMOOTH.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        // ===== 音乐播放器（原 buildMusicPage 合并而来） =====
        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("music_player_enabled"))
                .setName(Component.literal(LangUtil.tr("option.music_player_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.music_player_enabled.tooltip")))
                .setBinding((d, v) -> Config.MUSIC_PLAYER_ENABLED.set(v),
                        d -> Config.isMusicPlayerEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("music_hud_enabled"))
                .setName(Component.literal(LangUtil.tr("option.music_hud_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.music_hud_enabled.tooltip")))
                .setBinding((d, v) -> Config.MUSIC_HUD_ENABLED.set(v),
                        d -> Config.isMusicHudEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.OverlayCorner.class, STORAGE)
                .setId(id("music_hud_corner"))
                .setName(Component.literal(LangUtil.tr("option.music_hud_corner")))
                .setTooltip(Component.literal(LangUtil.tr("option.music_hud_corner.tooltip")))
                .setBinding((d, v) -> Config.MUSIC_HUD_CORNER.set(v),
                        d -> Config.getMusicHudCorner())
                .setControl(o -> new CyclingControl<>(o, Config.OverlayCorner.class))
                .setImpact(OptionImpact.LOW).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_misc")),
                Component.literal(LangUtil.tr("ribbon.page.misc")),
                ImmutableList.of(group.build()));
    }

    // ============ 页面 7：远景 LOD（实验性） ============
    private static OptionPage buildLodPage() {
        OptionGroup.Builder experimentalGroup = OptionGroup.createBuilder().setId(id("experimental_group"));

        experimentalGroup.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("experimental_enabled"))
                .setName(Component.literal(LangUtil.tr("option.experimental_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.experimental_enabled.tooltip")))
                .setBinding((d, v) -> { Config.EXPERIMENTAL_FEATURES_ENABLED.set(v); RuntimeConfig.refresh(); },
                        d -> Config.isExperimentalFeaturesEnabled())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.HIGH).build());

        OptionGroup.Builder group = OptionGroup.createBuilder().setId(id("lod_group"));

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lod_enabled"))
                .setName(Component.literal(LangUtil.tr("option.lod_enabled")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_enabled.tooltip")))
                .setBinding((d, v) -> { Config.LOD_ENABLED.set(v); RuntimeConfig.refresh(); },
                        d -> Config.LOD_ENABLED.get())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.HIGH).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("lod_radius_chunks"))
                .setName(Component.literal(LangUtil.tr("option.lod_radius_chunks")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_radius_chunks.tooltip")))
                .setBinding((d, v) -> Config.LOD_RADIUS_CHUNKS.set(v), d -> Config.getLodRadiusChunks())
                .setControl(o -> slider(o, 2, 32, 2, v -> Component.literal(v + " chunks")))
                .setImpact(OptionImpact.HIGH).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("lod_worker_threads"))
                .setName(Component.literal(LangUtil.tr("option.lod_worker_threads")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_worker_threads.tooltip")))
                .setBinding((d, v) -> Config.LOD_WORKER_THREADS.set(v), d -> Config.getLodWorkerThreads())
                .setControl(o -> slider(o, 0, 16, 1,
                        v -> Component.literal(v == 0 ? "Auto" : v + " Threads")))
                .setImpact(OptionImpact.MEDIUM).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("lod_request_interval_ticks"))
                .setName(Component.literal(LangUtil.tr("option.lod_request_interval_ticks")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_request_interval_ticks.tooltip")))
                .setBinding((d, v) -> Config.LOD_REQUEST_INTERVAL_TICKS.set(v), d -> Config.getLodRequestIntervalTicks())
                .setControl(o -> slider(o, 5, 100, 5, v -> Component.literal(v + " ticks")))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lod_water_surface"))
                .setName(Component.literal(LangUtil.tr("option.lod_water_surface")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_water_surface.tooltip")))
                .setBinding((d, v) -> Config.LOD_WATER_SURFACE.set(v), d -> Config.isLodWaterSurface())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Boolean.class, STORAGE)
                .setId(id("lod_frustum_culling"))
                .setName(Component.literal(LangUtil.tr("option.lod_frustum_culling")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_frustum_culling.tooltip")))
                .setBinding((d, v) -> Config.LOD_FRUSTUM_CULLING.set(v), d -> Config.isLodFrustumCulling())
                .setControl(TickBoxControl::new).setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Integer.class, STORAGE)
                .setId(id("lod_vertical_bias"))
                .setName(Component.literal(LangUtil.tr("option.lod_vertical_bias")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_vertical_bias.tooltip")))
                .setBinding((d, v) -> Config.LOD_VERTICAL_BIAS.set(v / 10.0),
                        d -> (int) Math.round(Config.getLodVerticalBias() * 10.0))
                .setControl(o -> slider(o, -160, 160, 5,
                        v -> Component.literal(String.format("%.1f", v / 10.0))))
                .setImpact(OptionImpact.LOW).build());

        group.add(OptionImpl.createBuilder(Config.LodQuality.class, STORAGE)
                .setId(id("lod_quality"))
                .setName(Component.literal(LangUtil.tr("option.lod_quality")))
                .setTooltip(Component.literal(LangUtil.tr("option.lod_quality.tooltip")))
                .setBinding((d, v) -> Config.LOD_QUALITY.set(v), d -> Config.getLodQuality())
                .setControl(o -> new CyclingControl<>(o, Config.LodQuality.class))
                .setImpact(OptionImpact.MEDIUM).build());

        return new OptionPage(
                OptionIdentifier.create(id("page_lod")),
                Component.literal(LangUtil.tr("ribbon.page.lod")),
                ImmutableList.of(experimentalGroup.build(), group.build()));
    }
}