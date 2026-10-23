package club.someoneice.humangunner;

import java.util.Arrays;

/** Deterministic strategy arithmetic, independent from random shots and native inventory. */
public final class RangedCombatProfileTest {
    public static void main(String[] args) {
        profile(false, false, "", 32, 16, 24, .50, 1, 1);
        profile(true, false, "", 48, 24, 36, .30, 1, 1);
        profile(false, false, "azure", 32, 16, 24, .30, 1.40, 1);
        profile(true, false, "azure", 48, 24, 36, .10, 1, 1.30);
        profile(false, false, "verdant", 32, 16, 24, 1.25, 1, 1);
        profile(true, false, "verdant", 48, 24, 36, 1.25, 1, 1);
        var crossbow = RangedCombatProfile.of(true, false, "verdant");
        check(crossbow.doubleArrow() && !crossbow.tripleArrow() && crossbow.savingChance() == .40,
                "green crossbow gets two independently paid native volleys");
        check(!RangedCombatProfile.of(false, true, "verdant").doubleArrow(), "trident has no arrow burst");
        check(RangedCombatProfile.of(true, false, "azure").savingChance() == 0, "blue crossbow unchanged");
        check(ArrowVolleyPolicy.angles(false, true, 1).length * 2 == 6
                && ArrowVolleyPolicy.cost(false, 1) * 2 == 2, "two multishot volleys: six projectiles, two payments");
        profile(false, false, "crimson", 19, 8, 12, .50, 1, 1);
        profile(true, false, "crimson", 28, 12, 18, .30, 1, 1);
        profile(false, true, "", 16, 6, 16, .65, 1, 1);
        profile(false, true, "azure", 16, 6, 16, .35, 1, 1.20);
        check(RangedCombatProfile.of(false, false, "ashen").advance(), "gray advances");
        check(!RangedCombatProfile.of(false, true, "ashen").advance(), "trident never gray charge");
        check(RangedCombatProfile.of(false, false, "verdant").doubleArrow(), "green two arrows");
        check(RangedCombatProfile.of(false, false, "azure").savingChance() == .40, "blue independent saving");
        check(RangedCombatProfile.of(false, false, "verdant").savingChance() == .40, "green independent saving");
        for (int reserve = 0; reserve <= 5; reserve++) {
            float[] angles = ArrowVolleyPolicy.angles(true, true, reserve);
            check(angles.length == (reserve == 0 ? 0 : reserve < 3 ? 1 : 5), "red low ammo fallback");
            check(ArrowVolleyPolicy.cost(true, reserve) == (reserve == 0 ? 0 : reserve < 3 ? 1 : 3), "red physical cost");
        }
        check(Arrays.equals(ArrowVolleyPolicy.angles(true, true, 3), new float[]{0,-4,4,-8,8}), "additive 40/80 percent angles");
        check(Arrays.equals(ArrowVolleyPolicy.angles(false, true, 1), new float[]{0,-10,10}), "ordinary native multishot");
        check(BowRangePolicy.orbitForwardInput(15*15,16,24) < 0, "backpedal below band");
        check(BowRangePolicy.orbitForwardInput(20*20,16,24) == 0, "lateral in band");
        check(BowRangePolicy.orbitForwardInput(25*25,16,24) > 0, "advance toward band");
        ArrowAttackCadence cadence = new ArrowAttackCadence();
        cadence.bowInterval(32);
        cadence.bowShot(100, 1.4);
        check(!cadence.canDrawBow(111) && cadence.canDrawBow(112), "blue complete draw cooldown");
        check(!cadence.canFireBow(116) && cadence.canFireBow(117), "blue full cycle divided by 1.4");
        System.out.println("RangedCombatProfileTest passed");
    }
    private static void profile(boolean crossbow, boolean trident, String faction, double max, double low,
                                double high, double speed, double rate, double damage) {
        var profile = RangedCombatProfile.of(crossbow, trident, faction);
        check(profile.maximum() == max && profile.minimumBand() == low && profile.maximumBand() == high,
                "range " + faction + crossbow + trident);
        check(profile.strafeSpeed() == speed && profile.attackRate() == rate && profile.damage() == damage,
                "faction modifiers " + faction + crossbow + trident);
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
