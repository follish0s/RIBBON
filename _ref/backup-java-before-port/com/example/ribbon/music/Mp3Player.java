package com.example.ribbon.music;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public final class Mp3Player {

    private static final Logger LOGGER = LoggerFactory.getLogger("Ribbon/Mp3Player");

    private volatile AdvancedPlayer player;
    private volatile Thread playThread;
    private volatile boolean playing = false;
    private volatile boolean paused = false;
    private volatile long playStartTime = 0;
    private volatile long savedMillis = 0;
    private volatile String currentFilePath;
    private volatile Runnable onCompleteCallback;

    public void setOnComplete(Runnable cb) { this.onCompleteCallback = cb; }

    public boolean isPlaying() { return playing && !paused; }
    public boolean isPaused()  { return playing && paused; }
    public boolean isStopped() { return !playing; }

    public long getPositionMs() {
        if (playing && !paused && playStartTime > 0) {
            return savedMillis + (System.currentTimeMillis() - playStartTime);
        }
        return savedMillis;
    }

    public String getCurrentFilePath() { return currentFilePath; }

    public synchronized void play(File file) {
        stop();
        if (file == null || !file.isFile()) {
            LOGGER.warn("[Ribbon] 不是有效的 MP3 文件: {}", file);
            return;
        }
        currentFilePath = file.getAbsolutePath();
        playing = true;
        paused = false;
        savedMillis = 0;
        playStartTime = System.currentTimeMillis();
        startThread(file, 0);
    }

    private void playFromFrame(File file, int startFrame) {
        playing = true;
        paused = false;
        playStartTime = System.currentTimeMillis();
        startThread(file, startFrame);
    }

    private void startThread(File file, int startFrame) {
        playThread = new Thread(() -> {
            try (InputStream is = new FileInputStream(file)) {
                player = new AdvancedPlayer(is);
                if (startFrame > 0) {
                    player.play(startFrame, Integer.MAX_VALUE);
                } else {
                    player.play();
                }
                playing = false;
                if (onCompleteCallback != null) onCompleteCallback.run();
            } catch (JavaLayerException e) {
                LOGGER.error("[Ribbon] MP3 解码失败: {}", file, e);
                playing = false;
            } catch (Exception e) {
                LOGGER.error("[Ribbon] MP3 播放异常: {}", file, e);
                playing = false;
            }
        }, "Ribbon-MP3-Player");
        playThread.setDaemon(true);
        playThread.start();
    }

    public synchronized void pause() {
        if (!playing || paused) return;
        paused = true;
        savedMillis = getPositionMs();
        if (player != null) {
            try { player.close(); } catch (Exception ignored) {}
        }
    }

    public synchronized void resume() {
        if (!playing || !paused) return;
        String path = currentFilePath;
        if (path == null) return;
        int startFrame = (int) (savedMillis / 26.0);
        playFromFrame(new File(path), startFrame);
    }

    public synchronized void stop() {
        playing = false;
        paused = false;
        savedMillis = 0;
        playStartTime = 0;
        if (player != null) {
            try { player.close(); } catch (Exception ignored) {}
            player = null;
        }
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
    }
}