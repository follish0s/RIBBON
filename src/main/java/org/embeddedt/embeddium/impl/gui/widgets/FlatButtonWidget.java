package org.embeddedt.embeddium.impl.gui.widgets;

import org.embeddedt.embeddium.api.math.Dim2i;
import net.minecraft.client.gui.ComponentPath;
import com.example.ribbon.compat.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import com.example.ribbon.compat.CommonInputs;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.embeddedt.embeddium.impl.gui.theme.DefaultColors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;

public class FlatButtonWidget extends AbstractWidget implements Renderable {
    protected final Dim2i dim;
    private final Runnable action;

    private @NotNull Style style = Style.defaults();

    private boolean selected;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean leftAligned;

    private Component label;

    /** 悬停发光平滑值（0=静止，1=完全高亮），指数缓动产生呼吸感。 */
    private float hoverGlow;
    private long lastFrameMs;

    public FlatButtonWidget(Dim2i dim, Component label, Runnable action) {
        this.dim = dim;
        this.label = label;
        this.action = action;
    }

    /** 每帧推进悬停动画，返回 0..1 的平滑发光值。 */
    protected float advanceHoverGlow() {
        long now = net.minecraft.util.Util.getMillis();
        if (this.lastFrameMs == 0L) {
            this.lastFrameMs = now;
        }
        float dt = Math.min(0.1f, (now - this.lastFrameMs) / 1000.0f);
        this.lastFrameMs = now;
        float target = this.hovered ? 1.0f : 0.0f;
        this.hoverGlow += (target - this.hoverGlow) * Math.min(1.0f, dt * 12.0f);
        if (Math.abs(this.hoverGlow - target) < 0.01f) {
            this.hoverGlow = target;
        }
        return this.hoverGlow;
    }

    protected int getLeftAlignedTextOffset() {
        return 10;
    }

    protected boolean isHovered(int mouseX, int mouseY) {
        return this.dim.containsCursor(mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        if (!this.visible) {
            return;
        }

        this.hovered = this.isHovered(mouseX, mouseY);
        float glow = this.advanceHoverGlow();

        int backgroundColor = this.enabled ? (this.hovered ? this.style.bgHovered : this.style.bgDefault) : this.style.bgDisabled;
        int textColor = this.enabled ? this.style.textDefault : this.style.textDisabled;

        int strWidth = this.font.width(this.label);

        if (com.example.ribbon.optimization.RuntimeConfig.liquidGlassEnabled
                && com.example.ribbon.glass.LiquidGlassTheme.drawPanel(drawContext,
                        this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(),
                        this.enabled ? (1.0f + glow + (this.selected ? 0.35f : 0.0f)) : 0.55f)) {
            // 液态玻璃胶囊：悬停平滑增亮、选中态常亮、禁用减淡
        } else {
            this.drawRect(drawContext, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), backgroundColor);
        }
        int textX;
        if (this.leftAligned) {
            textX = this.dim.x() + this.getLeftAlignedTextOffset();
        } else {
            textX = this.dim.getCenterX() - (strWidth / 2);
        }
        this.drawString(drawContext, this.label, textX, this.dim.getCenterY() - 4, textColor);

        if (this.enabled && this.selected) {
            // 选中强调条：玻璃圆角下上下各内缩 2px，避免压住圆角
            this.drawRect(drawContext, this.dim.x(), this.leftAligned ? this.dim.y() + 2 : (this.dim.getLimitY() - 1),
                    this.leftAligned ? (this.dim.x() + 1) : this.dim.getLimitX(),
                    this.leftAligned ? this.dim.getLimitY() - 2 : this.dim.getLimitY(), DefaultColors.ELEMENT_ACTIVATED);
        }
        if (this.enabled && this.isFocused()) {
            this.drawBorder(drawContext, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
        }
    }

    public void setStyle(@NotNull Style style) {
        Objects.requireNonNull(style);

        this.style = style;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setLeftAligned(boolean leftAligned) {
        this.leftAligned = leftAligned;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent __evt, boolean __doubleClick) {
        double mouseX = __evt.x();
        double mouseY = __evt.y();
        int button = __evt.input();
        if (!this.enabled || !this.visible) {
            return false;
        }

        if (button == 0 && this.dim.containsCursor(mouseX, mouseY)) {
            doAction();

            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent __evt) {
        int keyCode = __evt.key();
        int scanCode = 0;
        int modifiers = __evt.modifiers();
        if (!this.isFocused())
            return false;

        if (CommonInputs.selected(keyCode)) {
            doAction();
            return true;
        }

        return false;
    }

    private void doAction() {
        this.action.run();
        this.playClickSound();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setLabel(Component text) {
        this.label = text;
    }

    public Component getLabel() {
        return this.label;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        if (!this.enabled || !this.visible)
            return null;
        return super.nextFocusPath(navigation);
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.dim.x(), this.dim.y(), this.dim.width(), this.dim.height());
    }

    public Dim2i getDimensions() {
        return this.dim;
    }

    public static class Style {
        public int bgHovered, bgDefault, bgDisabled;
        public int textDefault, textDisabled;

        public static Style defaults() {
            var style = new Style();
            style.bgHovered = 0xE0202020;
            style.bgDefault = 0x90000000;
            style.bgDisabled = 0x60000000;
            style.textDefault = 0xFFFFFFFF;
            style.textDisabled = 0x90FFFFFF;

            return style;
        }
    }
}
