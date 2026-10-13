package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;

/** One permission gate for search movement, nearby collection and vanilla pickup. */
public final class SoldierPickupPolicy {
    static final String ENABLED = HumanGunner.MOD_ID + ":soldier_pickup_enabled";

    private SoldierPickupPolicy() {
    }

    public static boolean isEnabled(Human human) {
        // Wild humans retain their normal autonomous scavenging. Hired humans
        // default to disabled to preserve the behavior of existing saves.
        return enabled(human.hasOwner(), human.getPersistentData().getBoolean(ENABLED));
    }

    public static void set(Human human, boolean enabled) {
        if (!human.hasOwner()) return;
        human.getPersistentData().putBoolean(ENABLED, enabled);
        if (!enabled && human.isActivelyCollectingLoot()) {
            // The pickup goal releases only its own path on its next stop.
            // Do not stop a combat/guard path which may already have replaced it.
            human.setActivelyCollectingLoot(false);
            human.setPursuingWaterLoot(false);
        }
    }

    public static boolean canCollectNow(Human human) {
        if (!isEnabled(human) || !human.isAlive() || human.isDeadOrDying() || human.isNoAi()
                || human.getTarget() != null || human.isFleeing || human.isUsingItem() || human.isSleeping()
                || human.isInLava() || human.isOnFire() || SoldierOrder.isReturningFromRetreat(human)
                || HumanGunner.isManualInventoryOpen(human)
                || RecoverySupplies.hasHandCustody(human)) return false;
        int sinceHit = human.tickCount - human.getLastHurtByMobTimestamp();
        long sinceCombat = human.tickCount - human.lastCombatTime;
        if (human.isInWater() && human.lastCombatTime > 0 && sinceCombat >= 0 && sinceCombat <= 60) return false;
        return (human.getLastHurtByMob() == null || sinceHit < 0 || sinceHit > 40)
                && SoldierOrder.allowsLootPosition(human, human.getX(), human.getZ());
    }

    /** Separate from idle search permission: nearby emergency supplies never own a path. */
    static boolean canCombatResupplyNow(Human human) {
        if (human.level().isClientSide || !human.hasOwner() || !isEnabled(human) || !human.isAlive()
                || human.isDeadOrDying() || human.isNoAi() || human.isSleeping() || human.isInLava()
                || HumanInventorySession.isEditing(human) || HumanGunner.isManualInventoryOpen(human)
                || human.isActivelyCollectingLoot() || canCollectNow(human)
                || !SoldierOrder.allowsLootPosition(human, human.getX(), human.getZ())) return false;
        long sinceCombat = human.tickCount - human.lastCombatTime;
        return human.getTarget() != null || human.isFleeing
                || human.lastCombatTime > 0 && sinceCombat >= 0 && sinceCombat <= 60;
    }

    /** Resupply follows owned weapons, not current ammo or the temporary held weapon. */
    static boolean canCollectItem(Human human, net.minecraft.world.item.ItemStack stack) {
        if (!HumanAmmo.isAmmo(stack)) return true;
        var data = human.getData();
        if (HumanAmmo.isArrow(stack)) {
            return RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem())
                    || RangedWeaponCustody.isBowOrCrossbow(human.getOffhandItem())
                    || data != null && data.hasStoredBowOrCrossbow(human.tickCount);
        }
        return GunCustody.hasOwnedGun(human);
    }

    static boolean enabled(boolean hired, boolean configured) {
        return !hired || configured;
    }

    static void clear(Human human) {
        human.getPersistentData().remove(ENABLED);
    }
}
