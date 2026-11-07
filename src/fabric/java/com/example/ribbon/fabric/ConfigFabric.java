package com.example.ribbon.fabric;

import com.example.ribbon.config.ConfigSpec;
import com.example.ribbon.config.Setting;
import com.example.ribbon.config.SettingsBackend;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Fabric 配置后端：{@code config/ribbon.properties}。
 *
 * <p>键名与 NeoForge 的 TOML 路径一致（如 {@code gui_settings.liquidGlass}），
 * 两个平台的配置文件可以互相抄。</p>
 */
public final class ConfigFabric implements SettingsBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger("ribbon");
    private static final ConfigFabric INSTANCE = new ConfigFabric();

    private Path file;

    private ConfigFabric() {
    }

    public static void init() {
        INSTANCE.file = FabricLoader.getInstance().getConfigDir().resolve("ribbon.properties");
        ConfigSpec.setBackend(INSTANCE);
        com.example.ribbon.Config.SPEC.load();
        LOGGER.info("[Ribbon] Fabric 配置已加载: {}", INSTANCE.file);
    }

    @Override
    public void load(List<Setting<?>> settings) {
        Properties props = new Properties();
        if (this.file != null && Files.exists(this.file)) {
            try (InputStream in = Files.newInputStream(this.file)) {
                props.load(in);
            } catch (Throwable t) {
                LOGGER.warn("[Ribbon] 读取 {} 失败，使用默认值", this.file, t);
            }
        }
        for (Setting<?> s : settings) {
            String raw = props.getProperty(s.path());
            if (raw != null) {
                s.applyRaw(raw);
            }
        }
    }

    @Override
    public void save(List<Setting<?>> settings) {
        if (this.file == null) {
            return;
        }
        Properties props = new Properties();
        if (Files.exists(this.file)) {
            try (InputStream in = Files.newInputStream(this.file)) {
                props.load(in);
            } catch (Throwable ignored) {
            }
        }
        for (Setting<?> s : settings) {
            props.setProperty(s.path(), String.valueOf(s.get()));
        }
        try (OutputStream out = Files.newOutputStream(this.file)) {
            props.store(out, "Ribbon configuration (26.2 Fabric)");
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] 写入 {} 失败", this.file, t);
        }
    }
}
