package com.example.ribbon;

import com.example.ribbon.music.MusicPlayerManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = "ribbon", value = Dist.CLIENT)
public class RibbonDebugHandler {

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getDebugOverlay().showDebugScreen() || mc.options.hideGui) return;
        if (!Config.isShowAdvancedDebug()) return;

        List<String> left = event.getLeft();
        List<String> right = event.getRight();

        String divider = "§8§m                                ";

        // ================== 左侧：系统与硬件 ==================
        left.add("");
        left.add(divider);
        left.add("§b§l✦ Ribbon 环境信息 ✦");

        String mcVersion = SharedConstants.getCurrentVersion().getName();
        String ribbonVersion = ModList.get().getModContainerById("ribbon")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("0.20.7");
        left.add(" §fMC: §a" + mcVersion + " §7| §fRibbon: §a" + ribbonVersion);

        // 解释伪装机制，避免玩家困惑
        left.add(" §7(伪装为 Embeddium 运行中)");

        left.add(" §fMod数量: §a" + ModList.get().size() + " §7| §f视距: §a" + mc.options.renderDistance().get());

        String cpuInfo = System.getenv("PROCESSOR_IDENTIFIER");
        if (cpuInfo == null) cpuInfo = System.getProperty("os.arch");
        left.add(" §fCPU: §e" + cpuInfo);
        left.add(" §f核心数: §a" + Runtime.getRuntime().availableProcessors() + " 核");

        String gpu = GL11.glGetString(GL11.GL_RENDERER);
        left.add(" §fGPU: §e" + (gpu != null ? gpu : "未知"));

        long monitor = GLFW.glfwGetPrimaryMonitor();
        String monitorName = monitor != 0 ? GLFW.glfwGetMonitorName(monitor) : "未知";
        int refreshRate = mc.getWindow().getRefreshRate();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        left.add(" §f显示器: §e" + monitorName);
        left.add(" §f分辨率: §a" + width + "x" + height + " §7@ §a" + refreshRate + "Hz");

        LocalPlayer player = mc.player;
        if (player != null) {
            BlockPos pos = player.blockPosition();
            left.add("");
            left.add("§b§l✦ 玩家坐标 ✦");
            left.add(String.format(" §fXYZ: §a%.1f, %.1f, %.1f", player.getX(), player.getY(), player.getZ()));

            var biome = mc.level.getBiome(pos);
            biome.unwrapKey().ifPresent(key -> {
                left.add(" §f群系: §a" + key.location());
            });
        }

        // ================== 右侧：渲染与依赖 ==================
        right.add("");
        right.add(divider);
        right.add("§b§l✦ 渲染管线与接管 ✦");

        boolean hasIris = ModList.get().isLoaded("iris");
        boolean hasOculus = ModList.get().isLoaded("oculus");
        if (hasIris) right.add(" §f光影: §aIris §7(已接管)");
        else if (hasOculus) right.add(" §f光影: §aOculus §7(已接管)");
        else if (ModList.get().isLoaded("optifine")) right.add(" §f光影: §eOptiFine");
        else right.add(" §f光影: §7未安装");

        boolean hasIntruder = false;
        if (ModList.get().isLoaded("monocle")) { right.add(" §e⚡ Monocle §7(干预渲染)"); hasIntruder = true; }
        if (ModList.get().isLoaded("distanthorizons")) { right.add(" §e⚡ DH §7(接管地形)"); hasIntruder = true; }
        if (ModList.get().isLoaded("modernfix")) { right.add(" §e⚡ ModernFix §7(底层Mixin)"); hasIntruder = true; }
        if (ModList.get().isLoaded("immediatelyfast")) { right.add(" §e⚡ ImmediatelyFast §7(批处理)"); hasIntruder = true; }
        if (!hasIntruder) { right.add(" §a✔ 无外部渲染接管"); }

        // 依赖侦测
        right.add("");
        right.add("§b§l✦ 依赖 Ribbon 的模组 ✦");
        Set<String> dependents = getDependentMods();
        if (dependents.isEmpty()) {
            right.add(" §7无其他模组声明依赖");
        } else {
            for (String modName : dependents) {
                right.add(" §a↳ " + modName);
            }
        }

        boolean conflictFound = false;
        if (ModList.get().isLoaded("radium")) { right.add(" §c⚠ 致命: Radium"); conflictFound = true; }
        if (!conflictFound) { right.add(" §a✔ 无已知致命冲突"); }

        right.add("");
        right.add("§b§l✦ Ribbon 功能状态 ✦");
        String cullStatus = " §f剔除: ";
        cullStatus += (Config.isEntityCullingEnabled() ? "§a实体 " : "§c实体 ");
        cullStatus += (Config.isParticleCullingEnabled() ? "§a粒子 " : "§c粒子 ");
        right.add(cullStatus);
        right.add(" §f描边: " + (Config.RAINBOW_BLOCK_OUTLINE.get() ? "§a开启 §7(" + Config.OUTLINE_COLOR_MODE.get().name() + ")" : "§c关闭"));

        if (Config.isMusicPlayerEnabled()) {
            var playerInstance = MusicPlayerManager.getPlayer();
            String musicStatus = playerInstance.isPlaying() ? "§a播放中" : (playerInstance.isPaused() ? "§e已暂停" : "§7已停止");
            right.add(" §f音乐: " + musicStatus);
        }
    }

    /**
     * 精准侦测：找出真正把 embeddium 或 ribbon 写进前置声明的模组
     */
    private static Set<String> getDependentMods() {
        // 使用 LinkedHashSet 自动去重，同时保持加载顺序
        Set<String> dependents = new LinkedHashSet<>();

        ModList.get().getMods().forEach(modInfo -> {
            String modId = modInfo.getModId();

            // 铁律：绝对不能把自己的伪装身份（sodium）和自己（ribbon）列进去
            if (modId.equals("ribbon") || modId.equals("sodium")) return;

            // 检查该模组的依赖列表中是否有 ribbon 或 sodium
            boolean dependsOnUs = modInfo.getDependencies().stream()
                    .anyMatch(dep -> dep.getModId().equals("sodium") || dep.getModId().equals("ribbon"));

            if (dependsOnUs) {
                // 如果模组显示名和 ID 差别很大，以显示名为准
                String displayName = modInfo.getDisplayName();
                if (displayName == null || displayName.isEmpty()) {
                    displayName = modId;
                }
                dependents.add(displayName);
            }
        });
        return dependents;
    }
}