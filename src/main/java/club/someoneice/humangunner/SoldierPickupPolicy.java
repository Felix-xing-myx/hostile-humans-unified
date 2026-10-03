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
        return (human.getLastHurtByMob() == null || sinceHit < 0 || sinceHit > 40)
                && SoldierOrder.allowsLootPosition(human, human.getX(), human.getZ());
    }

    static boolean enabled(boolean hired, boolean configured) {
        return !hired || configured;
    }

    static void clear(Human human) {
        human.getPersistentData().remove(ENABLED);
    }
}
