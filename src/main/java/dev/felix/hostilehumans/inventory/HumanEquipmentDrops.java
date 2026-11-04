package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.soldier.HostileBeaconFoodPolicy;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hired humans return all owned stacks. Wild humans retain their per-item rolls:
 * guns 1% (configurable), equipment 5%, and all other carried supplies 20%.
 */
public final class HumanEquipmentDrops {
    private static final double EQUIPMENT_DROP_CHANCE = 0.05D;
    private static final double SUPPLY_DROP_CHANCE = 0.20D;
    private record DeathInventory(List<ItemStack> stacks, boolean hired, boolean hostileBeacon) {}
    private static final Map<Human, DeathInventory> PRE_DEATH_SNAPSHOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Suppress only known legacy inventory producers, before the final drop event. */
    public static boolean captured(Human human) { return PRE_DEATH_SNAPSHOTS.containsKey(human); }

    private HumanEquipmentDrops() {
    }

    /** Capture ownership before the base mod mutates and clears equipment. */
    public static void captureBeforeDeath(Human human) {
        boolean hired = human.hasOwner();
        PRE_DEATH_SNAPSHOTS.put(human, new DeathInventory(collectOwned(human), hired,
                !hired && HumanRelations.isHostileBeaconOrigin(human)));
        // The entity is committed to death at this point (totem cancellation
        // has already returned). Suppress the base equipment-roll producer as
        // a producer boundary. Third-party event drops remain untouched. A strongly negative
        // chance also remains impossible under Looting adjustments.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            human.setDropChance(slot, -100.0F);
        }
    }

    public static void rebuildOwnedDrops(
            Human human, Collection<ItemEntity> drops, double gunDropChance
    ) {
        DeathInventory snapshot = PRE_DEATH_SNAPSHOTS.remove(human);
        if (snapshot == null) {
            // Without a capture receipt, existing producers own their drops. Never erase unknown loot.
            return;
        }
        List<ItemStack> owned = snapshot.stacks();

        Map<Item, List<ItemStack>> packed = new LinkedHashMap<>();
        for (ItemStack stack : owned) {
            double chance = dropChance(stack, gunDropChance);
            int kept = snapshot.hired() ? stack.getCount() : 0;
            if (!snapshot.hired()) {
                for (int unit = 0; unit < stack.getCount(); unit++) {
                    if (human.getRandom().nextDouble() < chance) {
                        kept++;
                    }
                }
            }
            if (kept <= 0) {
                continue;
            }
            pack(packed.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>()), stack.copyWithCount(kept));
        }
        for (List<ItemStack> group : packed.values()) {
            for (ItemStack stack : group) {
                ItemEntity drop = new ItemEntity(
                        human.level(), human.getX(), human.getY() + 0.35D, human.getZ(), stack
                );
                drop.setDefaultPickUpDelay();
                drops.add(drop);
            }
        }

        // Apply the beacon rule once to the complete final event collection,
        // including loot-table and third-party drops. Inventory rolls above
        // retain their existing chances; food is thinned only once afterwards.
        if (snapshot.hostileBeacon() && !snapshot.hired()) {
            filterHostileBeaconDrops(human, drops);
        }
    }

    private static void pack(List<ItemStack> group, ItemStack remaining) {
        int capacity = Math.max(1, Math.min(64, remaining.getMaxStackSize()));
        if (capacity == 1 && remaining.getCount() == 1) {
            group.add(remaining);
            return;
        }
        for (ItemStack stack : group) {
            int transferred = Math.min(remaining.getCount(),
                    Math.min(stack.getMaxStackSize(), capacity) - stack.getCount());
            if (transferred <= 0 || !ItemEntity.areMergable(remaining.copyWithCount(transferred), stack)) continue;
            stack.grow(transferred);
            remaining.shrink(transferred);
            if (remaining.isEmpty()) return;
        }
        while (!remaining.isEmpty()) group.add(remaining.split(Math.min(remaining.getCount(), capacity)));
    }

    private static List<ItemStack> collectOwned(Human human) {
        List<ItemStack> owned = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = human.getItemBySlot(slot);
            if (!equipped.isEmpty()) {
                owned.add(equipped.copy());
            }
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (!stored.isEmpty()) {
                    owned.add(stored.copy());
                }
            }
        }
        return owned;
    }

    private static double dropChance(ItemStack stack, double gunDropChance) {
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)) {
            return gunDropChance;
        }
        return isControlledEquipment(stack) ? EQUIPMENT_DROP_CHANCE : SUPPLY_DROP_CHANCE;
    }

    private static boolean isFood(ItemStack stack, Human human) {
        return !stack.isEmpty() && stack.isEdible() && stack.getFoodProperties(human) != null;
    }

    private static void filterHostileBeaconDrops(Human human, Collection<ItemEntity> drops) {
        List<ItemEntity> remove = new ArrayList<>();
        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            int kept = HostileBeaconFoodPolicy.retainedCount(stack.getCount(),
                    isFood(stack, human), stack.is(Items.ENCHANTED_GOLDEN_APPLE),
                    human.getRandom()::nextDouble);
            if (kept == 0) {
                remove.add(entity);
            } else {
                stack.setCount(kept);
            }
        }
        drops.removeAll(remove);
    }

    private static boolean isControlledEquipment(ItemStack stack) {
        return !stack.isEmpty()
                && (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(stack)
                || stack.getItem() instanceof ArmorItem
                || SpartanEquipmentCompat.isShield(stack)
                || HumanLootManager.isNonGunWeapon(stack));
    }
}
