package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;

/** Clears legacy automatic standby without changing the player's stored orders. */
final class OwnerOfflinePolicy {
    private static final String ACTIVE = HumanGunner.MOD_ID + ":owner_offline_standby";
    private static final String WAS_SITTING = HumanGunner.MOD_ID + ":owner_offline_was_sitting";

    private OwnerOfflinePolicy() {}

    static void tick(Human human) {
        if (human.level().isClientSide) return;
        if (human.hasOwner() && human.getPersistentData().getBoolean(ACTIVE)) {
            human.setOrderedToSit(human.getPersistentData().getBoolean(WAS_SITTING));
        }
        clearForDismiss(human);
    }

    static void clearForDismiss(Human human) {
        human.getPersistentData().remove(ACTIVE);
        human.getPersistentData().remove(WAS_SITTING);
    }
}
