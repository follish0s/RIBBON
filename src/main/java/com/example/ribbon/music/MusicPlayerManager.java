package com.example.ribbon.music;

import net.minecraft.client.Minecraft;

import java.io.File;

public final class MusicPlayerManager {

    public enum LoopMode { NONE, ALL, ONE }

    private static final Mp3Player PLAYER = new Mp3Player();
    private static int currentIndex = -1;
    private static LoopMode loopMode = LoopMode.ALL;

    private MusicPlayerManager() {}

    public static Mp3Player getPlayer() { return PLAYER; }
    public static int getCurrentIndex() { return currentIndex; }
    public static LoopMode getLoopMode() { return loopMode; }
    public static void setLoopMode(LoopMode mode) { loopMode = mode; }

    public static void init() {
        PLAYER.setOnComplete(() -> Minecraft.getInstance().execute(() -> {
            switch (loopMode) {
                case ONE -> playIndex(currentIndex);
                case ALL -> next();
                case NONE -> { }
            }
        }));
    }

    public static void playIndex(int index) {
        File track = MusicLibrary.getTrack(index);
        if (track == null) return;
        currentIndex = index;
        PLAYER.play(track);
    }

    public static void playPause() {
        if (PLAYER.isPlaying()) {
            PLAYER.pause();
        } else if (PLAYER.isPaused()) {
            PLAYER.resume();
        } else if (currentIndex >= 0) {
            playIndex(currentIndex);
        } else if (MusicLibrary.size() > 0) {
            playIndex(0);
        }
    }

    public static void next() {
        int size = MusicLibrary.size();
        if (size == 0) return;
        playIndex((currentIndex + 1) % size);
    }

    public static void prev() {
        int size = MusicLibrary.size();
        if (size == 0) return;
        playIndex((currentIndex - 1 + size) % size);
    }

    public static void stop() {
        PLAYER.stop();
        currentIndex = -1;
    }
}