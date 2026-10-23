package club.someoneice.humangunner;

import java.util.Map;
import java.util.Objects;

/** Immutable faction composition. No actor, item, target, cooldown or execution state lives here. */
record CombatStrategySet(RangedStyle ranged, GunStyle gun, boolean reinforcedJump,
                         boolean dualWield, boolean shieldAdvance, ShieldStyle shield) {
    enum RangedStyle { DEFAULT, FORTIFIED, MOBILE, VOLLEY, ADVANCE }
    enum ShieldStyle { STANDARD, PRESSURE_ONLY }
    enum GunStyle { DEFAULT, FORTIFIED, MOBILE, ASSAULT, MELEE_ASSAULT }

    private static final CombatStrategySet DEFAULT = new CombatStrategySet(
            RangedStyle.DEFAULT, GunStyle.DEFAULT, false, false, false, ShieldStyle.STANDARD);
    private static final Map<String, CombatStrategySet> FACTIONS = Map.of(
            "azure", new CombatStrategySet(RangedStyle.FORTIFIED, GunStyle.FORTIFIED, false, false, true, ShieldStyle.STANDARD),
            "verdant", new CombatStrategySet(RangedStyle.MOBILE, GunStyle.MOBILE, false, false, false, ShieldStyle.STANDARD),
            "crimson", new CombatStrategySet(RangedStyle.VOLLEY, GunStyle.ASSAULT, true, false, false, ShieldStyle.STANDARD),
            "ashen", new CombatStrategySet(RangedStyle.ADVANCE, GunStyle.MELEE_ASSAULT, true, true, false, ShieldStyle.PRESSURE_ONLY));

    CombatStrategySet {
        Objects.requireNonNull(ranged);
        Objects.requireNonNull(gun);
        Objects.requireNonNull(shield);
    }

    static CombatStrategySet forFaction(String faction) {
        return faction == null ? DEFAULT : FACTIONS.getOrDefault(faction, DEFAULT);
    }

    RangedStyle ranged(boolean core, boolean enabled) {
        return core && enabled ? ranged : RangedStyle.DEFAULT;
    }

    /** Faction movement intent, independent of core-weapon bonuses. */
    boolean keepsRangedDistance() { return ranged == RangedStyle.MOBILE; }

    boolean allowsOrdinaryShield() { return shield == ShieldStyle.STANDARD; }

    boolean reinforcedJump(boolean core, boolean enabled) { return reinforcedJump && core && enabled; }
    boolean shieldAdvance(boolean core, boolean enabled) { return shieldAdvance && core && enabled; }
    boolean extendedGunRange(boolean core, boolean enabled) { return gun == GunStyle.MOBILE && core && enabled; }
}
