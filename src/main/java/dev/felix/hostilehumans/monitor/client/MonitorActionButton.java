package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native input/narration with the shared command-panel skin, never the vanilla button texture. */
final class MonitorActionButton extends Button {
    private final IntSupplier accent;
    private final BooleanSupplier selected;
    private final boolean danger;
    MonitorActionButton(int x, int y, int width, int height, Component label, OnPress press,
            IntSupplier accent, BooleanSupplier selected, boolean danger) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
        this.accent = accent; this.selected = selected; this.danger = danger;
    }
    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int available = Math.max(1, getWidth() - 12);
        String full = getMessage().getString();
        String label = font.width(full) <= available ? full
                : font.plainSubstrByWidth(full, Math.max(1, available - font.width("…"))) + "…";
        var theme=UiThemes.faction("").withAccent(accent.getAsInt());
        UiDraw.control(graphics,getX(),getY(),getWidth(),getHeight(),theme,isHoveredOrFocused(),active,
                selected.getAsBoolean(),danger,isFocused());
        int color=!active?theme.disabled():danger?theme.danger():theme.text();
        graphics.drawCenteredString(font,label,getX()+getWidth()/2,getY()+(getHeight()-8)/2,color);
    }
}
