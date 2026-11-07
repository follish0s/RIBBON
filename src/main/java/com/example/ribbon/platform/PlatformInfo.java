package com.example.ribbon.platform;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * 平台信息门面：游戏目录与 mod 加载查询。
 *
 * <p>共享代码不再直接调用 {@code FMLPaths} / {@code ModList} / {@code FabricLoader}，
 * 由各平台入口在启动早期注入。</p>
 */
public final class PlatformInfo {

    private static volatile Path gameDir;
    private static volatile Predicate<String> modLoaded = id -> false;

    private PlatformInfo() {
    }

    /** NeoForge：{@code FMLPaths.GAMEDIR} + {@code ModList.isLoaded}。 */
    /** Fabric：{@code FabricLoader.getGameDir()} + {@code isModLoaded}。 */
    public static void init(Path dir, Predicate<String> loader) {
        gameDir = dir;
        modLoaded = loader != null ? loader : id -> false;
    }

    public static Path gameDir() {
        Path d = gameDir;
        return d != null ? d : Path.of(".");
    }

    public static boolean isModLoaded(String modId) {
        return modLoaded.test(modId);
    }
}
