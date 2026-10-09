package club.someoneice.humangunner;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Command panel opened directly on a hired soldier. */
public final class SoldierCommandScreen extends Screen {
    private static final int PANEL_WIDTH = 350, PANEL_HEIGHT = 227;
    private static final int PANEL = 0xF0101820, SURFACE = 0xFF202B36;
    private static final int HOVER = 0xFF2A3947, EDGE = 0xFF394653;
    private static final int TEXT = 0xFFE5EAF0, MUTED = 0xFF9DADBB;
    private static final int GOLD = 0xFFF2C66D;

    private final int entityId;
    private final java.util.UUID humanId;
    private final java.util.UUID sessionToken;
    private final Component soldierName;
    private GrowthSnapshot growth;
    private SoldierOrder selectedOrder;
    private SoldierCombatMode selectedCombatMode;
    private boolean pickupEnabled;
    private long dismissConfirmUntil;

    private SoldierCommandScreen(int entityId, java.util.UUID humanId, Component soldierName, SoldierOrder selectedOrder,
                                 SoldierCombatMode selectedCombatMode, boolean pickupEnabled,
                                 java.util.UUID sessionToken, GrowthSnapshot growth) {
        super(Component.translatable("screen.hostile_humans_unified.soldier.title"));
        this.entityId = entityId;
        this.humanId = humanId;
        this.sessionToken = sessionToken;
        this.growth = growth;
        this.soldierName = soldierName;
        this.selectedOrder = selectedOrder;
        this.selectedCombatMode = selectedCombatMode;
        this.pickupEnabled = pickupEnabled;
    }

    static void open(int entityId, java.util.UUID humanId, Component soldierName, SoldierOrder selectedOrder,
                     SoldierCombatMode selectedCombatMode, boolean pickupEnabled,
                     java.util.UUID sessionToken, GrowthSnapshot growth) {
        Minecraft.getInstance().setScreen(new SoldierCommandScreen(
                entityId, humanId, soldierName, selectedOrder, selectedCombatMode, pickupEnabled, sessionToken, growth));
    }

    static void updateGrowth(java.util.UUID token, java.util.UUID humanId, int entityId, GrowthSnapshot growth) {
        if (Minecraft.getInstance().screen instanceof SoldierCommandScreen screen
                && screen.sessionToken.equals(token) && screen.humanId.equals(humanId) && screen.entityId == entityId) {
            screen.growth = growth;
        }
    }

    static void invalidate(java.util.UUID token) {
        if (Minecraft.getInstance().screen instanceof SoldierCommandScreen screen
                && screen.sessionToken.equals(token)) screen.onClose();
    }

    private float panelScale() {
        return Math.min(1.0F, Math.min(width / (PANEL_WIDTH + 12.0F), height / (panelHeight() + 12.0F)));
    }
    private int left() { return (int) (width / panelScale() / 2) - PANEL_WIDTH / 2; }
    private int growthHeight() { return growth.enabled() ? 96 : 0; }
    private int panelHeight() { return PANEL_HEIGHT + growthHeight(); }
    private int top() { return (int) (height / panelScale() / 2) - panelHeight() / 2; }

