package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Initial reserves only, after all weapon allocation and early-game substitutions finish. */
final class HumanAmmoSpawn {
    private static final String GRANTED = "humangunner:initial_ammo_granted";

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
        return minimum + human.getRandom().nextInt(maximum - minimum + 1);
    }

    private static void store(Human human, Item item, int count) {
        ItemStack reserve = new ItemStack(item, count);
        if (HumanInventoryCustody.storePickupTransactionally(human.getData(), reserve)) return;
        // Custom equipment can fill all slots. Use existing valuation/eviction, never overwrite gear.
        while (!reserve.isEmpty()) {
            ItemStack portion = reserve.copyWithCount(Math.min(reserve.getCount(), reserve.getMaxStackSize()));
            int before = portion.getCount();
            HumanLootManager.storeWithEviction(human, portion);
            int stored = before - portion.getCount();
            reserve.shrink(stored);
            if (stored == 0) {
                // Preserve the supply as a physical drop if every remaining slot is protected.
                ItemStack overflow = reserve.copyWithCount(Math.min(reserve.getCount(), reserve.getMaxStackSize()));
                var dropped = human.spawnAtLocation(overflow);
                if (dropped == null) return;
                dropped.setPickUpDelay(80);
                reserve.shrink(overflow.getCount());
            }
        }
    }
}
