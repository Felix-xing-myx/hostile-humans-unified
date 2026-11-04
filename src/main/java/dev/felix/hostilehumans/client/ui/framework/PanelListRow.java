package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * Two-line list row: title and metadata, with an optional right aligned value and progress bar.
 *
 * <p>Shared by the commission board and the task list, which both need the reference overview's
 * master/detail rows rather than full-width buttons carrying a joined-up sentence.
 */
public final class PanelListRow extends PanelButton {
    public static final int HEIGHT = 30, VALUE = 40;

    private final String title, metadata, value;
    private final double progress, target;
    private final int tone;
    private String clippedTitle = "", clippedMetadata = "", clippedValue = "";

    public PanelListRow(
            String title,
            String metadata,
            String value,
            double progress,
            double target,
            int tone,
            Component narration,
            OnPress press,
            Supplier<UiTheme> theme) {
        super(narration, press, theme);
        this.title = title;
        this.metadata = metadata;
        this.value = value;
        this.progress = progress;
        this.target = target;
        this.tone = tone;
    }

    @Override
    public int measureHeight(int width) {
        var font = Minecraft.getInstance().font;
        int valueWidth = value.isEmpty() ? 0 : Math.min(VALUE, font.width(value)) + 6;
        clippedTitle =
                font.plainSubstrByWidth(title, Math.max(1, width - 18 - valueWidth));
        clippedValue = value.isEmpty() ? "" : font.plainSubstrByWidth(value, VALUE);
        clippedMetadata = font.plainSubstrByWidth(metadata, Math.max(1, width - 18));
        return HEIGHT;
    }

    @Override
    public void place(UiRect b) {
        super.place(b);
        setHeight(HEIGHT);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        UiTheme t = theme.get();
        boolean hover = isHovered && inViewport(mx, my);
        int background =
                selected()
                        ? UiColor.mix(t.inset(), 0xFFFFFFFF, .05F)
                        : hover ? UiColor.mix(t.inset(), 0xFFFFFFFF, .028F) : t.inset();
        UiDraw.rect(g, getX(), getY(), width, height, background);
        UiDraw.rect(g, getX(), getY() + height - 1, width, 1, UiColor.alpha(0xFFFFFFFF, .04F));
        if (selected()) UiDraw.rect(g, getX(), getY() + 3, 2, height - 6, t.accent());
        if (isFocused()) UiDraw.outline(g, getX(), getY(), width, height, t.accent());

        var font = Minecraft.getInstance().font;
        int x = getX() + 9, y = getY() + 5;
        UiDraw.rect(g, x, y + 3, 3, 3, tone);
        g.drawString(font, clippedTitle, x + 7, y, selected() ? t.accent() : t.text(), false);
        if (!clippedValue.isEmpty())
            g.drawString(
                    font,
                    clippedValue,
                    getX() + width - 8 - font.width(clippedValue),
                    y,
                    t.muted(),
                    false);
        g.drawString(font, clippedMetadata, x + 7, y + 12, t.muted(), false);
        if (target > 0) {
            int barX = getX() + 9, barW = Math.max(20, width - 18);
            UiDraw.meter(g, barX, getY() + height - 7, barW, 3, progress, target, t.accent(), true);
        }
    }
}