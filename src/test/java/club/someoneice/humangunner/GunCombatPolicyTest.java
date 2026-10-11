package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Draw continuity and melee/retarget entrypoints, without optional gameplay dependencies. */
public final class GunCombatPolicyTest {
    private static final String HUMAN = "com/craftix/hostile_humans/entity/entities/Human";
    private static final String GOALS = "com/craftix/hostile_humans/entity/ai/goal/";
    private static final String MOD = "club/someoneice/humangunner/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        GunDrawContinuity state = new GunDrawContinuity();
        Object pistol = new Object(), rifle = new Object();
        int[] draws = {0};
        java.util.function.BooleanSupplier draw = () -> { draws[0]++; return true; };
        check(state.ensure(pistol, "pack:pistol", false, draw) && draws[0] == 1, "first unbound gun draws");
        for (int target = 0; target < 30; target++)
            check(!state.ensure(pistol, "pack:pistol", true, draw) && draws[0] == 1,
                    "same gun survives target/goal restart " + target);
        check(state.ensure(rifle, "pack:rifle", true, draw) && draws[0] == 2,
                "dynamic supplier cannot conceal a real weapon switch");
        check(state.ensure(rifle, "pack:modified_rifle", true, draw) && draws[0] == 3,
                "same stack with changed gun ID draws again");
        check(state.ensure(rifle, "pack:modified_rifle", false, draw) && draws[0] == 4,
                "lost operator binding is recovered");
        state.drawn(pistol, "pack:pistol");
        check(!state.ensure(pistol, "pack:pistol", true, draw) && draws[0] == 4,
                "forced recovery is adopted without another draw");
        GunDrawContinuity external = new GunDrawContinuity();
        check(!external.ensure(pistol, "pack:pistol", true, draw) && draws[0] == 4,
                "already-bound idle gun is adopted");
        check(external.ensure(rifle, "pack:rifle", true, () -> false), "cancelled switch is attempted");
        check(external.ensure(rifle, "pack:rifle", true, draw) && draws[0] == 5,
                "cancelled switch is retried rather than marked drawn");

