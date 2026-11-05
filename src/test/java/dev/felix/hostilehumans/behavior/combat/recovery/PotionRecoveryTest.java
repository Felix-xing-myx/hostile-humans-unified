package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.testutil.ModuleTestSources;

import dev.felix.hostilehumans.behavior.combat.recovery.CombatRecoveryGoal;
import dev.felix.hostilehumans.behavior.combat.recovery.IdleRecoveryGoal;
import dev.felix.hostilehumans.behavior.combat.recovery.PotionRecoveryPolicy;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Regression cases for non-player consumption, expiry and independent regeneration cadence. */
public final class PotionRecoveryTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(PotionRecoveryPolicy.needsPotionCharge(true, 1, 1), "vanilla NPC potion needs one charge");
        check(PotionRecoveryPolicy.needsPotionCharge(true, 4, 4), "modded stacked potion needs one charge");
        check(!PotionRecoveryPolicy.needsPotionCharge(true, 4, 3), "already consumed stack is not charged twice");
        check(!PotionRecoveryPolicy.needsPotionCharge(false, 1, 1), "custom finish result is preserved");
        check(!PotionRecoveryPolicy.needsPotionCharge(false, 1, 0), "returned empty bottle is preserved");
        check(!PotionRecoveryPolicy.needsPotionCharge(true, 0, 0), "empty use is not charged");
        check(PotionRecoveryPolicy.regenerationActive(1), "last live buff tick still blocks redrinking");
        check(PotionRecoveryPolicy.regenerationActive(-1), "infinite buff still blocks redrinking");
        check(!PotionRecoveryPolicy.regenerationActive(0), "expired buff permits another potion");
        check(PotionRecoveryPolicy.regenerationStep(0) == 0.5F, "level one total half-HP per independent ten-tick pulse");
        check(PotionRecoveryPolicy.regenerationStep(1) == 1.0F, "level two total one HP per independent ten-tick pulse");
        check(PotionRecoveryPolicy.urgentInstantHealing(0.499999), "strictly below fifty percent needs instant healing");
        check(!PotionRecoveryPolicy.urgentInstantHealing(0.50), "exactly fifty percent does not request instant healing");
        check(!PotionRecoveryPolicy.urgentInstantHealing(0.51), "ordinary recovery above threshold is preserved");
        check(PotionRecoveryPolicy.instantHealingUseDuration(32) == 20, "vanilla instant potion drinks in one second");
        check(PotionRecoveryPolicy.instantHealingUseDuration(20) == 20, "one-second custom potion is unchanged");
        check(PotionRecoveryPolicy.instantHealingUseDuration(8) == 8, "already faster custom potion is not slowed");
        check(PotionRecoveryPolicy.instantHealingUseDuration(0) == 1, "malformed zero duration remains safe");
        var clock = new PotionRecoveryPolicy.Clock(100, 10);
        check(!clock.pulse(109, 10) && clock.pulse(110, 10), "first regeneration waits its independent interval");
        check(!clock.pulse(110, 10), "duplicate tick cannot heal twice");
        check(!clock.pulse(119, 10) && clock.pulse(120, 10), "independent regular cadence");
        check(clock.pulse(1000, 10) && !clock.pulse(1000, 10), "unloaded time never catches up with a burst");
        var restarted = new PotionRecoveryPolicy.Clock(1001, 10);
        check(!restarted.pulse(1010, 10) && restarted.pulse(1011, 10), "new buff gets a fresh clock");
        check(PotionRecoveryPolicy.REGENERATION_INTERVAL_TICKS == 10, "regeneration is independent of food interval");
        check(PotionRecoveryPolicy.instantHealingAmount(0, 100, 1, 1) == 12, "healing I adds six percent");
        check(PotionRecoveryPolicy.instantHealingAmount(1, 100, 1, 1) == 24, "healing II adds twelve percent");
        check(PotionRecoveryPolicy.instantHealingAmount(1, 100, .5, 1) == 12, "splash scales both portions");
        check(PotionRecoveryPolicy.instantHealingAmount(0, 100, 2, 1) == 12, "exposure caps at full strength");
        check(Math.abs(PotionRecoveryPolicy.instantHealingAmount(0, 20, 1, 1) - 7.2F) < .0001F,
                "the bonus preserves fractional health");
        check(PotionRecoveryPolicy.instantHealingAmount(1, 100, 1, 1.5) == 30,
                "regeneration scales the twelve-point base but not the twelve-point bonus");
        check(PotionRecoveryPolicy.instantHealingAmount(0, 100, -1, 1) == 0
                && PotionRecoveryPolicy.instantHealingAmount(1, 100, Double.NaN, 1) == 0, "invalid exposures cannot heal");
        check(PotionRecoveryPolicy.instantHealingAmount(1, 400, 1, 3) == 84,
                "boss-sized health adds exactly forty-eight bonus healing at high regeneration");
        for(int amplifier=0;amplifier<=1;amplifier++){
            var cadence = new PotionRecoveryPolicy.Clock(0, PotionRecoveryPolicy.REGENERATION_INTERVAL_TICKS);
            float total=0;
            for(int tick=1;tick<=200;tick++)if(cadence.pulse(tick,PotionRecoveryPolicy.REGENERATION_INTERVAL_TICKS))
                total+=PotionRecoveryPolicy.regenerationStep(amplifier);
            check(total==(amplifier==0?10:20), "ten loaded seconds have exact total regeneration budget");
        }
        String recovery = "dev/felix/hostilehumans/behavior/combat/recovery/RecoverySupplies";
        check(callIndex(recovery, "hasUrgentInstantHealing", recovery, "healthRatio") >= 0
                && callIndex(recovery, "hasUrgentInstantHealing", recovery, "projectedHealth") < 0,
                "queued food healing cannot mask actual critical health");
        check(callIndex(recovery, "beginInventoryUse", "dev/felix/hostilehumans/inventory/HumanEquipmentExchange", "request") >= 0,
                "recovery starts through the real exchange layer");
        for (String goal : new String[] {"CombatRecoveryGoal", "IdleRecoveryGoal"})
            check(callIndex("dev/felix/hostilehumans/" + goal, "canUse", recovery, "hasRecoveryChoice") >= 0,
                    "recovery goal shares ordered selector: " + goal);
        String human = "dev/felix/hostilehumans/entity/Human";
        int nativeFinish = callIndex(human, "completeUsingItem", "dev/felix/hostilehumans/entity/HumanEntity", "completeUsingItem");
        check(nativeFinish >= 0 && callIndex(human, "completeUsingItem", recovery, "onUseCompleted") > nativeFinish
                && callIndex(recovery, "onUseCompleted", recovery, "consumeCompletedPotion") >= 0,
                "charge follows native completion and effect application");
        check(callIndex(recovery, "tickRegenerationHealing", recovery, "queueFoodHealing") < 0
                && callIndex(recovery, "tickRegenerationHealing", null, "getPersistentData") < 0,
                "regeneration neither queues nor spends food healing");
        String hook = "dev/felix/hostilehumans/bootstrap/HumanRuntime";
        int bonus = callIndex(hook, "onLivingTick", recovery, "tickEffects");
        check(bonus >= 0 && bonus < callIndex(hook, "onLivingTick", null, "isEditing"),
                "buff continues while owner edits inventory");
        for (String effect : new String[] {"tickRegenerationHealing", "tickGradualHealing"})
            check(callIndex(recovery, "tickEffects", recovery, effect) >= 0, "all healing uses one clock entry: " + effect);
        RecoverySelectionTest.main(args);
        System.out.println("PotionRecoveryTest: " + checks + " checks passed");
    }

    private static int callIndex(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = PotionRecoveryTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(owner) + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method)) {
            int index = 0;
            for (var instruction : entry.instructions) {
                if (instruction instanceof MethodInsnNode call && (target == null || call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(target))) && call.name.equals(name)) return index;
                index++;
            }
        }
        return -1;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
