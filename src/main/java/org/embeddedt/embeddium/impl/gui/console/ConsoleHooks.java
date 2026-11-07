package org.embeddedt.embeddium.impl.gui.console;

import com.example.ribbon.compat.GuiGraphics;

public class ConsoleHooks {
    public static void render(GuiGraphics drawContext, double currentTime) {
        ConsoleRenderer.INSTANCE.update(Console.INSTANCE, currentTime);
        ConsoleRenderer.INSTANCE.draw(drawContext);
    }
}
