package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.ranged.AmmoWeaponSelection;

import java.util.Locale;

/** Server-owned preference; Forge persistent data preserves it with the human. */
public enum CombatPreference {
    DEFAULT, GUN, BOW, CROSSBOW, TRIDENT, MELEE;
    private static final String KEY = "hostile_humans_unified:combat_preference";
    public static CombatPreference get(Human human) {
        try { return valueOf(human.getPersistentData().getString(KEY).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return DEFAULT; }
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
