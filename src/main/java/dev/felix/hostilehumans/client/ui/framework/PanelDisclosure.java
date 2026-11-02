package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Inline disclosure row: label on the left, value on the right, explained detail lines underneath.
 * The explanation is in flow and narrated, never a hover popup.
 */
public final class PanelDisclosure extends PanelButton {
    public static final int HEIGHT = 16, DETAIL = 12;

    private Component heading = Component.empty(), value = Component.empty();
    private List<Component> details = List.of();
    private List<FormattedCharSequence> headings = List.of(),
            values = List.of(),
            explanations = List.of();
    private boolean expanded, highlighted;
    private int headingHeight = HEIGHT, valueColor;

    public PanelDisclosure(OnPress press, Supplier<UiTheme> theme) {
        super(Component.empty(), press, theme);
    }

    public void update(
            Component heading,
            Component value,
            List<Component> details,
            boolean expanded,
            boolean highlighted,
            int valueColor) {
        this.heading = Component.literal(expanded ? "▾ " : "▸ ").append(heading);
        this.value = value;
        this.details = List.copyOf(details);
        this.expanded = expanded;
        this.highlighted = highlighted;
        this.valueColor = valueColor;
        Component narration = heading.copy().append(" · ").append(value);
        if (expanded) for (Component detail : details) narration = narration.copy().append(". ").append(detail);
        setMessage(narration);
    }

    @Override
    public int measureHeight(int width) {
        var font = Minecraft.getInstance().font;
        int wrap = Math.max(1, width - 8),
                valueWidth = Math.min(wrap / 2, Math.max(64, font.width(value) + 4));
        headings = font.split(heading, Math.max(1, wrap - valueWidth - 8));
        values = font.split(value, Math.max(1, valueWidth));
        headingHeight = Math.max(HEIGHT, Math.max(headings.size(), values.size()) * 10 + 6);
        ArrayList<FormattedCharSequence> lines = new ArrayList<>();
        if (expanded)
            for (Component line : details) lines.addAll(font.split(line, Math.max(1, wrap - 14)));
        explanations = List.copyOf(lines);
        return headingHeight + (expanded ? explanations.size() * DETAIL + 4 : 0);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        var font = Minecraft.getInstance().font;
        UiTheme t = theme.get();
        boolean hover = isHovered && inViewport(mx, my);
        if (hover || highlighted)
            UiDraw.rect(
                    g,
                    getX(),
                    getY(),
                    width,
                    headingHeight,
                    UiColor.mix(t.inset(), highlighted ? t.accent() : 0xFFFFFFFF, highlighted ? .06F : .03F));
        if (highlighted) UiDraw.rect(g, getX(), getY() + 2, 2, headingHeight - 4, t.accent());
        if (!expanded && explanations.isEmpty())
            UiDraw.rect(g, getX(), getY() + headingHeight - 1, width, 1, UiColor.alpha(0xFFFFFFFF, .035F));
        if (isFocused()) UiDraw.outline(g, getX(), getY(), width, headingHeight, t.accent());
        int y = getY() + 3;
        for (var line : headings) {
            g.drawString(font, line, getX() + 4, y, highlighted ? t.text() : t.muted(), false);
            y += 10;
        }
        y = getY() + 3;
        for (var line : values) {
            g.drawString(font, line, getX() + width - 4 - font.width(line), y, valueColor, false);
            y += 10;
        }
        if (!explanations.isEmpty()) {
            int top = getY() + headingHeight;
            UiDraw.rect(
                    g,
                    getX(),
                    top,
                    width,
                    explanations.size() * DETAIL + 2,
                    UiColor.alpha(0xFF000000, .25F));
            y = top + 2;
            for (var line : explanations) {
                g.drawString(font, line, getX() + 14, y, t.muted(), false);
                y += DETAIL;
            }
        }
    }
}