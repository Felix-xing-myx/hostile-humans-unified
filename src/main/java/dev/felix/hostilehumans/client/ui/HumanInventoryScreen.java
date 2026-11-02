package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.client.ui.framework.UiColor;
import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiTheme;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.inventory.HumanInventoryMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Owner-facing editor for a hired human's backpack and equipped items.
 *
 * <p>Slot indices belong to {@link HumanInventoryMenu}; this screen only assigns their visual
 * positions, so the three zones can follow the reference layout without touching the menu, its
 * validation or the exchange rules. Cell size and panel height adapt to the window so a small
 * GUI scale still fits on screen.
 */
public final class HumanInventoryScreen extends AbstractContainerScreen<HumanInventoryMenu> {
    private static final int HUMAN_SLOTS = 36, WIDTH = 420;

    private boolean compact;
    private int cell = 24, pitch = 26, equipmentTop = 40, backpackTop = 198, playerTop = 310;

    public HumanInventoryScreen(HumanInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = 520;
    }

    @Override
    protected void init() {
        super.init();
        compact = height < 560;
        cell = compact ? Math.max(16, Math.min(24, (height - 96) / 8 - 2)) : 24;
        cell = Math.min(cell, Math.max(16, (width - 52) / 10 - 2));
        pitch = cell + 2;
        equipmentTop = compact ? 34 : 40;
        backpackTop = equipmentTop + (compact ? pitch + 18 : 4 * pitch + 34);
        playerTop = backpackTop + 3 * pitch + (compact ? 18 : 34);
        imageHeight = playerTop + 4 * pitch + (compact ? 26 : 46);
        imageWidth = Math.min(WIDTH, Math.max(220, width - 24));
        leftPos = Math.max(0, (width - imageWidth) / 2);
        topPos = Math.max(0, (height - imageHeight) / 2);
        layoutSlots();
    }

    /** Three zones: equipment, the human's backpack, then the player's rows and hotbar. */
    private void layoutSlots() {
        int left = leftPos, top = topPos;
        int armourX = left + (compact ? 14 : 60), armourY = top + equipmentTop + (compact ? 16 : 22);
        for (int i = 0; i < 4; i++)
            place(32 + i, armourX + (compact ? i * pitch : 0), armourY + (compact ? 0 : i * pitch));
        place(30, compact ? armourX + 5 * pitch : left + 152, armourY);
        place(31, compact ? armourX + 6 * pitch : left + 152, armourY + (compact ? 0 : pitch));

        int packX = left + 14, packY = top + backpackTop + (compact ? 16 : 22);
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 10; column++)
                place(row * 10 + column, packX + column * pitch, packY + row * pitch);

        int playerX = left + 14, playerY = top + playerTop + (compact ? 16 : 22);
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                place(36 + row * 9 + column, playerX + column * pitch, playerY + row * pitch);
        for (int column = 0; column < 9; column++)
            place(63 + column, playerX + column * pitch, playerY + 3 * pitch + 8);
    }

    private void place(int index, int x, int y) {
        if (index >= menu.slots.size()) return;
        Slot slot = menu.slots.get(index);
        slot.x = x - leftPos + (cell - 16) / 2;
        slot.y = y - topPos + (cell - 16) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        UiTheme theme = UiThemes.faction("");
        UiDraw.frame(graphics, leftPos, topPos, imageWidth, imageHeight, theme);
        UiDraw.headerBand(graphics, leftPos + 1, topPos, imageWidth - 2, 28, theme);
        UiDraw.rule(graphics, leftPos + 12, topPos + 31, imageWidth - 24, theme.ruleDark(), theme.ruleLight());
        zone(graphics, theme, topPos + equipmentTop, (compact ? pitch + 18 : 4 * pitch + 34), true);
        zone(graphics, theme, topPos + backpackTop, 3 * pitch + (compact ? 18 : 34), true);
        zone(graphics, theme, topPos + playerTop, 4 * pitch + (compact ? 22 : 42), false);
        for (Slot slot : menu.slots) drawSlot(graphics, theme, slot);
    }

    private void zone(GuiGraphics g, UiTheme theme, int y, int h, boolean human) {
        int x = leftPos + 12, w = imageWidth - 26;
        UiDraw.roundRect(g, x, y, w, h, 2, human ? theme.groupBand() : UiColor.alpha(0xFF000000, .30F));
        UiDraw.outline(g, x, y, w, h, UiColor.alpha(0xFFFFFFFF, .06F));
        UiDraw.rect(g, x + 1, y + 1, 2, 7, UiColor.alpha(theme.accent(), .9F));
    }

    private void drawSlot(GuiGraphics g, UiTheme theme, Slot slot) {
        int inset = (cell - 16) / 2;
        int x = leftPos + slot.x - inset, y = topPos + slot.y - inset;
        UiDraw.roundRect(g, x, y, cell, cell, 2, 0xFF070B10);
        UiDraw.rect(g, x + 1, y + 1, cell - 2, cell - 2, theme.inset());
        UiDraw.outline(g, x, y, cell, cell, 0xFF394653);
        if (menu.internalOnly() && slot.index >= HUMAN_SLOTS)
            UiDraw.rect(g, x, y, cell, cell, 0x99070B10);
        if (menu.internalSelection() == slot.index)
            UiDraw.outline(g, x, y, cell, cell, theme.accent());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        UiTheme theme = UiThemes.faction("");
        graphics.drawString(font, title, 32, 10, theme.text(), false);
        graphics.drawString(
                font,
                Component.translatable("screen.hostile_humans_unified.inventory.equipment"),
                14,
                equipmentTop + 6,
                theme.muted(),
                false);
        graphics.drawString(
                font,
                Component.translatable("screen.hostile_humans_unified.inventory.backpack"),
                14,
                backpackTop + 6,
                theme.muted(),
                false);
        graphics.drawString(
                font,
                playerInventoryTitle,
                14,
                playerTop + 6,
                theme.accent(),
                false);
        if (!compact) note(
                graphics,
                theme,
                Component.translatable("screen.hostile_humans_unified.inventory.gun_rule"),
                216,
                equipmentTop + 24,
                imageWidth - 232);
        if (menu.internalOnly())
            graphics.drawString(
                    font,
                    Component.translatable("human_monitor.inventory.internal_only"),
                    14,
                    18,
                    theme.accent(),
                    false);
    }

    private void note(GuiGraphics g, UiTheme theme, Component text, int x, int y, int width) {
        int line = y;
        for (var part : font.split(text, Math.max(40, width))) {
            g.drawString(font, part, x, line, theme.disabled(), false);
            line += 10;
        }
    }
}