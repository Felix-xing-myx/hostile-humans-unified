package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.bootstrap.ContentRegistry;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.monitor.HumanMonitorService;
import dev.felix.hostilehumans.soldier.SoldierRecallProtection;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class HumanInventoryMenu extends AbstractContainerMenu {
    static final int HUMAN_SLOT_COUNT = HumanInventoryContainer.TOTAL_SIZE;
    private final Container humanInventory;
    private final Human human;
    private final Object editToken = new Object();
    private final boolean editLock;
    private final boolean monitorAccess;
    private final boolean internalOnly;
    private int internalSelection = -1;
    public boolean monitored() { return monitorAccess; }
    public boolean internalOnly() { return internalOnly; }
    public int internalSelection() { return internalSelection; }

    public static HumanInventoryMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        buffer.readVarInt();
        boolean monitor = buffer.readBoolean(), restricted = buffer.readBoolean();
        return new HumanInventoryMenu(id, playerInventory, null,
                new SimpleContainer(HumanInventoryContainer.TOTAL_SIZE), monitor, restricted);
    }

    public static void open(ServerPlayer player, Human human) { open(player, human, false); }
    public static void openMonitored(ServerPlayer player, Human human) { open(player, human, true); }
    private static void open(ServerPlayer player, Human human, boolean monitor) {
        boolean authorized = monitor ? HumanMonitorService.canAccess(player, human)
                : HumanRelations.isOwnedBy(human, player) && player.distanceToSqr(human) <= 64.0D;
        if (!human.isAlive() || !authorized || SoldierRecallProtection.pendingRescue(human) || human.getData() == null
                || HumanInventorySession.isEditing(human) || !player.containerMenu.getCarried().isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.hostile_humans_unified.inventory.unavailable"), false);
            return;
        }
        NetworkHooks.openScreen(
                player,
                new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, ignored) -> new HumanInventoryMenu(id, inventory, human, new HumanInventoryContainer(human), monitor),
                        Component.translatable("screen.hostile_humans_unified.inventory.title", human.getDisplayName())
                ),
                buffer -> { buffer.writeVarInt(human.getId()); buffer.writeBoolean(monitor); buffer.writeBoolean(monitor && !player.isCreative()); }
        );
    }

    HumanInventoryMenu(int id, Inventory playerInventory, Human human) {
        this(id, playerInventory, human, new HumanInventoryContainer(human));
    }

    private HumanInventoryMenu(int id, Inventory playerInventory, Human human, Container container) {
        this(id, playerInventory, human, container, false);
    }
    private HumanInventoryMenu(int id, Inventory playerInventory, Human human, Container container, boolean monitor) {
        this(id, playerInventory, human, container, monitor, monitor && !playerInventory.player.isCreative());
    }
    private HumanInventoryMenu(int id, Inventory playerInventory, Human human, Container container, boolean monitor, boolean restricted) {
        super(ContentRegistry.HUMAN_INVENTORY_MENU.get(), id);
        this.monitorAccess = monitor;
        this.internalOnly = restricted;
        this.human = human;
        this.humanInventory = container;
        this.editLock = human == null || HumanInventorySession.begin(
                human, (ServerPlayer) playerInventory.player, editToken);
        checkContainerSize(container, HumanInventoryContainer.TOTAL_SIZE);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 10; column++) {
                addHumanSlot(row * 10 + column, 62 + column * 18, 18 + row * 18, false);
            }
        }
        addHumanSlot(HumanInventoryContainer.MAIN_HAND, 8, 18, false);
        addHumanSlot(HumanInventoryContainer.OFF_HAND, 34, 18, false);
        addHumanSlot(HumanInventoryContainer.HEAD, 8, 54, true);
        addHumanSlot(HumanInventoryContainer.CHEST, 34, 54, true);
        addHumanSlot(HumanInventoryContainer.LEGS, 8, 72, true);
        addHumanSlot(HumanInventoryContainer.FEET, 34, 72, true);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addPlayerSlot(playerInventory, column + row * 9 + 9, 44 + column * 18, 120 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            addPlayerSlot(playerInventory, column, 44 + column * 18, 178);
        }
    }
    private void addPlayerSlot(Inventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPickup(Player player) { return !internalOnly; }
            @Override public boolean mayPlace(ItemStack stack) { return !internalOnly; }
        });
    }
    /** Restricted remote editing never places a real item on the player's cursor.
     * Select two Human slots for one validated swap; hotbar, drag, collect and drop cannot cross inventories. */
    @Override public void clicked(int index, int button, net.minecraft.world.inventory.ClickType type, Player player) {
        if (!internalOnly) { super.clicked(index, button, type, player); return; }
        if (type != net.minecraft.world.inventory.ClickType.PICKUP || index < 0 || index >= HUMAN_SLOT_COUNT
                || button < 0 || button > 1 || !getCarried().isEmpty() || !stillValid(player)) return;
        if (internalSelection < 0) { if (slots.get(index).hasItem()) internalSelection = index; return; }
        int first = internalSelection; internalSelection = -1;
        if (first == index) return;
        Slot source = slots.get(first), destination = slots.get(index);
        ItemStack a = source.getItem().copy(), b = destination.getItem().copy();
        if (!source.mayPickup(player) || !destination.mayPickup(player)
                || !a.isEmpty() && (!destination.mayPlace(a) || a.getCount() > destination.getMaxStackSize(a))
                || !b.isEmpty() && (!source.mayPlace(b) || b.getCount() > source.getMaxStackSize(b))) return;
        source.set(b); destination.set(a);
        broadcastChanges();
    }

    private void addHumanSlot(int index, int x, int y, boolean singleItem) {
        addSlot(new Slot(humanInventory, index, x, y) {
            @Override public boolean mayPickup(Player player) {
                // Keep the controlled dual-hand invariant during intermediate menu clicks.
                // The explicit weapon menu can change both hands atomically instead.
                return index != HumanInventoryContainer.MAIN_HAND || human == null
                        || !dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.bound(human)
                        || !ControlledEquipmentPolicy.dualOffhand(human)
                        || !ControlledEquipmentPolicy.oneHandWeapon(human.getOffhandItem());
            }
            @Override
            public boolean mayPlace(ItemStack stack) {
                return humanInventory.canPlaceItem(index, stack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                ResourceLocation icon = switch (index) {
                    case HumanInventoryContainer.HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
                    case HumanInventoryContainer.CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
                    case HumanInventoryContainer.LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
                    case HumanInventoryContainer.FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
                    case HumanInventoryContainer.OFF_HAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
                    default -> null;
                };
                return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
            }

            @Override
            public int getMaxStackSize() {
                return singleItem ? 1 : super.getMaxStackSize();
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (internalOnly) return ItemStack.EMPTY;
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < HUMAN_SLOT_COUNT) {
            moved = moveItemStackTo(source, HUMAN_SLOT_COUNT, slots.size(), true);
        } else {
            int equipment = quickEquipmentSlot(source);
            moved = equipment >= 0 && !slots.get(equipment).hasItem()
                    && moveItemStackTo(source, equipment, equipment + 1, false);
            if (!source.isEmpty())
                moved |= moveItemStackTo(source, 0, HumanInventoryContainer.BACKPACK_SIZE, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }

    private static int quickEquipmentSlot(ItemStack stack) {
        EquipmentSlot slot = Mob.getEquipmentSlotForItem(stack);
        if (slot.getType() == EquipmentSlot.Type.ARMOR) return switch (slot) {
            case HEAD -> HumanInventoryContainer.HEAD;
            case CHEST -> HumanInventoryContainer.CHEST;
            case LEGS -> HumanInventoryContainer.LEGS;
            case FEET -> HumanInventoryContainer.FEET;
            default -> -1;
        };
        if (SpartanEquipmentCompat.isShield(stack) || stack.is(Items.TOTEM_OF_UNDYING))
            return HumanInventoryContainer.OFF_HAND;
        return HumanLootManager.isNonGunWeapon(stack) || GunSupport.get().isGun(stack)
                ? HumanInventoryContainer.MAIN_HAND : -1;
    }

    @Override
    public boolean stillValid(Player player) {
        return editLock && (internalOnly || !monitorAccess || player.isCreative()) && (human == null || (HumanInventorySession.owns(human, player, editToken) && (monitorAccess
                ? player instanceof ServerPlayer server && HumanMonitorService.canAccess(server, human)
                : humanInventory.stillValid(player))));
    }

    boolean holdsEditToken(Object token) {
        return editToken == token;
    }

    @Override
    public void removed(Player player) {
        try {
            super.removed(player);
        } finally {
            if (human != null && editLock) HumanInventorySession.end(human, editToken);
        }
    }
}
