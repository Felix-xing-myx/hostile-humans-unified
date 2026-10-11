package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Called by the staggered supply check. One persisted warning per depletion episode, not per weapon. */
final class HiredHumanAmmoWarning {
    private static final String WARNED = HumanGunner.MOD_ID + ":dialogue_ammo_empty";
    private static final String COST = HumanGunner.MOD_ID + ":dialogue_ammo_reload_cost";
    private static final int ARROWS = 1, MAGAZINES = 2;
    private HiredHumanAmmoWarning() { }

    static void reset(Human human) {
        human.getPersistentData().remove(WARNED);
        human.getPersistentData().remove(COST);
    }

    static boolean tick(Human human, ServerPlayer owner) {
        if (human.level().isClientSide || !human.hasOwner() || !human.isAlive()
                || !HumanRelations.isOwnedBy(human, owner) || !UnifiedConfig.get().ammoConsumptionEnabled()
                || HumanInventorySession.isEditing(human)) return false;
        var data = human.getData();
        if (data == null) return false;
        int arrows = HumanAmmo.arrowCount(human), magazines = HumanAmmo.magazineCount(human);
        int warned = human.getPersistentData().getInt(WARNED);
        if (warned != 0) {
            boolean restocked = ((warned & ARROWS) == 0 || arrows > 0)
                    && ((warned & MAGAZINES) == 0 || magazines >= Math.max(1, human.getPersistentData().getInt(COST)));
            if (!restocked) return false;
            reset(human); // Weapon switching alone cannot rearm a warning.
        }
        boolean bow = false, gun = false, usableGun = false;
        int reloadCost = Integer.MAX_VALUE;
        var guns = GunSupport.get();
        // Include stowed weapons: ammo fallback must not erase the shortage before this check runs.
        for (int slot = -2; slot < data.getInventoryItemsSize(); slot++) {
            ItemStack stack = slot == -2 ? human.getMainHandItem()
                    : slot == -1 ? human.getOffhandItem() : data.getInventoryItem(slot);
            bow |= RangedWeaponCustody.isBowOrCrossbow(stack);
            if (!guns.isGun(stack) || !HumanGunAcceptance.accepts(stack)) continue;
            gun = true;
            int cost = HumanAmmo.magazineCost(stack);
            reloadCost = Math.min(reloadCost, cost);
            usableGun |= guns.hasLoadedAmmo(stack) || magazines >= cost
                    || (slot == -2 && guns.isReloading(human));
        }
        int exhausted = (bow && arrows == 0 ? ARROWS : 0) | (gun && !usableGun ? MAGAZINES : 0);
        if (exhausted == 0) return false;
        String kind = exhausted == ARROWS ? "arrows" : exhausted == MAGAZINES ? "magazines" : "both";
        if (!SoldierMessageDispatcher.tryStatus(owner, human,
                Component.translatable("dialogue.hostile_humans_unified.ammo_empty." + kind))) return false;
        human.getPersistentData().putInt(WARNED, exhausted);
        if ((exhausted & MAGAZINES) != 0) human.getPersistentData().putInt(COST, reloadCost);
        return true;
    }
}