    @Override
    public void removed() {
        if (Minecraft.getInstance().getConnection() != null) HumanCommandNetwork.closeScreen(sessionToken);
        super.removed();
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, "%.2f", Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D);
    }

    private static double fraction(double current, double required) {
        if (!Double.isFinite(current) || !Double.isFinite(required) || required <= 0.0D) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, current / required));
    }

    private void drawProgress(GuiGraphics graphics, int x, int y, int w, double progress, boolean slider) {
        graphics.fill(x, y, x + w, y + 5, SURFACE);
        int filled = (int) Math.round(w * progress);
        graphics.fill(x, y, x + filled, y + 5, GOLD);
        if (slider) {
            int marker = x + Math.max(1, Math.min(w - 2, filled));
            graphics.fill(marker - 1, y - 2, marker + 2, y + 7, TEXT);
        }
    }

    /** All numbers and visibility come from the server snapshot; these bars are read-only. */
    private void renderGrowth(GuiGraphics graphics, int left, int top) {
        if (!growth.enabled()) return;
        int x = left + 13, w = PANEL_WIDTH - 26;
        graphics.drawString(font, Component.translatable("screen.hostile_humans_unified.soldier.growth.level",
                Math.max(0, Math.min(5, growth.level()))), x, top + 51, GOLD, false);
        boolean maxLevel = growth.level() >= 5;
        graphics.drawString(font, maxLevel
                        ? Component.translatable("screen.hostile_humans_unified.soldier.growth.max_level")
                        : Component.translatable("screen.hostile_humans_unified.soldier.growth.stage",
                        growth.stagePercent(), amount(growth.stageProgress()), amount(growth.stageRequired())),
                x, top + 65, TEXT, false);
        drawProgress(graphics, x, top + 78, w, maxLevel ? 1.0D
                : fraction(growth.stageProgress(), growth.stageRequired()), false);
        graphics.drawString(font, Component.translatable("screen.hostile_humans_unified.soldier.growth.experience",
                growth.experiencePercent(), growth.experienceMinPercent(), growth.experienceCapPercent()),
                x, top + 90, TEXT, false);
        drawProgress(graphics, x, top + 103, w,
                fraction((double) growth.experiencePercent() - growth.experienceMinPercent(),
                        (double) growth.experienceCapPercent() - growth.experienceMinPercent()), true);
        graphics.drawString(font, growth.experiencePercent() >= growth.experienceCapPercent()
                        ? Component.translatable("screen.hostile_humans_unified.soldier.growth.experience_capped")
                        : Component.translatable("screen.hostile_humans_unified.soldier.growth.next_point",
                        amount(growth.experienceProgress()), amount(growth.experienceRequired())),
                x, top + 117, MUTED, false);
        graphics.fill(left + 9, top + 139, left + PANEL_WIDTH - 9, top + 140, EDGE);
    }

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
        float scale = panelScale();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        mouseX = (int) (mouseX / scale);
        mouseY = (int) (mouseY / scale);
        int left = left(), top = top();
        int panelHeight = panelHeight();
        graphics.fill(left, top, left + PANEL_WIDTH, top + panelHeight, PANEL);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 2, EDGE);
        graphics.fill(left, top + panelHeight - 2, left + PANEL_WIDTH, top + panelHeight, EDGE);
        graphics.fill(left, top, left + 2, top + panelHeight, EDGE);
        graphics.fill(left + PANEL_WIDTH - 2, top, left + PANEL_WIDTH, top + panelHeight, EDGE);
        graphics.drawString(font, title, left + 10, top + 9, TEXT, false);
        graphics.drawString(font, font.plainSubstrByWidth(soldierName.getString(), PANEL_WIDTH - 20),
                left + 10, top + 25, GOLD, false);
        graphics.fill(left + 9, top + 43, left + PANEL_WIDTH - 9, top + 44, EDGE);
        renderGrowth(graphics, left, top);
        top += growthHeight();

        int movementX = left + 10, combatX = left + 180;
        graphics.drawString(font, Component.translatable("screen.hostile_humans_unified.soldier.movement"),
                movementX + 3, top + 51, MUTED, false);
        graphics.drawString(font, Component.translatable("screen.hostile_humans_unified.soldier.combat"),
                combatX + 3, top + 51, MUTED, false);
        SoldierOrder[] orders = SoldierOrder.values();
        for (int i = 0; i < orders.length; i++) {
            SoldierOrder order = orders[i];
            int y = top + 65 + i * 22;
            drawButton(graphics, Component.translatable("screen.hostile_humans_unified.soldier."
                            + order.name().toLowerCase(Locale.ROOT)), movementX, y, 160, 19,
                    hit(mouseX, mouseY, movementX, y, 160, 19), selectedOrder == order, false);
        }
        SoldierCombatMode[] modes = {SoldierCombatMode.ACTIVE, SoldierCombatMode.AUTHORIZED,
                SoldierCombatMode.PASSIVE_PROTECTION, SoldierCombatMode.SELF_DEFENSE};
        for (int i = 0; i < modes.length; i++) {
            SoldierCombatMode mode = modes[i];
            int y = top + 65 + i * 22;
            drawButton(graphics, Component.translatable("screen.hostile_humans_unified.soldier.combat."
                            + mode.name().toLowerCase(Locale.ROOT)), combatX, y, 160, 19,
                    hit(mouseX, mouseY, combatX, y, 160, 19), selectedCombatMode == mode, false);
        }
        drawButton(graphics, Component.translatable(pickupEnabled
                        ? "screen.hostile_humans_unified.soldier.pickup.enabled"
                        : "screen.hostile_humans_unified.soldier.pickup.disabled"), combatX, top + 153, 160, 19,
                hit(mouseX, mouseY, combatX, top + 153, 160, 19), pickupEnabled, false);
        graphics.drawString(font, font.plainSubstrByWidth(
                        Component.translatable("screen.hostile_humans_unified.soldier.help").getString(),
                        PANEL_WIDTH - 20), left + 10, top + 181, MUTED, false);
        drawButton(graphics, Component.translatable("screen.hostile_humans_unified.soldier.close"),
                left + 10, top + 202, 90, 18,
                hit(mouseX, mouseY, left + 10, top + 202, 90, 18), false, false);
        boolean confirming = System.currentTimeMillis() < dismissConfirmUntil;
        drawButton(graphics, Component.translatable(confirming
                        ? "screen.hostile_humans_unified.soldier.dismiss.confirm"
                        : "screen.hostile_humans_unified.soldier.dismiss"),
                left + 208, top + 202, 132, 18,
                hit(mouseX, mouseY, left + 208, top + 202, 132, 18), false, true);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, mouseButton);
        }
        mouseX /= panelScale();
        mouseY /= panelScale();
        int left = left(), top = top() + growthHeight();
        int movementX = left + 10, combatX = left + 180;
        SoldierOrder[] orders = SoldierOrder.values();
        for (int i = 0; i < orders.length; i++) {
            if (hit(mouseX, mouseY, movementX, top + 65 + i * 22, 160, 19)) {
                selectedOrder = orders[i];
                HumanCommandNetwork.sendOrder(entityId, selectedOrder);
                return true;
            }
        }
        SoldierCombatMode[] modes = {SoldierCombatMode.ACTIVE, SoldierCombatMode.AUTHORIZED,
                SoldierCombatMode.PASSIVE_PROTECTION, SoldierCombatMode.SELF_DEFENSE};
        for (int i = 0; i < modes.length; i++) {
            if (hit(mouseX, mouseY, combatX, top + 65 + i * 22, 160, 19)) {
                selectedCombatMode = modes[i];
                HumanCommandNetwork.sendCombatMode(entityId, selectedCombatMode);
                return true;
            }
        }
        if (hit(mouseX, mouseY, combatX, top + 153, 160, 19)) {
            pickupEnabled = !pickupEnabled;
            HumanCommandNetwork.sendPickupPolicy(entityId, pickupEnabled);
            return true;
        }
        if (hit(mouseX, mouseY, left + 10, top + 202, 90, 18)) {
            onClose();
            return true;
        }
        if (hit(mouseX, mouseY, left + 208, top + 202, 132, 18)) {
            long now = System.currentTimeMillis();
            if (now < dismissConfirmUntil) {
                HumanCommandNetwork.sendDismiss(entityId, sessionToken);
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
