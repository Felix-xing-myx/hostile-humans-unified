package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;

/** Short-lived movement hint preventing pursuit hops during a gun attack. */
final class GunAttackMovementState {
    private static final String SUPPRESS_JUMP_UNTIL = "humangunner:gun_attack_suppress_jump_until";

    private GunAttackMovementState() {
    }

    static void markAttackActive(Human human) {
        human.getPersistentData().putInt(SUPPRESS_JUMP_UNTIL, human.tickCount + 1);
    }

    static boolean isAttackActive(Human human) {
        return human.getPersistentData().getInt(SUPPRESS_JUMP_UNTIL) >= human.tickCount;
    }

    static void clear(Human human) {
        human.getPersistentData().remove(SUPPRESS_JUMP_UNTIL);
    }
}
