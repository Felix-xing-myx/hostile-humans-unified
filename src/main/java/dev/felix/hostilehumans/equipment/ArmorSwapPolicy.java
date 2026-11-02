package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import dev.felix.hostilehumans.inventory.HumanManagedLoadout;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Shared automatic armor gate for ground pickups and backpack optimization. */
public final class ArmorSwapPolicy {
    private static final Map<Human, long[]> LAST_SWAP = new WeakHashMap<>();
    private ArmorSwapPolicy() {}

    public static boolean isArmor(EquipmentSlot slot) { return slot.getType() == EquipmentSlot.Type.ARMOR; }

    public static boolean allows(Human human, EquipmentSlot slot, ItemStack candidate) {
        if (!isArmor(slot) || candidate.isEmpty() || HumanManagedLoadout.isArmourLocked(human, slot)) return false;
        ItemStack current = human.getItemBySlot(slot);
        if (HumanSpawnEquipment.isBoundGear(current)) return false;
        ArmorSwapConfig config = UnifiedConfig.get().armorSwap();
        long[] last = LAST_SWAP.get(human);
        long now = human.level().getGameTime();
        long previous = last == null ? Long.MIN_VALUE : last[slot.getIndex()];
        boolean cooling = previous != Long.MIN_VALUE && now >= previous && now - previous < config.cooldownTicks();
        return config.shouldReplace(current.isEmpty(), HumanLootManager.fullEquipmentScore(current, slot),
                EquipmentCondition.remainingFraction(current), HumanLootManager.fullEquipmentScore(candidate, slot),
                EquipmentCondition.remainingFraction(candidate), cooling);
    }

    /** Call only after the real inventory exchange succeeds. Queries never start a timer. */
    public static void equipped(Human human, EquipmentSlot slot) {
        if (!isArmor(slot)) return;
        long[] last = LAST_SWAP.computeIfAbsent(human,
                ignored -> new long[]{Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE});
        last[slot.getIndex()] = human.level().getGameTime();
    }
}
