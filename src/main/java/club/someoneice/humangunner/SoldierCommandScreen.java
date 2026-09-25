package club.someoneice.humangunner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Client-only, menu-less command screen for one hired soldier. */
public final class SoldierCommandScreen extends Screen {
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

    @Override
    protected void init() {
        int width = 140;
        int gap = 8;
        int left = this.width / 2 - width - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y = this.height / 2 - 54;
        addRenderableWidget(orderButton(left, y, width, SoldierOrder.HOLD_POSITION));
        addRenderableWidget(orderButton(left, y + 26, width, SoldierOrder.GUARD));
        addRenderableWidget(orderButton(left, y + 52, width, SoldierOrder.FOLLOW));
        addRenderableWidget(orderButton(left, y + 78, width, SoldierOrder.PATROL));
        addRenderableWidget(combatButton(right, y, width, SoldierCombatMode.ACTIVE));
        addRenderableWidget(combatButton(right, y + 26, width, SoldierCombatMode.PASSIVE_PROTECTION));
        addRenderableWidget(combatButton(right, y + 52, width, SoldierCombatMode.SELF_DEFENSE));
        Component pickupLabel = Component.translatable(pickupEnabled
                ? "screen.humangunner.soldier.pickup.enabled"
                : "screen.humangunner.soldier.pickup.disabled");
        addRenderableWidget(Button.builder(pickupLabel, ignored -> {
            pickupEnabled = !pickupEnabled;
            HumanCommandNetwork.sendPickupPolicy(entityId, pickupEnabled);
            rebuildWidgets();
        }).bounds(right, y + 78, width, 20).build());
        boolean confirming = System.currentTimeMillis() <= dismissConfirmUntil;
        Component dismissLabel = Component.translatable(confirming
                ? "screen.humangunner.soldier.dismiss.confirm"
                : "screen.humangunner.soldier.dismiss").withStyle(ChatFormatting.RED);
        addRenderableWidget(Button.builder(dismissLabel, ignored -> {
            long now = System.currentTimeMillis();
            if (now <= dismissConfirmUntil) {
                HumanCommandNetwork.sendDismiss(entityId);
                onClose();
            } else {
                dismissConfirmUntil = now + 3000L;
                rebuildWidgets();
            }
        }).bounds(this.width - 112, this.height - 26, 104, 18).build());
    }

    private Button orderButton(int x, int y, int width, SoldierOrder order) {
        Component label = Component.translatable(
                "screen.humangunner.soldier." + order.name().toLowerCase(Locale.ROOT));
        if (selectedOrder == order) label = Component.literal("▶ ").append(label);
        return Button.builder(label, ignored -> {
            selectedOrder = order;
            HumanCommandNetwork.sendOrder(entityId, order);
            rebuildWidgets();
        }).bounds(x, y, width, 20).build();
    }

    private Button combatButton(int x, int y, int width, SoldierCombatMode mode) {
        Component label = Component.translatable(
                "screen.humangunner.soldier.combat." + mode.name().toLowerCase(Locale.ROOT));
        if (selectedCombatMode == mode) label = Component.literal("▶ ").append(label);
        return Button.builder(label, ignored -> {
            selectedCombatMode = mode;
            HumanCommandNetwork.sendCombatMode(entityId, mode);
            rebuildWidgets();
        }).bounds(x, y, width, 20).build();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 96, 0xFFFFFF);
        graphics.drawCenteredString(font, soldierName, width / 2, height / 2 - 82, 0xB8D8FF);
        graphics.drawCenteredString(font, Component.translatable("screen.humangunner.soldier.movement"),
                width / 2 - 74, height / 2 - 68, 0xA0A0A0);
        graphics.drawCenteredString(font, Component.translatable("screen.humangunner.soldier.combat"),
                width / 2 + 74, height / 2 - 68, 0xA0A0A0);
        graphics.drawCenteredString(font,
                Component.translatable("screen.humangunner.soldier.help"),
                width / 2, height / 2 + 52, 0xA0A0A0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
