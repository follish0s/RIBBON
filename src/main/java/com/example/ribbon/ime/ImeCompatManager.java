/*
 * Copyright (C) 2026 follish0s
 * Licensed under LGPL-3.0-or-later
 */
package com.example.ribbon.ime;

import com.example.ribbon.optimization.RuntimeConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.embeddedt.embeddium.impl.gui.frame.components.SearchTextFieldComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 输入法（IME）兼容修复。
 *
 * <p>解决中文/日文/韩文输入法在 Minecraft GUI 中的三类常见问题：</p>
 * <ol>
 *   <li><b>生僻字 / emoji 变乱码</b>：{@code KeyboardHandler.charTyped} 会把补充平面
 *       codePoint 拆成两个 char 分两次回调，本类暂存高代理项、与低代理项合并后一次写入。</li>
 *   <li><b>拼音字母误触全局快捷键</b>：输入框聚焦时，可打印字符键的 {@code keyPressed}
 *       不再继续传播（字符本身仍由 {@code charTyped} 送入输入框）。</li>
 *   <li><b>组合期 ESC / ENTER 误关界面或误触发按钮</b>：检测到 IME 信号后，
 *       在短时间内保护 ESC / ENTER / TAB / SPACE，避免选字时界面被关掉。</li>
 * </ol>
 *
 * <p>纯 Java 实现，不调用任何平台原生 API；所有启发式都有时间窗与开关兜底，
 * 关闭开关后行为与原版完全一致。</p>
 */
public final class ImeCompatManager {

    /** 非 ASCII 字符提交后的组合窗口：保护 ENTER / TAB / SPACE。 */
    private static final long COMPOSITION_WINDOW_MS = 800L;
    /** IME 伪按键后的窗口：额外保护 ESC（更严格，避免正常退出被吞）。 */
    private static final long PSEUDO_KEY_WINDOW_MS = 300L;
    /** 焦点链递归深度上限，避免异常控件导致死循环。 */
    private static final int MAX_FOCUS_DEPTH = 8;

    private static char pendingHighSurrogate;
    private static long lastImeCharMs;
    private static long lastPseudoKeyMs;

    private ImeCompatManager() {
    }

    /**
     * 规范化一次 {@code charTyped} 输入。
     *
     * @return 应写入输入框的字符串；{@code null} 表示该字符已被消费（等待代理对配对或被过滤）
     */
    public static @Nullable String normalizeInput(char chr) {
        long now = System.currentTimeMillis();
        if (Character.isHighSurrogate(chr)) {
            pendingHighSurrogate = chr;
            noteImeChar(now);
            return null;
        }
        if (Character.isLowSurrogate(chr)) {
            if (pendingHighSurrogate == 0) {
                return null; // 孤立低代理项，丢弃
            }
            String merged = new String(new char[]{pendingHighSurrogate, chr});
            pendingHighSurrogate = 0;
            noteImeChar(now);
            return merged;
        }
        pendingHighSurrogate = 0;
        // 过滤控制字符、DEL 与段落符 §（与原版聊天字符过滤等价，但放行全部可见 Unicode）
        if (chr < 0x20 || chr == 0x7F || chr == 0xA7) {
            return null;
        }
        if (chr >= 0x80) {
            noteImeChar(now);
        }
        return Character.toString(chr);
    }

    private static void noteImeChar(long nowMs) {
        lastImeCharMs = nowMs;
    }

    private static void notePseudoKey(long nowMs) {
        lastPseudoKeyMs = nowMs;
    }

    /** 最近是否提交过非 ASCII 字符（输入法上屏）。 */
    public static boolean isCompositionActive() {
        return lastImeCharMs != 0L
                && System.currentTimeMillis() - lastImeCharMs < COMPOSITION_WINDOW_MS;
    }

    private static boolean isPseudoKeyActive() {
        return lastPseudoKeyMs != 0L
                && System.currentTimeMillis() - lastPseudoKeyMs < PSEUDO_KEY_WINDOW_MS;
    }

    /**
     * 组合期需要保护的按键：吞掉后不会关界面、不会触发默认按钮、不会跳焦点。
     * 仅在「组合保护」开关打开且确实检测到 IME 信号时生效，正常退出界面不受影响。
     */
    public static boolean shouldProtectKey(int keyCode) {
        if (!RuntimeConfig.imeFixEnabled || !RuntimeConfig.imeCompositionGuard) {
            return false;
        }
        // GLFW 在 IME 组合期常发 UNKNOWN(-1) 或 0；这类伪按键本身也要吞掉
        if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            notePseudoKey(System.currentTimeMillis());
            return true;
        }
        boolean composing = isCompositionActive();
        boolean pseudoKey = isPseudoKeyActive();
        if (!composing && !pseudoKey) {
            return false;
        }
        return switch (keyCode) {
            // ESC 是退出路径，只在明确的组合伪按键后的极短窗口里保护，正常退出不受影响
            case GLFW.GLFW_KEY_ESCAPE -> pseudoKey;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER,
                    GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_SPACE -> composing || pseudoKey;
            default -> false;
        };
    }

    /**
     * 输入框聚焦时，可打印字符键的 keyPressed 应被吞掉（字符由 charTyped 处理）。
     * 带 Ctrl / Alt / Super 的组合键一律放行，保证全选、复制、粘贴等快捷键可用。
     */
    public static boolean shouldSwallowTypingKey(int keyCode, int modifiers) {
        if (!RuntimeConfig.imeFixEnabled) {
            return false;
        }
        int reserved = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        if ((modifiers & reserved) != 0) {
            return false;
        }
        return (keyCode >= GLFW.GLFW_KEY_SPACE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT)
                || (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL);
    }

    /** 焦点链上是否存在文本输入框（原版 EditBox 或 Embeddium 搜索框）。 */
    public static boolean isTextFieldFocused(@Nullable GuiEventListener listener) {
        return hasTextField(listener, 0);
    }

    private static boolean hasTextField(@Nullable GuiEventListener listener, int depth) {
        if (listener == null || depth > MAX_FOCUS_DEPTH) {
            return false;
        }
        if (listener instanceof EditBox || listener instanceof SearchTextFieldComponent) {
            return true;
        }
        if (listener instanceof ContainerEventHandler container) {
            return hasTextField(container.getFocused(), depth + 1);
        }
        return false;
    }

    /** 界面关闭 / 世界重载时清空未配对的代理项与组合状态。 */
    public static void reset() {
        pendingHighSurrogate = 0;
        lastImeCharMs = 0L;
        lastPseudoKeyMs = 0L;
    }
}
