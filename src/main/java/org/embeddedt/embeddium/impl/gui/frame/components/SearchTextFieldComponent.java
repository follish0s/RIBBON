package org.embeddedt.embeddium.impl.gui.frame.components;

import org.embeddedt.embeddium.api.options.structure.OptionPage;
import org.embeddedt.embeddium.impl.gui.widgets.AbstractWidget;
import org.embeddedt.embeddium.api.math.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import com.example.ribbon.compat.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

public class SearchTextFieldComponent extends AbstractWidget {
    protected final Dim2i dim;
    protected final List<OptionPage> pages;
    private final Font textRenderer = Minecraft.getInstance().font;
    private final BiFunction<String, Integer, FormattedCharSequence> renderTextProvider = (string, firstCharacterIndex) -> FormattedCharSequence.forward(string, Style.EMPTY);
    private final SearchTextFieldModel model;

    public SearchTextFieldComponent(Dim2i dim, List<OptionPage> pages, SearchTextFieldModel model) {
        this.dim = dim;
        this.pages = pages;
        this.model = model;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.model.innerWidth = this.getInnerWidth();

        if (!this.isVisible()) {
            return;
        }
        if (this.model.text.isEmpty()) {
            this.drawString(context, Component.translatable("embeddium.search_bar_empty"), this.dim.x() + 6, this.dim.y() + 6, 0xFFAAAAAA);
        }

        if (com.example.ribbon.optimization.RuntimeConfig.liquidGlassEnabled
                && com.example.ribbon.glass.LiquidGlassTheme.drawPanel(context,
                        this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(),
                        this.isFocused() ? 1.8F : 1.0F)) {
            if (this.isFocused()) {
                this.drawBorder(context, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), 0x90FFFFFF);
            }
        } else {
            this.drawRect(context, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), this.isFocused() ? 0xE0000000 : 0x90000000);
        }
        int j = this.model.selectionStart - this.model.firstCharacterIndex;
        int k = this.model.selectionEnd - this.model.firstCharacterIndex;
        String string = this.textRenderer.plainSubstrByWidth(this.model.text.substring(this.model.firstCharacterIndex), this.getInnerWidth());
        boolean bl = j >= 0 && j <= string.length();
        int l = this.dim.x() + 6;
        int m = this.dim.y() + 6;
        int n = l;
        if (k > string.length()) {
            k = string.length();
        }
        if (!string.isEmpty()) {
            String string2 = bl ? string.substring(0, j) : string;
            n = context.drawString(this.textRenderer, this.renderTextProvider.apply(string2, this.model.firstCharacterIndex), n, m, 0xFFFFFFFF);
        }
        boolean bl3 = this.model.selectionStart < this.model.text.length() || this.model.text.length() >= this.model.getMaxLength();
        int o = n;
        if (!bl) {
            o = j > 0 ? l + this.dim.width() - 12 : l;
        } else if (bl3) {
            --o;
            --n;
        }
        if (!string.isEmpty() && bl && j < string.length()) {
            context.drawString(this.textRenderer, this.renderTextProvider.apply(string.substring(j), this.model.selectionStart), n, m, 0xFFFFFFFF);
        }
        // Cursor
        if (this.isFocused()) {
            context.fill(o, m - 1, o + 1, m + 1 + this.textRenderer.lineHeight, -3092272);
        }
        // Highlighted text
        if (k != j) {
            int p = l + this.textRenderer.width(string.substring(0, k));
            this.drawSelectionHighlight(context, o, m - 1, p - 1, m + 1 + this.textRenderer.lineHeight);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent __evt, boolean __doubleClick) {
        double mouseX = __evt.x();
        double mouseY = __evt.y();
        int button = __evt.input();
        int i = Mth.floor(mouseX) - this.dim.x() - 6;
        String string = this.textRenderer.plainSubstrByWidth(this.model.text.substring(this.model.firstCharacterIndex), this.getInnerWidth());
        this.model.setCursor(this.textRenderer.plainSubstrByWidth(string, i).length() + this.model.firstCharacterIndex);

        this.setFocused(this.dim.containsCursor(mouseX, mouseY));
        return this.isFocused();
    }

    // fixme: this is here because of 0.5.1's https://github.com/CaffeineMC/sodium-fabric/commit/20006a85fb7a64889f507eb13521e55693ae0d7e
    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    private void drawSelectionHighlight(GuiGraphics context, int x1, int y1, int x2, int y2) {
        int i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }
        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }
        if (x2 > this.dim.x() + this.dim.width()) {
            x2 = this.dim.x() + this.dim.width();
        }
        if (x1 > this.dim.x() + this.dim.width()) {
            x1 = this.dim.x() + this.dim.width();
        }
        context.fill(x1, y1, x2, y2, -16776961);
    }
    

    public boolean isActive() {
        return this.isVisible() && this.isFocused() && this.isEditable();
    }

    @Override
    public boolean charTyped(CharacterEvent __evt) {
        char chr = (char) __evt.codepoint();
        int modifiers = 0;
        if (!this.isActive()) {
            return false;
        }

        String input;
        if (com.example.ribbon.optimization.RuntimeConfig.imeFixEnabled) {
            // IME 兼容：放行全部可见 Unicode（含中文），合并补充平面字符的代理对
            input = com.example.ribbon.ime.ImeCompatManager.normalizeInput(chr);
            if (input == null) {
                return true; // 已消费：等待低代理项，或过滤掉控制字符
            }
        } else {
            if (!StringUtil.isAllowedChatCharacter(chr)) {
                return false;
            }
            input = Character.toString(chr);
        }

        if (this.model.editable) {
            this.model.write(input);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent __evt) {
        int keyCode = __evt.key();
        int scanCode = 0;
        int modifiers = __evt.modifiers();
        if (!this.isActive()) {
            return false;
        } else {
            this.model.selecting = Minecraft.getInstance().hasShiftDown();
            if (com.example.ribbon.compat.KeyCompat.isSelectAll(__evt)) {
                this.model.setCursorToEnd();
                this.model.setSelectionEnd(0);
                return true;
            } else if (com.example.ribbon.compat.KeyCompat.isCopy(__evt)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(this.model.getSelectedText());
                return true;
            } else if (com.example.ribbon.compat.KeyCompat.isPaste(__evt)) {
                if (this.model.editable) {
                    this.model.write(Minecraft.getInstance().keyboardHandler.getClipboard());
                }

                return true;
            } else if (com.example.ribbon.compat.KeyCompat.isCut(__evt)) {
                Minecraft.getInstance().keyboardHandler.setClipboard(this.model.getSelectedText());
                if (this.model.editable) {
                    this.model.write("");
                }

                return true;
            } else {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_BACKSPACE -> {
                        if (this.model.editable) {
                            this.model.selecting = false;
                            this.model.erase(-1);
                            this.model.selecting = Minecraft.getInstance().hasShiftDown();
                        }
                        return true;
                    }
                    case GLFW.GLFW_KEY_DELETE -> {
                        if (this.model.editable) {
                            this.model.selecting = false;
                            this.model.erase(1);
                            this.model.selecting = Minecraft.getInstance().hasShiftDown();
                        }
                        return true;
                    }
                    case GLFW.GLFW_KEY_RIGHT -> {
                        if (com.example.ribbon.compat.KeyCompat.hasControlDown(__evt)) {
                            this.model.setCursor(this.model.getWordSkipPosition(1));
                        } else {
                            this.model.moveCursor(1);
                        }
                        boolean state = this.model.getCursor() != this.model.lastCursorPosition && this.model.getCursor() != this.model.text.length() + 1;
                        this.model.lastCursorPosition = this.model.getCursor();
                        return state;
                    }
                    case GLFW.GLFW_KEY_LEFT -> {
                        if (com.example.ribbon.compat.KeyCompat.hasControlDown(__evt)) {
                            this.model.setCursor(this.model.getWordSkipPosition(-1));
                        } else {
                            this.model.moveCursor(-1);
                        }
                        boolean state = this.model.getCursor() != this.model.lastCursorPosition && this.model.getCursor() != 0;
                        this.model.lastCursorPosition = this.model.getCursor();
                        return state;
                    }
                    case GLFW.GLFW_KEY_HOME -> {
                        this.model.setCursorToStart();
                        return true;
                    }
                    case GLFW.GLFW_KEY_END -> {
                        this.model.setCursorToEnd();
                        return true;
                    }
                    default -> {
                        // 输入法兼容：拼音/候选的可打印按键不再传播到全局快捷键或按钮
                        if (com.example.ribbon.optimization.RuntimeConfig.imeFixEnabled
                                && com.example.ribbon.ime.ImeCompatManager.shouldSwallowTypingKey(keyCode, modifiers)) {
                            return true;
                        }
                        return false;
                    }
                }
            }
        }
    }

    public boolean isVisible() {
        return model.visible;
    }

    public boolean isEditable() {
        return model.editable;
    }

    public int getInnerWidth() {
        return this.dim.width() - 12;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        if (!this.model.visible)
            return null;
        return super.nextFocusPath(navigation);
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.dim.x(), this.dim.y(), this.dim.width(), this.dim.height());
    }

    /** 26.2：容器事件分发改为 getChildAt 命中判定，控件必须覆盖 isMouseOver 才能接收点击。 */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }
}
