package club.someoneice.humangunner;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Command panel opened directly on a hired soldier. */
public final class SoldierCommandScreen extends Screen {
    private static final int PANEL_WIDTH = 350, PANEL_HEIGHT = 205;
    private static final int PANEL = 0xF0101820, SURFACE = 0xFF202B36;
    private static final int HOVER = 0xFF2A3947, EDGE = 0xFF394653;
    private static final int TEXT = 0xFFE5EAF0, MUTED = 0xFF9DADBB;
    private static final int GOLD = 0xFFF2C66D;

    private final int entityId;
    private final Component soldierName;
    private SoldierOrder selectedOrder;
    private SoldierCombatMode selectedCombatMode;
    private boolean pickupEnabled;
    private long dismissConfirmUntil;

    private SoldierCommandScreen(int entityId, Component soldierName, SoldierOrder selectedOrder,
                                 SoldierCombatMode selectedCombatMode, boolean pickupEnabled) {
        super(Component.translatable("screen.humangunner.soldier.title"));
        this.entityId = entityId;
        this.soldierName = soldierName;
        this.selectedOrder = selectedOrder;
        this.selectedCombatMode = selectedCombatMode;
        this.pickupEnabled = pickupEnabled;
    }

    static void open(int entityId, Component soldierName, SoldierOrder selectedOrder,
                     SoldierCombatMode selectedCombatMode, boolean pickupEnabled) {
        Minecraft.getInstance().setScreen(new SoldierCommandScreen(
                entityId, soldierName, selectedOrder, selectedCombatMode, pickupEnabled));
    }

    private int left() { return width / 2 - PANEL_WIDTH / 2; }
    private int top() { return height / 2 - PANEL_HEIGHT / 2; }

    private static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawButton(GuiGraphics graphics, Component label, int x, int y, int w, int h,
                            boolean hovered, boolean chosen, boolean danger) {
        graphics.fill(x, y, x + w, y + h, 0xFF070B10);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, EDGE);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2,
                chosen ? 0xFF354754 : hovered ? HOVER : SURFACE);
        if (chosen) graphics.fill(x + 2, y + 2, x + 4, y + h - 2, GOLD);
        int color = danger ? 0xFFED9B96 : chosen ? GOLD : TEXT;
        graphics.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), w - 8),
                x + w / 2, y + (h - 8) / 2, color);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left(), top = top();
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, EDGE);
        graphics.fill(left, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH, top + PANEL_HEIGHT, EDGE);
        graphics.fill(left, top, left + 2, top + PANEL_HEIGHT, EDGE);
        graphics.fill(left + PANEL_WIDTH - 2, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, EDGE);
        graphics.drawString(font, title, left + 10, top + 9, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(soldierName.getString(), PANEL_WIDTH - 20),
                left + 10, top + 25, GOLD, false);
        graphics.fill(left + 9, top + 43, left + PANEL_WIDTH - 9, top + 44, EDGE);

        int movementX = left + 10, combatX = left + 180;
        graphics.drawString(font, Component.translatable("screen.humangunner.soldier.movement"),
                movementX + 3, top + 51, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.humangunner.soldier.combat"),
                combatX + 3, top + 51, MUTED, false);
        SoldierOrder[] orders = SoldierOrder.values();
        for (int i = 0; i < orders.length; i++) {
            SoldierOrder order = orders[i];
            int y = top + 65 + i * 22;
            drawButton(graphics, Component.translatable("screen.humangunner.soldier."
                            + order.name().toLowerCase(Locale.ROOT)), movementX, y, 160, 19,
                    hit(mouseX, mouseY, movementX, y, 160, 19), selectedOrder == order, false);
        }
        SoldierCombatMode[] modes = SoldierCombatMode.values();
        for (int i = 0; i < modes.length; i++) {
            SoldierCombatMode mode = modes[i];
            int y = top + 65 + i * 22;
            drawButton(graphics, Component.translatable("screen.humangunner.soldier.combat."
                            + mode.name().toLowerCase(Locale.ROOT)), combatX, y, 160, 19,
                    hit(mouseX, mouseY, combatX, y, 160, 19), selectedCombatMode == mode, false);
        }
        drawButton(graphics, Component.translatable(pickupEnabled
                        ? "screen.humangunner.soldier.pickup.enabled"
                        : "screen.humangunner.soldier.pickup.disabled"), combatX, top + 131, 160, 19,
                hit(mouseX, mouseY, combatX, top + 131, 160, 19), pickupEnabled, false);
        graphics.drawString(font, font.plainSubstrByWidth(
                        Component.translatable("screen.humangunner.soldier.help").getString(),
                        PANEL_WIDTH - 20), left + 10, top + 159, MUTED, false);
        drawButton(graphics, Component.translatable("screen.humangunner.soldier.close"),
                left + 10, top + 180, 90, 18,
                hit(mouseX, mouseY, left + 10, top + 180, 90, 18), false, false);
        boolean confirming = System.currentTimeMillis() < dismissConfirmUntil;
        drawButton(graphics, Component.translatable(confirming
                        ? "screen.humangunner.soldier.dismiss.confirm"
                        : "screen.humangunner.soldier.dismiss"),
                left + 208, top + 180, 132, 18,
                hit(mouseX, mouseY, left + 208, top + 180, 132, 18), false, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        int left = left(), top = top();
        int movementX = left + 10, combatX = left + 180;
        SoldierOrder[] orders = SoldierOrder.values();
        for (int i = 0; i < orders.length; i++) {
            if (hit(mouseX, mouseY, movementX, top + 65 + i * 22, 160, 19)) {
                selectedOrder = orders[i];
                HumanCommandNetwork.sendOrder(entityId, selectedOrder);
                return true;
            }
        }
        SoldierCombatMode[] modes = SoldierCombatMode.values();
        for (int i = 0; i < modes.length; i++) {
            if (hit(mouseX, mouseY, combatX, top + 65 + i * 22, 160, 19)) {
                selectedCombatMode = modes[i];
                HumanCommandNetwork.sendCombatMode(entityId, selectedCombatMode);
                return true;
            }
        }
        if (hit(mouseX, mouseY, combatX, top + 131, 160, 19)) {
            pickupEnabled = !pickupEnabled;
            HumanCommandNetwork.sendPickupPolicy(entityId, pickupEnabled);
            return true;
        }
        if (hit(mouseX, mouseY, left + 10, top + 180, 90, 18)) {
            onClose();
            return true;
        }
        if (hit(mouseX, mouseY, left + 208, top + 180, 132, 18)) {
            long now = System.currentTimeMillis();
            if (now < dismissConfirmUntil) {
                HumanCommandNetwork.sendDismiss(entityId);
                onClose();
            } else {
                dismissConfirmUntil = now + 3000L;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
