package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class HumanInventoryContainer implements Container {
    static final int BACKPACK_SIZE = 30;
    static final int MAIN_HAND = 30;
    static final int OFF_HAND = 31;
    static final int HEAD = 32;
    static final int CHEST = 33;
    static final int LEGS = 34;
    static final int FEET = 35;
    static final int TOTAL_SIZE = 36;

    private final Human human;

    HumanInventoryContainer(Human human) {
        this.human = human;
    }

    @Override
    public int getContainerSize() {
        return TOTAL_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < TOTAL_SIZE; slot++) {
            if (!getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        HumanData data = human.getData();
        if (slot < 0 || slot >= TOTAL_SIZE || data == null) return ItemStack.EMPTY;
        if (slot < BACKPACK_SIZE) return data.getInventoryItem(slot);
        return human.getItemBySlot(equipmentSlot(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(slot);
        if (current.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        ItemStack remainder = current.copy();
        ItemStack removed = remainder.split(amount);
        setItem(slot, remainder);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = current.copy();
        setItem(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        HumanData data = human.getData();
        if (slot < 0 || slot >= TOTAL_SIZE || data == null) return;
        if (!stack.isEmpty() && !canPlaceItem(slot, stack)) return;
        ItemStack previous = getItem(slot);
        boolean changed = previous.getCount() != stack.getCount()
                || !ItemStack.isSameItemSameTags(previous, stack);
        if (slot < BACKPACK_SIZE) {
            data.setInventoryItem(slot, stack);
        } else {
            human.setItemSlot(equipmentSlot(slot), stack);
        }
        if (changed && slot >= MAIN_HAND) {
            HumanManagedLoadout.recordManualEquipmentChange(human, equipmentSlot(slot), stack);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!HumanGunAcceptance.acceptsOwnerInput(stack)) return false;
        if (slot == HEAD) return Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.HEAD;
        if (slot == CHEST) return Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST;
        if (slot == LEGS) return Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.LEGS;
        if (slot == FEET) return Mob.getEquipmentSlotForItem(stack) == EquipmentSlot.FEET;
        return slot >= 0 && slot < TOTAL_SIZE;
    }

    @Override
    public void setChanged() {
        HumanData data = human.getData();
        if (data != null) data.markInventoryDirty();
    }

    @Override
    public boolean stillValid(Player player) {
        return human.isAlive() && HumanRelations.isOwnedBy(human, player)
                && player.distanceToSqr(human) <= 64.0D;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < TOTAL_SIZE; slot++) setItem(slot, ItemStack.EMPTY);
    }

    Human human() {
        return human;
    }

    private static EquipmentSlot equipmentSlot(int slot) {
        return switch (slot) {
            case MAIN_HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            default -> throw new IllegalArgumentException("Not an equipment slot: " + slot);
        };
    }
}
