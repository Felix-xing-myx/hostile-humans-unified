package dev.felix.hostilehumans.behavior.combat;

import java.util.Map;
import java.util.Objects;

/** Immutable faction composition. No actor, item, target, cooldown or execution state lives here. */
public record CombatStrategySet(RangedStyle ranged, GunStyle gun, boolean reinforcedJump,
                         boolean dualWield, boolean shieldAdvance, ShieldStyle shield) {
    public enum RangedStyle { DEFAULT, FORTIFIED, MOBILE, VOLLEY, ADVANCE }
    public enum ShieldStyle { STANDARD, PRESSURE_ONLY }
    public enum GunStyle { DEFAULT, FORTIFIED, MOBILE, ASSAULT, MELEE_ASSAULT }

    private static final CombatStrategySet DEFAULT = new CombatStrategySet(
            RangedStyle.DEFAULT, GunStyle.DEFAULT, false, false, false, ShieldStyle.STANDARD);
    private static final Map<String, CombatStrategySet> FACTIONS = Map.of(
            "azure", new CombatStrategySet(RangedStyle.FORTIFIED, GunStyle.FORTIFIED, false, false, true, ShieldStyle.STANDARD),
            "verdant", new CombatStrategySet(RangedStyle.MOBILE, GunStyle.MOBILE, false, false, false, ShieldStyle.STANDARD),
            "crimson", new CombatStrategySet(RangedStyle.VOLLEY, GunStyle.ASSAULT, true, false, false, ShieldStyle.STANDARD),
            "ashen", new CombatStrategySet(RangedStyle.ADVANCE, GunStyle.MELEE_ASSAULT, true, true, false, ShieldStyle.PRESSURE_ONLY));

    public CombatStrategySet {
        Objects.requireNonNull(ranged);
        Objects.requireNonNull(gun);
        Objects.requireNonNull(shield);
    }

    public static CombatStrategySet forFaction(String faction) {
        return faction == null ? DEFAULT : FACTIONS.getOrDefault(faction, DEFAULT);
    }

    public RangedStyle ranged(boolean core, boolean enabled) {
        return core && enabled ? ranged : RangedStyle.DEFAULT;
    }

    /** Faction movement intent, independent of core-weapon bonuses. */
    public boolean keepsRangedDistance() { return ranged == RangedStyle.MOBILE; }

    public boolean allowsOrdinaryShield() { return shield == ShieldStyle.STANDARD; }

    public boolean reinforcedJump(boolean core, boolean enabled) { return reinforcedJump && core && enabled; }
    boolean shieldAdvance(boolean core, boolean enabled) { return shieldAdvance && core && enabled; }
    public boolean extendedGunRange(boolean core, boolean enabled) { return gun == GunStyle.MOBILE && core && enabled; }
}
