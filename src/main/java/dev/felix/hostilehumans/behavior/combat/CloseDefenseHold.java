package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;

import java.util.Map;
import java.util.WeakHashMap;

/** Keeps automatic ranged restoration from cutting off a close-defense attack. */
public final class CloseDefenseHold {
    private static final int MIN_HOLD_TICKS = 8;
    private static final Map<Human, State> HOLDS = new WeakHashMap<>();
    private CloseDefenseHold() {}

    public static final class State {
        private final Object weapon;
        private int until;
        public State(Object weapon, int now) { this.weapon = weapon; until = now + MIN_HOLD_TICKS; }
        public void attack(int now, int animationTicks) { until = Math.max(until, now + Math.max(8, animationTicks)); }
        public boolean holds(Object currentWeapon, int now) { return holds(currentWeapon, now, false); }
        public boolean holds(Object currentWeapon, int now, boolean swinging) {
            return weapon == currentWeapon && (now < until || swinging);
        }
    }

    public static void begin(Human human) { HOLDS.put(human, new State(human.getMainHandItem(), human.tickCount)); }
    public static void clear(Human human) { HOLDS.remove(human); }
    public static boolean shouldHold(Human human) {
        State state = HOLDS.get(human);
        return state != null && !human.getMainHandItem().isEmpty() && !GunSupport.isGunItem(human.getMainHandItem())
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
