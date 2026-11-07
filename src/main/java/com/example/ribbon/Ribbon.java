package com.example.ribbon;

import com.example.ribbon.entityculling.EntityCullingManager;
import com.example.ribbon.util.TextureThreadPool;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod("ribbon")
public class Ribbon {

    public static final String MODID = "ribbon";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Ribbon(IEventBus modEventBus, ModContainer modContainer) {
        // 配置：由平台无关的 Setting 定义程序化生成 ModConfigSpec（键名/默认值/范围与原 TOML 一致）
        com.example.ribbon.config.ConfigNeoForge.install(modContainer);

        // 26.2 的 @EventBusSubscriber 不再区分 MOD/GAME 总线，MOD 总线事件显式注册
        modContainer.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (container, screen) -> {
                    LOGGER.info("[Ribbon] 配置屏工厂被调用，正在打开 Ribbon 设置界面");
                    return new org.embeddedt.embeddium.impl.gui.EmbeddiumVideoOptionsScreen(
                            screen, org.embeddedt.embeddium.impl.gui.EmbeddiumVideoOptionsScreen.makePages());
                });
        modEventBus.addListener(com.example.ribbon.config.ConfigNeoForge::onConfigLoad);
        modEventBus.addListener(RibbonUIHandler.ModBusEvents::registerKeys);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            // 1. 初始化 UI 与兼容层
            RibbonOptions.init();
            DistantHorizonsCompat.init();
            EntityCullingManager.init();

            // 2. 初始化音乐播放器
            com.example.ribbon.music.MusicPlayerManager.init();
            com.example.ribbon.music.MusicLibrary.rescan();

            // 3. 初始化底层线程池
            TextureThreadPool.init(); // 极速材质加载线程池

            // 5. 启动异步寻路线程池 (千问写的功能)
            com.example.ribbon.pathfinding.AsyncPathfindingManager.getInstance().start();

            // 6. 兼容性检测
            if (ModList.get().isLoaded("modernfix")) {
                if (Config.isFastTextureLoadingEnabled()) {
                    LOGGER.warn("[Ribbon] Detected ModernFix! Ribbon's Fast Texture Loading might conflict with ModernFix's dynamic resources. Automatically falling back to safe mode.");
                } else {
                    LOGGER.info("[Ribbon] ModernFix detected. Fast Texture Loading is currently disabled, no conflict.");
                }
            }

            LOGGER.info("[Ribbon] 客户端启动完成，所有底层引擎已就绪");
        }
    }
}