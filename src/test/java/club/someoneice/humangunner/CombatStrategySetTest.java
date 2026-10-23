package club.someoneice.humangunner;

/** Composition and capability fallbacks through the same selection seam used by executors. */
public final class CombatStrategySetTest {
    public static void main(String[] args) {
        var defaults = CombatStrategySet.forFaction("");
        for (String faction : new String[]{"", "neutral", "roamer", "custom:azure", "unknown"}) {
            check(CombatStrategySet.forFaction(faction).equals(defaults), "unknown faction uses default: " + faction);
        }
        check(CombatStrategySet.forFaction(null).equals(defaults), "absent faction uses default");
        var blue = CombatStrategySet.forFaction("azure");
        var green = CombatStrategySet.forFaction("verdant");
        var red = CombatStrategySet.forFaction("crimson");
        var gray = CombatStrategySet.forFaction("ashen");
        check(blue.shieldAdvance(true, true) && !gray.shieldAdvance(true, true), "only blue shield advances");
        check(gray.dualWield() && !red.dualWield(), "only gray admits dual-wield capability");
        for (var strategy : new CombatStrategySet[]{defaults, blue, green, red, gray}) {
            for (boolean core : new boolean[]{false, true}) for (boolean enabled : new boolean[]{false, true}) {
                if (!core || !enabled) {
                    check(strategy.ranged(core, enabled) == CombatStrategySet.RangedStyle.DEFAULT, "no ranged bonus without live core capability");
                    check(!strategy.shieldAdvance(core, enabled), "no shield advance without live core capability");
                    check(!strategy.reinforcedJump(core, enabled), "no jump bonus without live core capability");
                    check(!strategy.extendedGunRange(core, enabled), "no gun range extension without live core capability");
                }
            }
        }
        check(red.reinforcedJump(true, true) && gray.reinforcedJump(true, true), "red and gray jump composition");
        check(!blue.reinforcedJump(true, true) && !green.reinforcedJump(true, true), "blue and green retain normal pursuit");
        check(green.extendedGunRange(true, true) && !blue.extendedGunRange(true, true), "only green core extends range");
        check(RangedCombatProfile.of(false, false, green.ranged(true, true)).doubleArrow(), "green composition selects actual double arrows");
        check(RangedCombatProfile.of(false, false, red.ranged(true, true)).tripleArrow(), "red composition selects actual volley");
        check(RangedCombatProfile.of(false, false, gray.ranged(true, true)).advance(), "gray bow advances");
        check(!RangedCombatProfile.of(false, true, gray.ranged(true, true)).advance(), "trident does not inherit bow advance");
        check(GunDoctrine.style(blue.gun(), "rifle", true).reloadDuration() == .7, "blue actual rifle duration");
        check(!GunDoctrine.style(red.gun(), "shotgun", true).forceMelee(), "red shotgun keeps close firing cycle");
        check(GunDoctrine.style(gray.gun(), "shotgun", true).forceMelee(), "gray shotgun uses melee strategy");
        check(GunDoctrine.style(green.gun(), "pistol", false).forceMelee(), "green non-core gun uses existing fallback");
        System.out.println("CombatStrategySetTest passed");
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
