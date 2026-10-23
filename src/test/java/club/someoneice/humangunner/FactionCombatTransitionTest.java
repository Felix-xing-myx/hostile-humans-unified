package club.someoneice.humangunner;

import java.util.UUID;

/** Scenario boundaries for strategy selection and actual-damage direction memory. No Minecraft launch. */
public final class FactionCombatTransitionTest {
    public static void main(String[] args) throws Exception {
        verifyOffhandAttributes();
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
        check(calls("club/someoneice/humangunner/NpcDualWield", "tickDefaultLayout", "hasMainHandMeleeWeapon"),
                "daily offhand layout requires current melee before restoring a remembered pair");
        check(calls("club/someoneice/humangunner/NpcDualWield", "hasMainHandMeleeWeapon", "isDedicatedMeleeWeapon")
                && !calls("club/someoneice/humangunner/NpcDualWield", "hasMainHandMeleeWeapon", "isNonGunWeapon"),
                "gun, bow and trident cannot authorize a melee offhand");
        check(!calls("club/someoneice/humangunner/NpcDualWield", "pair", "core")
                && !calls("club/someoneice/humangunner/NpcDualWield", "candidate", "core"),
                "non-core axes and maces are not rejected by an extra core requirement");
        check(calls("club/someoneice/humangunner/NpcDualWield", "active", "locate"),
                "dual speed/damage require a uniquely bound pair in the actual hands");
        check(calls("club/someoneice/humangunner/NpcDualWield", "candidate", "supportsOneHand"),
                "actual BC single-hand capability remains mandatory");
        check(callsIncludingLambdas("com/craftix/hostile_humans/entity/entities/Human", "finishBetterCombatUpswing",
                "club/someoneice/humangunner/NpcMeleeWeaponContext", "strike"),
                "real delayed impact uses scoped attacking weapon rather than physical hand swaps");
        System.out.println("FactionCombatTransitionTest passed");
    }
    private static void verifyOffhandAttributes() {
        var attribute = new net.minecraft.world.entity.ai.attributes.RangedAttribute("test.dual.damage", 10, 0, 1000);
        var attributes = new net.minecraft.world.entity.ai.attributes.AttributeMap(
                net.minecraft.world.entity.ai.attributes.AttributeSupplier.builder().add(attribute, 10).build());
        var instance = attributes.getInstance(attribute);
        UUID shared = UUID.randomUUID();
        var main = new net.minecraft.world.entity.ai.attributes.AttributeModifier(shared, "main", 4,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        var off = new net.minecraft.world.entity.ai.attributes.AttributeModifier(shared, "off", 8,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        var unrelated = new net.minecraft.world.entity.ai.attributes.AttributeModifier(UUID.randomUUID(), "buff", 2,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        instance.addPermanentModifier(main); instance.addTransientModifier(unrelated);
        var mainModifiers = com.google.common.collect.HashMultimap
                .<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
        var offModifiers = com.google.common.collect.HashMultimap
                .<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>create();
        mainModifiers.put(attribute, main); offModifiers.put(attribute, off);
        check(instance.getValue() == 16, "normal main weapon plus unrelated buff");
        double value = NpcMeleeWeaponContext.withAttributeModifiers(attributes, mainModifiers, offModifiers,
                () -> instance.getValue());
        check(value == 20 && instance.getValue() == 16, "offhand replaces rather than adds main damage and restores");
        boolean failed = false;
        try {
            NpcMeleeWeaponContext.withAttributeModifiers(attributes, mainModifiers, offModifiers, () -> {
                check(instance.getValue() == 20, "exception callback receives offhand attributes");
                throw new IllegalStateException("expected native callback failure");
            });
        } catch (IllegalStateException expected) { failed = true; }
        check(failed && instance.getValue() == 16 && instance.getModifier(shared) == main,
                "callback failure restores exact original modifier");
        check(instance.removePermanentModifier(shared), "permanent status survives the scope");
        instance.addPermanentModifier(main);
        var foreign = new net.minecraft.world.entity.ai.attributes.AttributeModifier(shared, "foreign", 12,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
        NpcMeleeWeaponContext.withAttributeModifiers(attributes, mainModifiers, offModifiers, () -> {
            instance.removeModifier(shared); instance.addTransientModifier(foreign); return true;
        });
        check(instance.getModifier(shared) == foreign && instance.getModifier(unrelated.getId()) == unrelated,
                "foreign callback changes and unrelated modifiers are not overwritten");
        var offProfile = new BetterCombatMeleeCombat.Profile(3, BetterCombatMeleeCombat.Shape.FORWARD_BOX,
                1, 0, 20, "slash", .5F, true);
        check(offProfile.attackSlot() == net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                "committed profile pins physical offhand for geometry, damage and wear");
    }

    /** A scoped strike may live in a javac-generated lambda; follow its actual method handle. */
    private static boolean callsIncludingLambdas(String owner, String entry, String calledOwner,
                                                String calledName) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = FactionCombatTransitionTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class: " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        var pending = new java.util.ArrayDeque<String>();
        var visited = new java.util.HashSet<String>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            String name = pending.removeFirst();
            if (!visited.add(name)) continue;
            for (var method : node.methods) if (method.name.equals(name))
                for (var instruction : method.instructions) {
                    if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals(calledOwner) && call.name.equals(calledName)) return true;
                    if (instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode dynamic)
                        for (Object argument : dynamic.bsmArgs)
                            if (argument instanceof org.objectweb.asm.Handle handle && handle.getOwner().equals(owner)
                                    && handle.getName().startsWith("lambda$")) pending.add(handle.getName());
                }
        }
        return false;
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
