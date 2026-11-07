package com.example.ribbon.config;

import java.util.List;

/**
 * 配置持久化后端。NeoForge 用 ModConfigSpec（TOML），Fabric 用 Properties。
 */
public interface SettingsBackend {

    void load(List<Setting<?>> settings);

    void save(List<Setting<?>> settings);
}