        check(calls(HUMAN, "humanGunner$canMeleeAttack", "isGunItem"), "physical identity guards melee");
        for (String entry : new String[]{"humanGunner$isMeleeMode", "doHurtTarget", "hasPendingBetterCombatFacing",
                "finishBetterCombatUpswing"})
            check(calls(HUMAN, entry, "humanGunner$canMeleeAttack"), "gun guard at " + entry);
        MethodNode damage = node(HUMAN).methods.stream().filter(method -> method.name.equals("doHurtTargetSingle")
                && method.desc.endsWith(";ZZ)Z")).findFirst().orElseThrow();
        check(index(damage, "humanGunner$canMeleeAttack") < index(damage, "doHurtTarget"),
                "guard precedes vanilla damage and swing");
        for (String entry : new String[]{"canUse", "canContinueToUse", "tick", "humanGunner$tryImmediateCounterattack"})
            check(calls(GOALS + "MeleeAttackGoal", entry, "isGunItem"), "native goal guard " + entry);
        check(calls(MOD + "BetterCombatMeleeCombat", "isEnabled", "humanGunner$canMeleeAttack"), "BC disabled for held guns");
        check(calls(MOD + "GunnerGoal", "start", "ensureDrawn")
                && !calls(MOD + "GunnerGoal", "start", "draw"), "goal restart avoids unconditional draw");
        for (String targetGoal : new String[]{"NearestAttackableTargetGoalCustom", "NearestAttackableTargetGoalWithHumanLimiter"}) {
            check(calls(GOALS + targetGoal, "canContinueToUse", "isAlive"), "dead target rejected " + targetGoal);
            String searchEntry = targetGoal.equals("NearestAttackableTargetGoalWithHumanLimiter") ? "findCandidate" : "canUse";
            check(calls(GOALS + targetGoal, searchEntry, "humanGunner$shouldFastGunRetarget"), "bounded rapid reacquisition " + targetGoal);
        }
        String limitedGoal = GOALS + "NearestAttackableTargetGoalWithHumanLimiter";
        check(calls(limitedGoal, "canUse", "findCandidate") && calls(limitedGoal, "canUse", "canSelectPlayer"),
                "limiter entry preserves search and player authorization");
        check(calls(limitedGoal, "findCandidate", "canUse") && calls(limitedGoal, "findCandidate", "findTarget"),
                "ordinary search interval and fast search both remain available");
        check(calls(limitedGoal, "canSelectPlayer", "isLookingAtTarget")
                && calls(limitedGoal, "canSelectPlayer", "isPlayerTargetLimitReached"),
                "player alert and population checks remain connected");
        check(calls(limitedGoal, "isPlayerTargetLimitReached", "getEntities")
                && calls(limitedGoal, "canSelectPlayer", "setTarget"),
                "bounded population query still rejects excess attackers");
        for (String entry : new String[]{"canUse", "tick"}) {
            int aliveChecks = 0;
            for (var instruction : method(MOD + "GuardCombatTargetGoal", entry).instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals("isAlive")) aliveChecks++;
            check(aliveChecks >= 2, "guard rejects dead remembered attacker " + entry);
        }
        check(calls(MOD + "GuardCombatTargetGoal", "stop", "setTarget"), "dead guard target cleared for reacquisition");
        check(calls(HUMAN, "setTarget", "canControlledHumanAttack"), "target assignment rejects controlled allies");
        check(calls(MOD + "HumanGunner", "onLivingTick", "canControlledHumanAttack"),
                "ownership changes invalidate already assigned friendly targets");
        for (String entry : new String[]{"canContinueToUse", "tick"})
            check(calls(MOD + "AdaptiveCombatGoal", entry, "isValidThreat"), "cached tactical threat revalidated: " + entry);
        check(calls(MOD + "GunnerGoal", "tick", "canAttack"), "live gun tick rechecks attack permission");
        check(index(method(MOD + "TaczGunMechanics", "handleShootResult"), "isUsingItem")
                < index(method(MOD + "TaczGunMechanics", "handleShootResult"), "reload"),
                "ordinary item-use wait is checked before reload attempts");
        check(!calls(MOD + "TaczHumanReloadTransactions", "isPaidReloading", "end"),
                "reload-state queries never cancel native reload state");
        String mixin = MOD + "mixin/TaczHumanReloadMixin";
        MethodNode publish = method(mixin, "humanGunner$paidStart");
        check(index(publish, "start") < index(publish, "sendToTrackingEntity"),
                "native NPC reload notification follows successful authorization");
        boolean successGuard = false;
        for (var instruction : publish.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("sendToTrackingEntity")) break;
            if (instruction.getOpcode() == org.objectweb.asm.Opcodes.IFEQ) successGuard = true;
        }
        check(successGuard, "failed NPC start cannot publish a reload notification");
        check(calls(mixin, "humanGunner$discardFinishedPayment", "isUsingItem")
                && calls(mixin, "humanGunner$discardFinishedPayment", "canReload"),
                "preflight rejects item use and insufficient reserves before native notifications");
        check(calls(mixin, "humanGunner$deferReloadNotification", "sendToTrackingEntity"),
                "non-Human native notification forwarding remains present");
        System.out.println("GunCombatPolicyTest: " + checks + " checks passed");
    }

    private static ClassNode node(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (var stream = GunCombatPolicyTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + name);
            new ClassReader(stream).accept(result, 0);
        }
        return result;
    }
    private static MethodNode method(String owner, String name) throws Exception {
        return node(owner).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }
    private static int index(MethodNode method, String name) {
        int i = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return i;
            i++;
        }
        return Integer.MAX_VALUE;
    }
    private static boolean calls(String owner, String entry, String name) throws Exception {
        return index(method(owner, entry), name) != Integer.MAX_VALUE;
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
