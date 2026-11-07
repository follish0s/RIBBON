package org.embeddedt.embeddium.api.options.control;

import org.embeddedt.embeddium.api.options.structure.Option;
import org.embeddedt.embeddium.api.options.structure.OptionControlElement;
import org.embeddedt.embeddium.impl.gui.widgets.AbstractWidget;
import org.embeddedt.embeddium.impl.gui.widgets.FlatButtonWidget;
import org.embeddedt.embeddium.api.math.Dim2i;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ControlElement<T> extends AbstractWidget implements OptionControlElement<T> {
    protected final Option<T> option;

    protected final Dim2i dim;

    private @NotNull FlatButtonWidget.Style style = FlatButtonWidget.Style.defaults();

    /** 行悬停高亮平滑值（0..1），指数缓动。 */
    private float rowGlow;
    private long lastFrameMs;

    public ControlElement(Option<T> option, Dim2i dim) {
        this.option = option;
        this.dim = dim;
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        String name = this.option.getName().getString();
        String label;

        if ((this.hovered || this.isFocused()) && this.font.width(name) > (this.dim.width() - this.option.getControl().getMaxWidth())) {
            name = name.substring(0, Math.min(name.length(), 10)) + "...";
        }

        if (this.option.isAvailable()) {
            if (this.option.hasChanged()) {
                label = ChatFormatting.ITALIC + name + " *";
            } else {
                label = ChatFormatting.WHITE + name;
            }
        } else {
            label = String.valueOf(ChatFormatting.GRAY) + ChatFormatting.STRIKETHROUGH + name;
        }

        this.hovered = this.dim.containsCursor(mouseX, mouseY);

        // 悬停高亮缓动：玻璃模式下平滑增亮，普通模式保持原版瞬时切换
        long now = net.minecraft.Util.getMillis();
        if (this.lastFrameMs == 0L) {
            this.lastFrameMs = now;
        }
        float dt = Math.min(0.1f, (now - this.lastFrameMs) / 1000.0f);
        this.lastFrameMs = now;
        float target = this.hovered ? 1.0f : 0.0f;
        this.rowGlow += (target - this.rowGlow) * Math.min(1.0f, dt * 12.0f);
        if (Math.abs(this.rowGlow - target) < 0.01f) {
            this.rowGlow = target;
        }

        // 液态玻璃模式下，选项行改为透明底 + 悬停白色高亮（避免深色行压住玻璃底板）
        int rowBackground;
        if (com.example.ribbon.optimization.RuntimeConfig.liquidGlassEnabled) {
            int alpha = (int) (0x2A * this.rowGlow);
            rowBackground = (alpha << 24) | 0x00FFFFFF;
        } else {
            rowBackground = this.hovered ? style.bgHovered : style.bgDefault;
        }
        this.drawRect(drawContext, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), rowBackground);
        this.drawString(drawContext, label, this.dim.x() + 6, this.dim.getCenterY() - 4, style.textDefault);

        if (this.isFocused()) {
            this.drawBorder(drawContext, this.dim.x(), this.dim.y(), this.dim.getLimitX(), this.dim.getLimitY(), -1);
        }
    }

    @Override
    public Option<T> getOption() {
        return this.option;
    }

    public Dim2i getDimensions() {
        return this.dim;
    }

    @Override
    public @Nullable ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        if (!this.option.isAvailable())
            return null;
        return super.nextFocusPath(navigation);
    }

    @Override
    public ScreenRectangle getRectangle() {
        return new ScreenRectangle(this.dim.x(), this.dim.y(), this.dim.width(), this.dim.height());
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return this.dim.containsCursor(x, y);
    }
}
