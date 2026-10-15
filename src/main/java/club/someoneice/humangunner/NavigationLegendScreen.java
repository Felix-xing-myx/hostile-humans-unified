package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Client-only reference view; opening it never changes observation or movement leases. */
public final class NavigationLegendScreen extends Screen {
    private record Row(int color, Component text) {}
    private final List<Row> rows = new ArrayList<>();
    private int scroll, maxScroll;
    public NavigationLegendScreen() {
        super(label("title"));
        rows.add(new Row(-1, label("entities")));
        for (int slot = 0; slot < NavigationDebugFrame.MAX_ENTITIES; slot++)
            rows.add(new Row(NavigationDebugClient.slotColor(slot), Component.translatable("navigation_debug.help.entity", slot + 1)));
        rows.add(new Row(0xFFFFFF, label("next")));
        rows.add(new Row(0xFF1A1A, label("enemy")));
        rows.add(new Row(0xFF40FF, label("pending")));
        rows.add(new Row(0x33FF33, label("accepted")));
        rows.add(new Row(0xFF4033, label("rejected")));
        rows.add(new Row(-1, label("limits")));
        rows.add(new Row(-1, label("controls")));
    }
    private static Component label(String key) { return Component.translatable("navigation_debug.help." + key); }
    private int panelWidth() { return Math.min(420, width - 24); }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int)(delta * 22), 0, maxScroll);
        return true;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int left = (width - panelWidth()) / 2, top = 38, bottom = height - 38;
        int wrapWidth = Math.max(8, panelWidth() - 34);
        int contentHeight = 0;
        for (Row row : rows) contentHeight += font.split(row.text(), wrapWidth).size() * 11 + 7;
        maxScroll = Math.max(0, contentHeight - (bottom - top));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.fill(left, top - 4, left + panelWidth(), bottom + 4, 0xD0101820);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        g.drawCenteredString(font, label("scroll"), width / 2, 25, 0xB0B8C0);
        g.enableScissor(left, top, left + panelWidth(), bottom);
        try {
            int y = top - scroll;
            for (Row row : rows) {
                if (row.color() >= 0) {
                    g.fill(left + 8, y + 1, left + 17, y + 10, 0xFF000000 | row.color());
                    g.fill(left + 10, y + 3, left + 15, y + 8, 0xFF101820);
                }
                for (var line : font.split(row.text(), wrapWidth)) {
                    g.drawString(font, line, left + 24, y, 0xE5EAF0, false);
                    y += 11;
                }
                y += 7;
            }
            if (maxScroll > 0) {
                int track = bottom - top, thumb = Math.max(12, track * track / contentHeight);
                int thumbY = top + (track - thumb) * scroll / maxScroll;
                g.fill(left + panelWidth() - 4, thumbY, left + panelWidth() - 2, thumbY + thumb, 0xFF729EFF);
            }
        } finally { g.disableScissor(); }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
