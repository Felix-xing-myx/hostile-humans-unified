package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Ground pickup only. Owner transfers, generation and recovery custody do not pass through this cap. */
final class ShieldPickupPolicy {
    private static final int BACKPACK_LIMIT = 3;
    private record Reserve(int count, int replaceSlot, double improvement) { }
    private ShieldPickupPolicy() { }

    private static Reserve inspect(Human human, ItemStack incoming) {
        var data = human.getData();
        if (data == null) return new Reserve(BACKPACK_LIMIT, -1, 0);
        int count = 0, worst = -1;
        double lowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (!SpartanEquipmentCompat.isShield(stored)) continue;
            count += stored.getCount(); // Count physical shields, including stackable addon shields.
            if (HumanSpawnEquipment.isBoundGear(stored) || stored.getCount() != 1) continue;
            double score = HumanLootManager.inventoryValue(human, stored);
            if (score < lowest) { lowest = score; worst = i; }
        }
        double improvement = HumanLootManager.inventoryValue(human, incoming) - lowest;
        return new Reserve(count, improvement > 1.5D ? worst : -1, improvement);
    }

    private static boolean canEquipEmptyHand(Human human) {
        return human.getOffhandItem().isEmpty() && !human.isUsingItem()
                && !RecoverySupplies.hasActiveUse(human);
    }

    static double benefit(Human human, ItemStack incoming, HumanLootManager.StorageSummary capacity) {
        if (!EquipmentCondition.canAutomaticallyCollect(incoming)) return 0;
        Reserve reserve = inspect(human, incoming);
        if (reserve.count() >= BACKPACK_LIMIT)
            return reserve.replaceSlot() < 0 ? 0 : 24 + reserve.improvement();
        var data = human.getData();
        if (data == null) return 0;
        if (canEquipEmptyHand(human)) return 80 + HumanLootManager.inventoryValue(human, incoming);
        var summary = capacity == null ? new HumanLootManager.StorageSummary(human, data) : capacity;
        double value = HumanLootManager.inventoryValue(human, incoming.copyWithCount(1));
        return summary.canStore(incoming.copyWithCount(1), value) ? value
                : reserve.replaceSlot() < 0 ? 0 : 24 + reserve.improvement();
    }

    static boolean collect(Human human, ItemEntity entity) {
        var data = human.getData();
        if (human.level().isClientSide || data == null || HumanInventorySession.isEditing(human)) return false;
        ItemStack ground = entity.getItem();
        if (!EquipmentCondition.canAutomaticallyCollect(ground)) return false;
        Reserve reserve = inspect(human, ground); // Recheck live inventory at commit, not the search snapshot.
        ItemStack replacement = ground.copyWithCount(1);
        if (reserve.count() < BACKPACK_LIMIT && canEquipEmptyHand(human)) {
            ShieldEnchantmentRoll.applyToShield(human, replacement);
            human.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, replacement);
            human.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    HumanSpawnEquipment.isBoundGear(replacement) ? 0 : 0.2F);
        } else if (reserve.count() < BACKPACK_LIMIT && new HumanLootManager.StorageSummary(human, data)
                .canStore(replacement, HumanLootManager.inventoryValue(human, replacement))) {
            ShieldEnchantmentRoll.applyToShield(human, replacement);
            if (!HumanLootManager.storeWithEviction(human, replacement)) return false;
        } else {
            if (reserve.replaceSlot() < 0) return false;
            ItemStack previous = data.getInventoryItem(reserve.replaceSlot()).copy();
            ItemEntity dropped = new ItemEntity(human.level(), human.getX(), human.getY() + 0.4D,
                    human.getZ(), previous);
            dropped.setPickUpDelay(60);
            if (!human.level().addFreshEntity(dropped)) return false;
            ShieldEnchantmentRoll.applyToShield(human, replacement);
            data.setInventoryItem(reserve.replaceSlot(), replacement);
        }
        ground.shrink(1); // Never absorb a whole pile past the cap.
        if (ground.isEmpty()) entity.discard();
        else entity.setItem(ground);
        return true;
    }
}
