package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.entity.Human;


/** Short-lived movement hint preventing pursuit hops during a gun attack. */
public final class GunAttackMovementState {
    private static final String SUPPRESS_JUMP_UNTIL = "hostile_humans_unified:gun_attack_suppress_jump_until";

    private GunAttackMovementState() {
    }

    public static void markAttackActive(Human human) {
        human.getPersistentData().putInt(SUPPRESS_JUMP_UNTIL, human.tickCount + 1);
    }

    public static boolean isAttackActive(Human human) {
        return human.getPersistentData().getInt(SUPPRESS_JUMP_UNTIL) >= human.tickCount;
    }

    static void clear(Human human) {
        human.getPersistentData().remove(SUPPRESS_JUMP_UNTIL);
    }
}
