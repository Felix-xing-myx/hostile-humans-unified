package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Initial reserves only, after all weapon allocation and early-game substitutions finish. */
final class HumanAmmoSpawn {
    private static final String GRANTED = "hostile_humans_unified:initial_ammo_granted";

    private HumanAmmoSpawn() { }

    static void applyOnce(Human human) {
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
        if (bow) store(human, HumanGunnerRegistries.HUMAN_ARROW.get(), roll(human,
                reserve.arrowMin(), reserve.arrowMax()));
        if (gun) store(human, HumanGunnerRegistries.HUMAN_MAGAZINE.get(), roll(human,
                reserve.magazineMin(), reserve.magazineMax()));
    }

    private static int roll(Human human, int minimum, int maximum) {
        int base = minimum + human.getRandom().nextInt(maximum - minimum + 1);
        return FactionEquipmentPolicy.scaledCount(base,FactionEquipmentConfig.of(human).number("ammo_multiplier",1,0,100));
    }

    private static void store(Human human, Item item, int count) {
        ItemStack reserve = new ItemStack(item, count);
        int requested=count;
        RecoverySupplies.insertInventory(human.getData(),reserve);
        if(!reserve.isEmpty()) {
            String key="hostile_humans_unified:spawn_ammo_capacity_shortfall";
            human.getPersistentData().putInt(key,human.getPersistentData().getInt(key)+reserve.getCount());
            HumanGunner.LOGGER.debug("Initial ammo truncated npc={} requested={} omitted={}",human.getUUID(),requested,reserve.getCount());
        }
    }
}
