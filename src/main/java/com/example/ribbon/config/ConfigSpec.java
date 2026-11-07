package com.example.ribbon.config;

import java.util.List;

/**
 * 平台无关的配置规格：持有全部 {@link Setting}，并把加载 / 保存委托给平台后端。
 */
public final class ConfigSpec {

    /** 各平台入口在启动早期设置。 */
    private static SettingsBackend backend;

    private final List<Setting<?>> settings;

    ConfigSpec(List<Setting<?>> settings) {
        this.settings = settings;
    }

    public static void setBackend(SettingsBackend b) {
        backend = b;
    }

    public static SettingsBackend backend() {
        return backend;
    }

    public List<Setting<?>> settings() {
        return this.settings;
    }

    /** 从后端读取（NeoForge 由 ModConfigEvent 触发，Fabric 在客户端初始化时调用）。 */
    public void load() {
        SettingsBackend b = backend;
        if (b != null) {
            b.load(this.settings);
        }
    }

    public void save() {
        SettingsBackend b = backend;
        if (b != null) {
            b.save(this.settings);
        }
    }
}
