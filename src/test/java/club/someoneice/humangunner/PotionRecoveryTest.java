package club.someoneice.humangunner;

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
        check(PotionRecoveryPolicy.regenerationStep(0) == 0.5F, "level one provides half a food pulse");
        check(PotionRecoveryPolicy.regenerationStep(1) == 1.0F, "level two matches a food pulse");
        check(PotionRecoveryPolicy.urgentInstantHealing(0.299999), "strictly below thirty percent needs instant healing");
        check(!PotionRecoveryPolicy.urgentInstantHealing(0.30), "exactly thirty percent does not override food");
        check(!PotionRecoveryPolicy.urgentInstantHealing(0.31), "ordinary recovery above threshold is preserved");
        check(PotionRecoveryPolicy.instantHealingUseDuration(32) == 20, "vanilla instant potion drinks in one second");
        check(PotionRecoveryPolicy.instantHealingUseDuration(20) == 20, "one-second custom potion is unchanged");
        check(PotionRecoveryPolicy.instantHealingUseDuration(8) == 8, "already faster custom potion is not slowed");
        check(PotionRecoveryPolicy.instantHealingUseDuration(0) == 1, "malformed zero duration remains safe");
        var clock = new PotionRecoveryPolicy.Clock(100, 10);
        check(!clock.pulse(109, 10) && clock.pulse(110, 10), "first regeneration waits a food interval");
        check(!clock.pulse(110, 10), "duplicate tick cannot heal twice");
        check(!clock.pulse(119, 10) && clock.pulse(120, 10), "independent regular cadence");
        check(clock.pulse(1000, 10) && !clock.pulse(1000, 10), "unloaded time never catches up with a burst");
        var restarted = new PotionRecoveryPolicy.Clock(1001, 10);
        check(!restarted.pulse(1010, 10) && restarted.pulse(1011, 10), "new buff gets a fresh clock");
        String recovery = "club/someoneice/humangunner/RecoverySupplies";
        check(callIndex(recovery, "hasUrgentInstantHealing", recovery, "healthRatio") >= 0
                && callIndex(recovery, "hasUrgentInstantHealing", recovery, "projectedHealth") < 0,
                "queued food healing cannot mask actual critical health");
        check(callIndex(recovery, "findBestSlot", recovery, "findBestInstantHealingSlot") >= 0,
                "idle and retreat selectors prioritize instant healing");
        check(callIndex(recovery, "continueFoodUse", recovery, "hasUrgentInstantHealing") >= 0,
                "critical health stops food chaining");
        check(callIndex(recovery, "createSession", recovery, "recoveryUseDuration") >= 0
                && callIndex(recovery, "accelerateFoodUse", recovery, "recoveryUseDuration") >= 0,
                "session completion and native countdown use identical duration");
        check(callIndex(recovery, "recoveryUseDuration", "club/someoneice/humangunner/PotionRecoveryPolicy", "instantHealingUseDuration") >= 0,
                "only instant healing has one-second duration cap");
        check(callIndex("club/someoneice/humangunner/CombatFoodRecoveryGoal", "tick", recovery, "beginUrgentInstantHealing") >= 0,
                "urgent use shares existing food goal and custody controller");
        check(callIndex("club/someoneice/humangunner/IdleRecoveryGoal", "tick", recovery, "hasUrgentInstantHealing") >= 0,
                "idle food yields to emergency potion");
        for (String method : new String[] {"hasSupply", "beginInventoryUse", "findUsefulDrinkableBuffSlot", "healingPotionScore"}) {
            check(callIndex(recovery, method, recovery, "canUsePotionNow") >= 0, "regeneration gate: " + method);
        }
        String human = "com/craftix/hostile_humans/entity/entities/Human";
        int nativeFinish = callIndex(human, "completeUsingItem", "com/craftix/hostile_humans/entity/HumanEntity", "completeUsingItem");
        check(nativeFinish >= 0 && callIndex(human, "completeUsingItem", recovery, "onUseCompleted") > nativeFinish
                && callIndex(recovery, "onUseCompleted", recovery, "consumeCompletedPotion") >= 0,
                "charge follows native completion and effect application");
        check(callIndex(recovery, "tickRegenerationHealing", recovery, "queueFoodHealing") < 0
                && callIndex(recovery, "tickRegenerationHealing", null, "getPersistentData") < 0,
                "regeneration neither queues nor spends food healing");
        String hook = "club/someoneice/humangunner/HumanGunner";
        int bonus = callIndex(hook, "onLivingTick", recovery, "tickEffects");
        check(bonus >= 0 && bonus < callIndex(hook, "onLivingTick", null, "isEditing"),
                "buff continues while owner edits inventory");
        for (String effect : new String[] {"tickRegenerationHealing", "tickGradualHealing"})
            check(callIndex(recovery, "tickEffects", recovery, effect) >= 0, "all healing uses one clock entry: " + effect);
        System.out.println("PotionRecoveryTest: " + checks + " checks passed");
    }

    private static int callIndex(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = PotionRecoveryTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method)) {
            int index = 0;
            for (var instruction : entry.instructions) {
                if (instruction instanceof MethodInsnNode call && (target == null || call.owner.equals(target)) && call.name.equals(name)) return index;
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
