package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.ranged.AmmoWeaponSelection;

/** Server-owned preference; Forge persistent data preserves it with the human. */
public enum CombatPreference {
    DEFAULT, GUN, BOW, CROSSBOW, TRIDENT, MELEE;
    private static final String KEY = "hostile_humans_unified:combat_preference";
    private static final CombatPreference[] VALUES = values();

    public static CombatPreference get(Human human) {
        String stored = human.getPersistentData().getString(KEY);
        if (stored.isEmpty()) return DEFAULT;
        for (CombatPreference value : VALUES) if (value.name().equalsIgnoreCase(stored)) return value;
        return DEFAULT;
    }
    public static void set(Human human, CombatPreference value) {
        if (!human.level().isClientSide) {
            CombatPreference desired = value == null ? DEFAULT : value;
            if (get(human) == desired) return;
            human.getPersistentData().putString(KEY, desired.name());
            AmmoWeaponSelection.preferenceChanged(human);
        }
    }
}
