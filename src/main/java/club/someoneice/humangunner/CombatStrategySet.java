package club.someoneice.humangunner;

import java.util.Map;
import java.util.Objects;

/** Immutable faction composition. No actor, item, target, cooldown or execution state lives here. */
record CombatStrategySet(RangedStyle ranged, GunStyle gun, boolean reinforcedJump,
                         boolean dualWield, boolean shieldAdvance) {
    enum RangedStyle { DEFAULT, FORTIFIED, MOBILE, VOLLEY, ADVANCE }
    enum GunStyle { DEFAULT, FORTIFIED, MOBILE, ASSAULT, MELEE_ASSAULT }

    private static final CombatStrategySet DEFAULT = new CombatStrategySet(
            RangedStyle.DEFAULT, GunStyle.DEFAULT, false, false, false);
    private static final Map<String, CombatStrategySet> FACTIONS = Map.of(
            "azure", new CombatStrategySet(RangedStyle.FORTIFIED, GunStyle.FORTIFIED, false, false, true),
            "verdant", new CombatStrategySet(RangedStyle.MOBILE, GunStyle.MOBILE, false, false, false),
            "crimson", new CombatStrategySet(RangedStyle.VOLLEY, GunStyle.ASSAULT, true, false, false),
            "ashen", new CombatStrategySet(RangedStyle.ADVANCE, GunStyle.MELEE_ASSAULT, true, true, false));

    CombatStrategySet {
        Objects.requireNonNull(ranged);
        Objects.requireNonNull(gun);
    }

    static CombatStrategySet forFaction(String faction) {
        return faction == null ? DEFAULT : FACTIONS.getOrDefault(faction, DEFAULT);
    }

    RangedStyle ranged(boolean core, boolean enabled) {
        return core && enabled ? ranged : RangedStyle.DEFAULT;
    }

    boolean reinforcedJump(boolean core, boolean enabled) { return reinforcedJump && core && enabled; }
    boolean shieldAdvance(boolean core, boolean enabled) { return shieldAdvance && core && enabled; }
    boolean extendedGunRange(boolean core, boolean enabled) { return gun == GunStyle.MOBILE && core && enabled; }
}
