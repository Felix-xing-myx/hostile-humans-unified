package club.someoneice.humangunner;

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
        titleLabelY = -12;
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
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xE0101820);
        graphics.fill(left + 4, top + 4, left + 248, top + 98, 0xC0202B36);
        graphics.fill(left + 40, top + 104, left + 248, top + 198, 0xC0202B36);
        for (Slot slot : menu.slots) {
            int x = left + slot.x - 1;
            int y = top + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xFF070B10);
            graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF394653);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, titleLabelY, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.humangunner.inventory.equipment"),
                8, 6, 0xE5EAF0, false);
        graphics.drawString(font, Component.translatable("screen.humangunner.inventory.backpack"),
                62, 6, 0xE5EAF0, false);
        graphics.drawString(font, Component.translatable("screen.humangunner.inventory.gun_rule"),
                62, 78, 0xF2C66D, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                0xE5EAF0, false);
    }
}
