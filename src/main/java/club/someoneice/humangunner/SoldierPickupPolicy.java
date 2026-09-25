package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;

/** Persistent owner choice controlling deliberate item-search movement. */
public final class SoldierPickupPolicy {
    private static final String ENABLED = HumanGunner.MOD_ID + ":soldier_pickup_enabled";

    private SoldierPickupPolicy() {
    }

    public static boolean isEnabled(Human human) {
        // Wild humans retain their normal autonomous scavenging. Hired humans
        // default to disabled to preserve the behavior of existing saves.
        return !human.hasOwner() || human.getPersistentData().getBoolean(ENABLED);
    }

    public static void set(Human human, boolean enabled) {
        if (!human.hasOwner()) return;
        human.getPersistentData().putBoolean(ENABLED, enabled);
        if (!enabled) {
            human.getNavigation().stop();
        }
    }

    static void clear(Human human) {
        human.getPersistentData().remove(ENABLED);
    }
}
