package com.example.ribbon.music;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MusicLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/MusicLibrary");
    private static final List<File> TRACKS = new ArrayList<>();

    private MusicLibrary() {}

    public static File getMusicDir() {
        // mod 构造期 Minecraft 实例尚未创建，游戏目录由平台入口注入
        File dir = com.example.ribbon.platform.PlatformInfo.gameDir().resolve("ribbon_music").toFile();
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static void rescan() {
        TRACKS.clear();
        File dir = getMusicDir();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp3"));
        if (files != null) {
            Collections.addAll(TRACKS, files);
            TRACKS.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }
        LOGGER.info("[Ribbon] 音乐库扫描完成，共 {} 首", TRACKS.size());
    }

    public static List<File> getTracks() {
        if (TRACKS.isEmpty()) rescan();
        return Collections.unmodifiableList(TRACKS);
    }

    public static File getTrack(int index) {
        List<File> list = getTracks();
        if (index < 0 || index >= list.size()) return null;
        return list.get(index);
    }

    public static int size() { return getTracks().size(); }
}