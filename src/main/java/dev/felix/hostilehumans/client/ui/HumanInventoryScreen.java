package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiTheme;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.inventory.HumanInventoryMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Owner-facing editor for a hired human's backpack and equipped items. */
public final class HumanInventoryScreen extends AbstractContainerScreen<HumanInventoryMenu> {
    public HumanInventoryScreen(HumanInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 252;
        imageHeight = 202;
        titleLabelY = -21;
        inventoryLabelX = 44;
        inventoryLabelY = 108;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        UiTheme theme = UiThemes.faction("");
        UiDraw.frame(graphics, left, top - 30, imageWidth, imageHeight + 30, theme);
        UiDraw.roundRect(graphics, left + 4, top + 4, 244, 94, 2, theme.inset());
        UiDraw.roundRect(graphics, left + 40, top + 104, 208, 94, 2, theme.inset());
        for (Slot slot : menu.slots) {
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            UiDraw.roundRect(graphics, x, y, 18, 18, 2, 0xFF070B10);
            UiDraw.rect(graphics, x + 1, y + 1, 16, 16, theme.surface());
            if (menu.internalOnly() && slot.index >= 36)
                UiDraw.rect(graphics, x, y, 18, 18, 0x99070B10);
            if (menu.internalSelection() == slot.index)
                UiDraw.outline(graphics, x, y, 18, 18, theme.accent());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, titleLabelY, 0xFFFFFF, false);
        graphics.drawString(
                font,
                Component.translatable("screen.hostile_humans_unified.inventory.equipment"),
                8,
                6,
                UiThemes.faction("").text(),
                false);
        graphics.drawString(
                font,
                Component.translatable("screen.hostile_humans_unified.inventory.backpack"),
                62,
                6,
                UiThemes.faction("").text(),
                false);
        graphics.drawString(
                font,
                Component.translatable("screen.hostile_humans_unified.inventory.gun_rule"),
                62,
                78,
                UiThemes.faction("").accent(),
                false);
        graphics.drawString(
                font,
                playerInventoryTitle,
                inventoryLabelX,
                inventoryLabelY,
                UiThemes.faction("").text(),
                false);
        if (menu.internalOnly())
            graphics.drawString(
                    font,
                    Component.translatable("human_monitor.inventory.internal_only"),
                    8,
                    98,
                    UiThemes.faction("").accent(),
                    false);
    }
}
