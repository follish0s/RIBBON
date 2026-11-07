package com.example.ribbon.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.impl.gui.EmbeddiumVideoOptionsScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Fabric 平台入口（26.2）。
 *
 * <p>26.2 起游戏不再混淆，Fabric 与 NeoForge 运行时都是 Mojang 官方映射，
 * 因此本 jar 的共享字节码可在两个加载器上直接运行，不需要 remap 或 intermediary。</p>
 *
 * <p>平台隔离方式：本类只由 {@code fabric.mod.json} 的 {@code client} 入口点加载，
 * NeoForge 侧永远不会触碰它；反过来 NeoForge 的 {@code @Mod} 入口在 Fabric 上也不会被加载。</p>
 *
 * <p>当前阶段（Stage 1）：完成加载链路与设置界面入口；配置项迁移（Config 门面化）在下一阶段，
 * 因此这里传入空的页面列表，界面可打开但选项尚未填充。</p>
 */
public final class RibbonFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ribbon");

    private static Method addRenderableWidget;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Ribbon] Fabric 平台初始化（26.2 双平台通用 jar）");

        // 配置：properties 后端 → 刷新热路径快照 → 注册设置页 → 自检
        // （对应 NeoForge 侧 ModConfigEvent 的加载链）
        try {
            com.example.ribbon.platform.PlatformInfo.init(
                    net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(),
                    id -> net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(id));
            ConfigFabric.init();
            com.example.ribbon.optimization.RuntimeConfig.refresh();
            com.example.ribbon.RibbonOptions.init();
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] Fabric 配置初始化失败，将使用默认值", t);
        }
        try {
            com.example.ribbon.RibbonOptions.selfCheck();
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] Fabric 设置页自检异常", t);
        }

        // 游戏内功能：HUD 渲染 + 客户端/服务端 tick（与 NeoForgeBridge 对应的另一半）
        try {
            RibbonFabricHooks.register();
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] Fabric HUD/tick 钩子注册失败", t);
        }

        // 主菜单右下角入口。Fabric 没有 NeoForge 的 ScreenEvent.Init.Post.addListener，
        // 用 fabric-screen-api-v1 的 AFTER_INIT 等价实现。
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) {
                return;
            }
            Button button = Button.builder(
                            Component.literal("Ribbon 设置"),
                            b -> openSettings(screen))
                    .bounds(scaledWidth - 84, scaledHeight - 26, 80, 20)
                    .build();
            addWidget(screen, button);
        });
    }

    private static void openSettings(Screen from) {
        try {
            Minecraft.getInstance().gui.setScreen(
                    new EmbeddiumVideoOptionsScreen(from, EmbeddiumVideoOptionsScreen.makePages()));
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] Fabric: 打开设置界面失败", t);
        }
    }

    /**
     * {@code Screen#addRenderableWidget} 是 protected，Fabric 侧没有暴露等价 API，
     * 通过反射调用（一次解析后缓存）。失败只记日志，绝不影响启动。
     */
    private static void addWidget(Screen screen, GuiEventListener widget) {
        try {
            if (addRenderableWidget == null) {
                for (Method m : Screen.class.getDeclaredMethods()) {
                    if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        addRenderableWidget = m;
                        break;
                    }
                }
            }
            if (addRenderableWidget == null) {
                LOGGER.warn("[Ribbon] Fabric: 未找到 Screen#addRenderableWidget，跳过按钮注入");
                return;
            }
            addRenderableWidget.invoke(screen, widget);
        } catch (Throwable t) {
            LOGGER.warn("[Ribbon] Fabric: 注入设置按钮失败", t);
        }
    }
}
