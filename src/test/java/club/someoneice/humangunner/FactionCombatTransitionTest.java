package club.someoneice.humangunner;

import java.util.UUID;

/** Scenario boundaries for strategy selection and actual-damage direction memory. No Minecraft launch. */
public final class FactionCombatTransitionTest {
    public static void main(String[] args) throws Exception {
        boolean melee = false;
        int switches = 0;
        for (double distance : new double[]{8,4.4,4.6,4.3,5.9,4.5,5.8,6.1}) {
            boolean next = WeaponTransitionPolicy.holdsMelee(distance*distance, melee, true);
            if (next != melee) switches++;
            melee = next;
        }
        check(switches == 2 && !melee, "close gun engagement has one entry and one exit, no range-edge oscillation");
        check(!WeaponTransitionPolicy.holdsMelee(4, false, false), "ordinary loaded guns do not inherit assault melee");
        check(!WeaponTransitionPolicy.holdsMelee(Double.NaN, true, true), "invalid range cannot retain melee");
        check(WeaponTransitionPolicy.holdsBowMelee(36, false), "bow enters at six");
        check(WeaponTransitionPolicy.holdsBowMelee(49, true)
                && !WeaponTransitionPolicy.holdsBowMelee(49, false), "bow keeps current choice inside buffer");
        check(!WeaponTransitionPolicy.holdsBowMelee(57, true), "bow leaves past seven and a half");
        for (String faction : new String[]{"azure","verdant","crimson","neutral","roamer"})
            check(CombatStrategySet.forFaction(faction).allowsOrdinaryShield(), "ordinary shield policy: " + faction);
        check(!CombatStrategySet.forFaction("ashen").allowsOrdinaryShield(), "gray permission does not depend on current hand layout");
        check(!ShieldDefensePolicy.worthMeleeGuard(3, 40), "ordinary zombie chip damage yields to attacks");
        check(!ShieldDefensePolicy.worthMeleeGuard(0, 40), "harmless contact does not request guard");
        check(ShieldDefensePolicy.worthMeleeGuard(8, 100), "heavy melee still warrants guard");
        check(!ShieldDefensePolicy.worthMeleeGuard(5, 200), "durable fighter judges damage relative to health");
        check(!ShieldDefensePolicy.worthMeleeGuard(Double.NaN, 40), "unknown damage cannot fabricate urgency");
        var mixed = new CombatStrategySet(CombatStrategySet.RangedStyle.DEFAULT, CombatStrategySet.GunStyle.DEFAULT,
                false, true, false, CombatStrategySet.ShieldStyle.STANDARD);
        check(mixed.allowsOrdinaryShield(), "future dual faction can explicitly choose a different shield style");
        var sources = new PressureThreatDirections();
        UUID front = UUID.randomUUID(), rear = UUID.randomUUID();
        sources.record(front, 0, 30, 8, 100);
        sources.record(rear, 1, -150, 1, 100);
        near(sources.yaw(1, 0), 30, "single meaningful source faces fully; chip source cannot redirect");
        sources.record(rear, 2, -150, 2, 100);
        near(sources.yaw(2, 0), 30, "greater actual pressure wins over opposite weaker attacker");
        sources.record(rear, 3, -150, 12, 100);
        near(sources.yaw(3, 0), -150, "new genuinely stronger source can change direction");
        check(sources.yaw(43, 0) == null, "old impact directions expire");
        sources.record(front, 44, Float.NaN, 100, 100);
        check(sources.yaw(44, 0) == null, "invalid yaw cannot poison direction memory");
        sources.record(front, 45, 170, 8, 100); sources.record(rear, 45, -170, 8, 100);
        check(Math.abs(sources.yaw(45, 0)) >= 170, "wraparound pressure covers rear rather than averaging to front");
        sources.clear(); check(sources.yaw(46, 0) == null, "session cancellation clears all directions");
        for (String method : new String[]{"raise","wantsCombatNow","canPreemptivelyBlockProjectile","tickAdvance","tickBodyguard"})
            check(calls("club/someoneice/humangunner/ShieldDefenseController", method, "allowsShieldOwner"),
                    "all shield entrypoints share faction permission: " + method);
        check(calls("club/someoneice/humangunner/ShieldDefenseController", "facePressure", "yaw")
                && !calls("club/someoneice/humangunner/ShieldDefenseController", "facePressure", "detectDefense"),
                "pressure facing cannot overwrite actual-impact memory using target snapshot");
        check(calls("club/someoneice/humangunner/ShieldDefenseController", "predictsIncomingMelee", "worthMeleeGuard"),
                "real ordinary guard assesses damage before geometry");
        check(calls("club/someoneice/humangunner/GunCustody", "restorePrimaryGun", "holdsMelee")
                && calls("club/someoneice/humangunner/GunCustody", "beginCloseMeleeCounter", "holdsMelee"),
                "actual gun entry/exit use tested transition policy");
        check(calls("club/someoneice/humangunner/RangedHybridMeleeGoal", "tick", "prefersRangedSeparation")
                && calls("club/someoneice/humangunner/RangedHybridMeleeGoal", "tickSeparation", "holdsBowMelee"),
                "native melee delegate respects faction request and same departure buffer");
        String render = "com/craftix/hostile_humans/client/renderer/";
        for (String[] entry : new String[][]{{"HumanGunPresentation","mainArm"},
                {"HumanAnimatedItemInHandLayer","renderArmWithItem"}, {"TaczNpcAnimator","canAnimate"}})
            check(calls(render+entry[0], entry[1], "activeShield"), "shared synchronized shield presentation: " + entry[0]);
        check(calls("club/someoneice/humangunner/NpcDualWield", "tickDefaultLayout", "request")
                && calls("club/someoneice/humangunner/NpcDualWield", "tickDefaultLayout", "stow"),
                "daily layout only moves real equipment through exchange");
        check(calls("club/someoneice/humangunner/NpcDualWield", "tickDefaultLayout", "hasMainHandWeapon"),
                "daily offhand layout requires a current main weapon before restoring a remembered pair");
        System.out.println("FactionCombatTransitionTest passed");
    }
    private static boolean calls(String owner, String methodName, String calledName) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = FactionCombatTransitionTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class: " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) if (method.name.equals(methodName))
            for (var instruction : method.instructions)
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals(calledName)) return true;
        return false;
    }
    private static void near(Float value, float expected, String message) {
        check(value != null && Math.abs(ShieldDefensePolicy.wrap(value-expected)) < .01, message + ": " + value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
