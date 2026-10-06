package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps automatic ranged restoration from cutting off a close-defense attack. */
public final class CloseDefenseHold {
    private static final int MIN_HOLD_TICKS = 40;
    private static final Map<Human, State> HOLDS = new WeakHashMap<>();
    private CloseDefenseHold() {}

    static final class State {
        private final Object weapon;
        private int until;
        State(Object weapon, int now) { this.weapon = weapon; until = now + MIN_HOLD_TICKS; }
        void attack(int now, int animationTicks) { until = Math.max(until, now + Math.max(8, animationTicks)); }
        boolean holds(Object currentWeapon, int now) { return holds(currentWeapon, now, false); }
        boolean holds(Object currentWeapon, int now, boolean swinging) {
            return weapon == currentWeapon && (now < until || swinging);
        }
    }

    static void begin(Human human) { HOLDS.put(human, new State(human.getMainHandItem(), human.tickCount)); }
    static void clear(Human human) { HOLDS.remove(human); }
    static boolean shouldHold(Human human) {
        State state = HOLDS.get(human);
        return state != null && !human.getMainHandItem().isEmpty() && human.humanGunner$canMeleeAttack()
                && state.holds(human.getMainHandItem(), human.tickCount, human.swinging);
    }
    public static void attack(Human human, int animationTicks) {
        if (!GunCustody.isActive(human) && !RangedWeaponCustody.isActive(human)) return;
        State state = HOLDS.get(human);
        if (state == null || state.weapon != human.getMainHandItem()) {
            begin(human);
            state = HOLDS.get(human);
        }
        state.attack(human.tickCount, animationTicks);
    }
}
