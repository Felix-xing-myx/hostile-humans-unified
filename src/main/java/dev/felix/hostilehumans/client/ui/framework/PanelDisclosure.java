package dev.felix.hostilehumans.client.ui.framework;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Reusable inline disclosure row. Explanation is in-flow and narrated, never a hover popup. */
public final class PanelDisclosure extends PanelButton {
    private Component heading = Component.empty(), value = Component.empty();
    private List<Component> details = List.of();
    private List<FormattedCharSequence> headings = List.of(),
            values = List.of(),
            explanations = List.of();
    private boolean expanded, highlighted;
    private int headingHeight, valueColor;

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
        if (expanded)
            for (Component detail : details)
                narration = narration.copy().append(". ").append(detail);
        setMessage(narration);
    }

    @Override
    public int measureHeight(int width) {
        var font = Minecraft.getInstance().font;
        int wrap = Math.max(1, width - 12),
                valueWidth = Math.min(wrap / 2, Math.max(72, font.width(value) + 4));
        headings = font.split(heading, Math.max(1, wrap - valueWidth - 10));
        values = font.split(value, Math.max(1, valueWidth));
        headingHeight = Math.max(25, Math.max(headings.size(), values.size()) * 11 + 10);
        ArrayList<FormattedCharSequence> lines = new ArrayList<>();
        if (expanded)
            for (Component line : details) lines.addAll(font.split(line, Math.max(1, wrap - 8)));
        explanations = List.copyOf(lines);
        return headingHeight + (expanded ? explanations.size() * 12 + 4 : 0);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
        var font = Minecraft.getInstance().font;
        UiTheme t = theme.get();
        UiDraw.roundRect(
                g, getX(), getY(), width, height, 2, highlighted ? 0xFF263C34 : t.surface());
        UiDraw.rect(
                g,
                getX() + 1,
                getY() + 4,
                2,
                Math.max(0, headingHeight - 8),
                highlighted ? t.accent() : t.edge());
        if (isFocused()) UiDraw.outline(g, getX(), getY(), width, height, t.accent());
        int y = getY() + 6;
        for (var line : headings) {
            g.drawString(font, line, getX() + 7, y, t.text(), false);
            y += 11;
        }
        y = getY() + 6;
        for (var line : values) {
            g.drawString(font, line, getX() + width - 6 - font.width(line), y, valueColor, false);
            y += 11;
        }
        y = getY() + headingHeight;
        for (var line : explanations) {
            UiDraw.rect(g, getX() + 3, y, width - 6, 12, t.inset());
            g.drawString(font, line, getX() + 8, y + 1, t.muted(), false);
            y += 12;
        }
    }
}
