package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.ContentRegistry;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.equipment.FactionEquipmentConfig;
import dev.felix.hostilehumans.equipment.FactionEquipmentPolicy;
import dev.felix.hostilehumans.progression.TierAttributes;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Initial reserves only, after all weapon allocation and early-game substitutions finish. */
public final class HumanAmmoSpawn {
    private static final String GRANTED = "hostile_humans_unified:initial_ammo_granted";

    private HumanAmmoSpawn() { }

    public static void applyOnce(Human human) {
        if (human.level().isClientSide || human.getData() == null
                || human.getPersistentData().getBoolean(GRANTED)) return;
        boolean bow = RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem())
                || RangedWeaponCustody.isBowOrCrossbow(human.getOffhandItem());
        boolean gun = GunSupport.get().isGun(human.getMainHandItem())
                || GunSupport.get().isGun(human.getOffhandItem());
        var data = human.getData();
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            bow |= RangedWeaponCustody.isBowOrCrossbow(stack);
            gun |= GunSupport.get().isGun(stack);
        }
        var reserve = UnifiedConfig.get().ammoReserve(TierAttributes.key(human));
        // Set before storage: cancellation or later gear requests must not grant repeatedly.
        human.getPersistentData().putBoolean(GRANTED, true);
        if (bow) store(human, ContentRegistry.HUMAN_ARROW.get(), roll(human,
                reserve.arrowMin(), reserve.arrowMax()));
        if (gun) store(human, ContentRegistry.HUMAN_MAGAZINE.get(), roll(human,
                reserve.magazineMin(), reserve.magazineMax()));
    }

    private static int roll(Human human, int minimum, int maximum) {
        int base = minimum + human.getRandom().nextInt(maximum - minimum + 1);
        return FactionEquipmentPolicy.scaledCount(base,FactionEquipmentConfig.ammoMultiplier(human));
    }

    private static void store(Human human, Item item, int count) {
        ItemStack reserve = new ItemStack(item, count);
        int requested=count;
        RecoverySupplies.insertInventory(human.getData(),reserve);
        if(!reserve.isEmpty()) {
            String key="hostile_humans_unified:spawn_ammo_capacity_shortfall";
            human.getPersistentData().putInt(key,human.getPersistentData().getInt(key)+reserve.getCount());
            HumanRuntime.LOGGER.debug("Initial ammo truncated npc={} requested={} omitted={}",human.getUUID(),requested,reserve.getCount());
        }
    }
}
