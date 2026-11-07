package com.example.ribbon.config;

import com.example.ribbon.RibbonOptions;
import com.example.ribbon.optimization.RuntimeConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * NeoForge 配置后端：由平台无关的 {@link Setting} 定义<b>程序化生成</b> ModConfigSpec。
 *
 * <p>这样 132 个配置项只定义一次（{@code Config} 里的 BUILDER 语句），
 * NeoForge 侧键名 / 默认值 / 取值范围与原 TOML 完全一致，用户现有
 * {@code ribbon-common.toml} 不会失效；Fabric 侧则由 Properties 后端处理。</p>
 */
@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public final class ConfigNeoForge implements SettingsBackend {

    private static final Map<Setting<?>, ModConfigSpec.ConfigValue<?>> VALUES = new IdentityHashMap<>();
    private static final ConfigNeoForge INSTANCE = new ConfigNeoForge();

    private static ModConfigSpec platformSpec;

    private ConfigNeoForge() {
    }

    /** 由 NeoForge 的 {@code @Mod} 入口在构造期调用。 */
    public static void install(ModContainer container) {
        platformSpec = build(com.example.ribbon.Config.SPEC.settings());
        container.registerConfig(ModConfig.Type.COMMON, platformSpec);
        ConfigSpec.setBackend(INSTANCE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ModConfigSpec build(List<Setting<?>> settings) {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        Deque<String> path = new ArrayDeque<>();
        for (Setting<?> s : settings) {
            String[] parts = s.group().isEmpty() ? new String[0] : s.group().split("\\.");
            int common = 0;
            Iterator<String> it = path.iterator();
            while (it.hasNext() && common < parts.length && it.next().equals(parts[common])) {
                common++;
            }
            while (path.size() > common) {
                b.pop();
                path.removeLast();
            }
            for (int i = common; i < parts.length; i++) {
                b.push(parts[i]);
                path.addLast(parts[i]);
            }
            if (!s.comments().isEmpty()) {
                b.comment(s.comments().toArray(new String[0]));
            }
            ModConfigSpec.ConfigValue<?> value = switch (s.type()) {
                case BOOLEAN -> b.define(s.name(), (Boolean) s.defaultValue());
                case INT -> b.defineInRange(s.name(), (Integer) s.defaultValue(),
                        (Integer) s.min(), (Integer) s.max());
                case DOUBLE -> b.defineInRange(s.name(), (Double) s.defaultValue(),
                        (Double) s.min(), (Double) s.max());
                case STRING -> b.define(s.name(), (String) s.defaultValue());
                case ENUM -> b.defineEnum(s.name(), (Enum) s.defaultValue());
            };
            VALUES.put(s, value);
        }
        while (!path.isEmpty()) {
            b.pop();
            path.removeLast();
        }
        return b.build();
    }

    @Override
    public void load(List<Setting<?>> settings) {
        for (Setting<?> s : settings) {
            ModConfigSpec.ConfigValue<?> v = VALUES.get(s);
            if (v == null) {
                continue;
            }
            try {
                s.applyRaw(v.get());
            } catch (Throwable ignored) {
                // 单项读取失败不能让启动挂掉
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void save(List<Setting<?>> settings) {
        for (Setting<?> s : settings) {
            ModConfigSpec.ConfigValue<?> v = VALUES.get(s);
            if (v == null) {
                continue;
            }
            try {
                ((ModConfigSpec.ConfigValue) v).set(s.get());
            } catch (Throwable ignored) {
            }
        }
        if (platformSpec != null) {
            platformSpec.save();
        }
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event) {
        com.example.ribbon.Config.SPEC.load();
        RuntimeConfig.refresh();
        if (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading) {
            RibbonOptions.selfCheck();
        }
    }
}
