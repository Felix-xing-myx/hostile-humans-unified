package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Persistent combat policy for a hired human, independent of movement orders. */
public enum SoldierCombatMode {
    ACTIVE,
    PASSIVE_PROTECTION,
    SELF_DEFENSE;

    private enum Cause {
        SELF,
        OWNER_DEFENSE,
        OWNER_ASSAULT
    }

    private static final String MODE = "humangunner:soldier_combat_mode";
    private static final String AUTHORIZED_TARGET = "humangunner:authorized_combat_target";
    private static final String AUTHORIZED_CAUSE = "humangunner:authorized_combat_cause";
    private static final String AUTHORIZED_UNTIL = "humangunner:authorized_combat_until";
    private static final int MAX_AUTHORIZED_TARGETS = 8;
    private static final long AUTHORIZATION_TICKS = 200L;

    public static SoldierCombatMode get(Human human) {
        String value = human.getPersistentData().getString(MODE);
        try {
            return value.isEmpty() ? ACTIVE : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ACTIVE;
        }
    }

    public static void initialize(Human human) {
        if (hasCombatController(human) && !human.getPersistentData().contains(MODE)) {
            human.getPersistentData().putString(MODE, ACTIVE.name());
        }
    }

    public static void set(Human human, SoldierCombatMode mode) {
        if (!human.hasOwner()) return;
        human.getPersistentData().putString(MODE, mode.name());
        clearAuthorization(human);
        human.setTarget(null);
        human.setLastHurtByMob(null);
    }

    static void clear(Human human) {
        human.getPersistentData().remove(MODE);
        clearAuthorization(human);
        human.forgetSoldierTarget();
    }

    public static boolean allowsAutonomousTargeting(Human human) {
        return !hasCombatController(human) || get(human) == ACTIVE;
    }

    public static boolean authorizeSelfDefense(Human human, LivingEntity attacker) {
        return authorize(human, attacker, Cause.SELF);
    }

    public static boolean authorizeOwnerDefense(Human human, LivingEntity attacker) {
        return authorize(human, attacker, Cause.OWNER_DEFENSE);
    }

    public static boolean authorizeOwnerAssault(Human human, LivingEntity target) {
        return authorize(human, target, Cause.OWNER_ASSAULT);
    }

    public static boolean allowsTarget(Human human, LivingEntity target) {
        if (target == null) return false;
        if (!hasCombatController(human) || get(human) == ACTIVE) return true;
        return isExplicitlyAuthorizedAgainst(human, target);
    }

    public static boolean isExplicitlyAuthorizedAgainst(Human human, LivingEntity target) {
        if (target == null || !hasCombatController(human)) return false;
        long now = human.level().getGameTime();
        for (int slot = 0; slot < MAX_AUTHORIZED_TARGETS; slot++) {
            String targetKey = slotKey(AUTHORIZED_TARGET, slot);
            if (!human.getPersistentData().hasUUID(targetKey)
                    || !target.getUUID().equals(human.getPersistentData().getUUID(targetKey))
                    || now > human.getPersistentData().getLong(slotKey(AUTHORIZED_UNTIL, slot))) continue;
            return permits(get(human), readCause(human, slot));
        }
        return false;
    }

    public static boolean hasCombatController(Human human) {
        return HumanRelations.effectiveOwner(human) != null;
    }

    static boolean permits(SoldierCombatMode mode, String cause) {
        try {
            return permits(mode, Cause.valueOf(cause));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean authorize(Human human, LivingEntity target, Cause cause) {
        if (target == null || !target.isAlive()) return false;
        if (!hasCombatController(human)) return true;
        if (!permits(get(human), cause)) return false;
        long now = human.level().getGameTime();
        UUID[] targets = new UUID[MAX_AUTHORIZED_TARGETS];
        long[] expiries = new long[MAX_AUTHORIZED_TARGETS];
        for (int slot = 0; slot < MAX_AUTHORIZED_TARGETS; slot++) {
            String targetKey = slotKey(AUTHORIZED_TARGET, slot);
            if (human.getPersistentData().hasUUID(targetKey)) {
                targets[slot] = human.getPersistentData().getUUID(targetKey);
            }
            expiries[slot] = human.getPersistentData().getLong(slotKey(AUTHORIZED_UNTIL, slot));
        }
        int selected = selectAuthorizationSlot(target.getUUID(), targets, expiries, now);
        human.getPersistentData().putUUID(slotKey(AUTHORIZED_TARGET, selected), target.getUUID());
        human.getPersistentData().putString(slotKey(AUTHORIZED_CAUSE, selected), cause.name());
        human.getPersistentData().putLong(slotKey(AUTHORIZED_UNTIL, selected), now + AUTHORIZATION_TICKS);
        human.soldierCombatMemory.threatened(target.getUUID(), now);
        return true;
    }

    static int selectAuthorizationSlot(UUID requested, UUID[] targets, long[] expiries, long now) {
        int selected = 0;
        long earliestExpiry = Long.MAX_VALUE;
        for (int slot = 0; slot < targets.length; slot++) {
            if (requested.equals(targets[slot])) return slot;
            if (targets[slot] == null || expiries[slot] < now) return slot;
            if (expiries[slot] < earliestExpiry) {
                earliestExpiry = expiries[slot];
                selected = slot;
            }
        }
        return selected;
    }

    private static boolean permits(SoldierCombatMode mode, Cause cause) {
        return switch (mode) {
            case ACTIVE -> true;
            case PASSIVE_PROTECTION -> cause == Cause.SELF || cause == Cause.OWNER_DEFENSE;
            case SELF_DEFENSE -> cause == Cause.SELF;
        };
    }

    private static Cause readCause(Human human, int slot) {
        try {
            return Cause.valueOf(human.getPersistentData().getString(slotKey(AUTHORIZED_CAUSE, slot)));
        } catch (IllegalArgumentException ignored) {
            return Cause.OWNER_ASSAULT;
        }
    }

    private static void clearAuthorization(Human human) {
        human.getPersistentData().remove(AUTHORIZED_TARGET);
        human.getPersistentData().remove(AUTHORIZED_CAUSE);
        human.getPersistentData().remove(AUTHORIZED_UNTIL);
        for (int slot = 0; slot < MAX_AUTHORIZED_TARGETS; slot++) {
            human.getPersistentData().remove(slotKey(AUTHORIZED_TARGET, slot));
            human.getPersistentData().remove(slotKey(AUTHORIZED_CAUSE, slot));
            human.getPersistentData().remove(slotKey(AUTHORIZED_UNTIL, slot));
        }
    }

    private static String slotKey(String base, int slot) {
        return base + "_" + slot;
    }
}
