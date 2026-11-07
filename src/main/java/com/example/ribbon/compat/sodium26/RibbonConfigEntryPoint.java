package com.example.ribbon.compat.sodium26;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPointForge;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.impl.gui.EmbeddiumVideoOptionsScreen;

/**
 * Sodium 26.2 视频设置入口。
 *
 * <p>26.2 的原版「视频设置」按钮被 Sodium 的 {@code OptionsScreenMixin} 接管：
 * 它注入 {@code OptionsScreen.lambda$init$3} 并直接返回 Sodium 自己的
 * {@code net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen}，原版
 * {@code net.minecraft.client.gui.screens.options.VideoSettingsScreen} 实际不会出现。
 * 因此往原版视频设置屏注入按钮是死路（该屏永远不被打开）。</p>
 *
 * <p>Sodium 26.2 提供了官方配置扩展点：{@code ConfigLoaderForge.collectConfigEntryPoints()}
 * 通过 {@code ModFileScanData} 注解扫描，发现带 {@link ConfigEntryPointForge} 的类后调用
 * {@code ConfigManager.registerConfigEntryPoint}，再在客户端初始化时回调
 * {@link #registerConfigLate}。注册的页面会出现在 Sodium 视频设置左侧页面列表
 * （Iris 的 {@code IrisConfig} 用的就是同一条路径）。</p>
 *
 * <p>这里只注册一个外部页面（{@code ExternalPageBuilder} + {@code setScreenConsumer}），
 * 点击后打开 Ribbon 自己的完整设置界面（7 个页面），避免重复实现 Sodium 风格控件。</p>
 */
@ConfigEntryPointForge("ribbon")
public class RibbonConfigEntryPoint implements ConfigEntryPoint {

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        ModOptionsBuilder mod = builder.registerOwnModOptions().setName("Ribbon");
        mod.addPage(builder.createExternalPage()
                .setName(Component.literal("Ribbon"))
                .setScreenConsumer(previous -> Minecraft.getInstance().gui.setScreen(
                        new EmbeddiumVideoOptionsScreen(previous, EmbeddiumVideoOptionsScreen.makePages()))));
    }
}
