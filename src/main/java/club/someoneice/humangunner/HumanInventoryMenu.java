package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class HumanInventoryMenu extends AbstractContainerMenu {
    static final int HUMAN_SLOT_COUNT = HumanInventoryContainer.TOTAL_SIZE;
    private final Container humanInventory;
    private final Human human;
    private final boolean editLock;

    public static HumanInventoryMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buffer) {
        buffer.readVarInt();
        return new HumanInventoryMenu(id, playerInventory, null,
                new SimpleContainer(HumanInventoryContainer.TOTAL_SIZE));
    }

    static void open(ServerPlayer player, Human human) {
        if (!human.isAlive() || !HumanRelations.isOwnedBy(human, player)
                || player.distanceToSqr(human) > 64.0D || human.getData() == null
                || HumanInventorySession.isEditing(human)) {
            player.displayClientMessage(Component.translatable(
                    "message.humangunner.inventory.unavailable"), false);
            return;
        }
        NetworkHooks.openScreen(
                player,
                new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, ignored) -> new HumanInventoryMenu(id, inventory, human),
                        Component.translatable("screen.humangunner.inventory.title", human.getDisplayName())
                ),
                buffer -> buffer.writeVarInt(human.getId())
        );
    }

    HumanInventoryMenu(int id, Inventory playerInventory, Human human) {
        this(id, playerInventory, human, new HumanInventoryContainer(human));
    }

    private HumanInventoryMenu(int id, Inventory playerInventory, Human human, Container container) {
        super(HumanGunnerRegistries.HUMAN_INVENTORY_MENU.get(), id);
        this.human = human;
        this.humanInventory = container;
        this.editLock = human == null || HumanInventorySession.begin(
                human, (ServerPlayer) playerInventory.player, id);
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
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        44 + column * 18, 120 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 44 + column * 18, 178));
        }
    }

    private void addHumanSlot(int index, int x, int y, boolean singleItem) {
        addSlot(new Slot(humanInventory, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return humanInventory.canPlaceItem(index, stack);
            }

            @Override
            public int getMaxStackSize() {
                return singleItem ? 1 : super.getMaxStackSize();
            }
        });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < HUMAN_SLOT_COUNT) {
            moved = moveItemStackTo(source, HUMAN_SLOT_COUNT, slots.size(), true);
        } else {
            moved = moveItemStackTo(source, 0, HumanInventoryContainer.BACKPACK_SIZE, false);
        }
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return editLock && (human == null || humanInventory.stillValid(player));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (human != null && editLock) {
            HumanInventorySession.end(human, containerId);
        }
    }
}
