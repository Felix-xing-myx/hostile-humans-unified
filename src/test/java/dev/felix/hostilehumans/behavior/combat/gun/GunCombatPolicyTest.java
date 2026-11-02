package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.testutil.ModuleTestSources;

import dev.felix.hostilehumans.behavior.combat.AdaptiveCombatGoal;
import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.behavior.combat.defense.GunMeleeProtection;
import dev.felix.hostilehumans.behavior.combat.melee.BetterCombatMeleeCombat;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeActionControl;
import dev.felix.hostilehumans.behavior.combat.melee.TridentHybridGoal;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.tacz.TaczGunItemIdentity;
import dev.felix.hostilehumans.compat.tacz.TaczGunMechanics;
import dev.felix.hostilehumans.compat.tacz.TaczHumanReloadTransactions;
import dev.felix.hostilehumans.compat.tacz.TaczIntegration;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.PerceptionTargetGoal;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Draw continuity and melee/retarget entrypoints, without optional gameplay dependencies. */
public final class GunCombatPolicyTest {
    private static final String HUMAN = "dev/felix/hostilehumans/entity/Human";
    private static final String GOALS = "dev/felix/hostilehumans/entity/ai/goal/";
    private static final String MOD = "dev/felix/hostilehumans/";
    private static int checks;

    public static void main(String[] args) throws Exception {
        GunDoctrineTest.main(args);
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

        check(calls(HUMAN, "hhu$canMeleeAttack", "isGunItem"), "physical identity guards melee");
        check(calls("dev/felix/hostilehumans/compat/tacz/TaczIntegration", "onGunShoot", "allows")
                        && calls("dev/felix/hostilehumans/compat/tacz/TaczIntegration", "onGunShoot", "setCanceled"),
                "native public shoot event can reject unpermitted NPC fire");
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunFireAuthorization", "allows", "isActivelyCollectingLoot")
                        && calls("dev/felix/hostilehumans/behavior/combat/gun/GunFireAuthorization", "allows", "getEntity")
                        && calls("dev/felix/hostilehumans/behavior/combat/gun/GunFireAuthorization", "allows", "attack"),
                "final authorization rejects loot, unloaded targets and obsolete bodyguard selection");
        for (String type : new String[]{"shotgun", "pistol", "smg", "rifle", "mg", "sniper"}) {
            var band = GunRangePolicy.forType(type);
            check(GunRangePolicy.withinPreferredRange(band.minimum(), band)
                            && GunRangePolicy.withinPreferredRange(band.maximum(), band),
                    "preferred distance boundaries accepted: " + type);
            check(!GunRangePolicy.withinPreferredRange(band.maximum() + 1, band)
                            && !GunRangePolicy.withinPreferredRange(band.minimum() - .1, band),
                    "firing reach does not imply preferred station: " + type);
        }
        for (var entry : java.util.Map.of("pistol",36d,"smg",48d,"rifle",80d,"shotgun",24d,
                "mg",56d,"sniper",96d,"launcher",56d).entrySet()) {
            var base = GunRangePolicy.forType(entry.getKey());
            check(base.fireRange() == entry.getValue(), "current maximum range: " + entry.getKey());
            var limited = GunRangePolicy.nativeBand(entry.getKey(), 5);
            check(limited.fireRange() == 5 && limited.maximum() <= 5,
                    "extended range cannot exceed native projectile lifetime");
        }
        check(!GunRangePolicy.withinPreferredRange(Double.NaN, GunRangePolicy.forType("rifle")),
                "invalid distance cannot grant stationary fire");
        check(!calls("dev/felix/hostilehumans/behavior/combat/gun/GunnerGoal", "canUse", "firingRangeSqr"),
                "gunner pursuit remains active beyond firing reach");
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "moveForFiringPosition", "directFiringMovement"),
                "clear effective firing range controls navigation handoff, not preferred spacing");
        for(double distance:new double[]{4,15,16,24,25,40,64})
            check(GunRangePolicy.directFiringMovement(false,distance*distance,64*64),"clear in-range spacing remains direct: "+distance);
        check(!GunRangePolicy.directFiringMovement(true,400,4096)
                &&!GunRangePolicy.directFiringMovement(false,65*65,4096)
                &&!GunRangePolicy.directFiringMovement(false,Double.NaN,4096),
                "occlusion, excessive range or invalid distance cannot claim direct firing movement");
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "strafeAround", "clearDirectStrafe")
                        &&calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "strafeAround", "movement")
                        &&calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "strafeAround", "cancelStep"),
                "direct firing handoff removes stale input and declares combat facing before aim gate");
        check(!calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "strafeAround", "approachTarget"),
                "a rejected safe lateral step cannot manufacture a chase command");
        check(calls("dev/felix/hostilehumans/behavior/combat/HumanCombatDirector", "keepsRangedDistance", "usableGun")
                && calls("dev/felix/hostilehumans/behavior/combat/HumanCombatDirector", "keepsRangedDistance", "hasArrows"),
                "range intent uses actual ammunition, including reloadable or in-progress guns");
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunCustody", "beginCloseMeleeCounter", "keepsRangedDistance")
                && calls("dev/felix/hostilehumans/behavior/combat/gun/GunCustody", "restorePrimaryGun", "keepsRangedDistance")
                && calls("dev/felix/hostilehumans/behavior/combat/gun/GunCustody", "tick", "keepsRangedDistance"),
                "gun entry and both old-lease reload paths honor distance intent");
        check(calls("dev/felix/hostilehumans/behavior/combat/ranged/RangedWeaponCustody", "beginCloseMelee", "keepsRangedDistance")
                && calls("dev/felix/hostilehumans/behavior/combat/ranged/RangedWeaponCustody", "restorePreferred", "keepsRangedDistance")
                && calls("dev/felix/hostilehumans/behavior/combat/ranged/RangedWeaponCustody", "shouldReturnToRanged", "keepsRangedDistance")
                && calls("dev/felix/hostilehumans/behavior/combat/melee/TridentHybridGoal", "tick", "keepsRangedDistance"),
                "bows, old bow leases and hybrid tridents share the faction distance intent");
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "clearDirectStrafe", "strafe"),
                "stopping navigation also replaces stale MoveControl MOVE_TO when local movement is unsafe");
        MethodNode urgentShot = method("dev/felix/hostilehumans/compat/tacz/TaczGunMechanics", "prepareUrgentCloseShot");
        int prepWrites = 0;
        for (var instruction : urgentShot.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field
                    && field.name.equals("meleePrepTickCount")) {
                var value = instruction.getPrevious();
                while (value != null && value.getOpcode() < 0) value = value.getPrevious();
                check(value != null && value.getOpcode() == org.objectweb.asm.Opcodes.ICONST_M1,
                        "urgent shot cancels native melee rather than queueing a zero-tick hit");
                prepWrites++;
            }
        }
        check(prepWrites == 1, "native melee sentinel checked");
        check(calls("dev/felix/hostilehumans/behavior/combat/defense/GunMeleeProtection", "blocks", "hhu$canMeleeAttack"),
                "final damage guard uses the same held-gun gate as melee scheduling");
        check(calls("dev/felix/hostilehumans/compat/tacz/TaczGunItemIdentity", "onGunMelee", "setCanceled"),
                "public TaCZ melee event cancels NPC gun-butt startup");
        for (String entry : new String[]{"doHurtTarget", "hasPendingBetterCombatFacing"})
            check(calls(HUMAN, entry, "hhu$canMeleeAttack"), "gun guard at " + entry);
        check(calls(HUMAN, "hhu$isMeleeMode", "hhu$canMeleeMove")
                && !calls(HUMAN, "hhu$isMeleeMode", "hhu$canMeleeAttack"),
                "shield can deny the strike without disabling the melee movement goal");
        check(calls(HUMAN, "finishBetterCombatUpswing", "valid")
                && calls("dev/felix/hostilehumans/behavior/combat/melee/MeleeActionControl", "valid", "available")
                && calls("dev/felix/hostilehumans/behavior/combat/melee/MeleeActionControl", "available", "hhu$canMeleeAttack"),
                "delayed strike reaches the same gun/retreat/permission guard through its original action token");
        MethodNode damage = node(HUMAN).methods.stream().filter(method -> method.name.equals("doHurtTargetSingle")
                && method.desc.endsWith(";ZZ)Z")).findFirst().orElseThrow();
        check(index(damage, "hhu$canMeleeAttack") < index(damage, "doHurtTarget"),
                "guard precedes vanilla damage and swing");
        for (String entry : new String[]{"canUse", "canContinueToUse", "tick", "hhu$tryImmediateCounterattack"})
            check(calls("dev/felix/hostilehumans/behavior/combat/melee/MeleeAttackGoal", entry, "isGunItem"), "native goal guard " + entry);
        check(calls("dev/felix/hostilehumans/behavior/combat/melee/BetterCombatMeleeCombat", "isEnabled", "hhu$canMeleeAttack"), "BC disabled for held guns");
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunnerGoal", "start", "ensureDrawn")
                && !calls("dev/felix/hostilehumans/behavior/combat/gun/GunnerGoal", "start", "draw"), "goal restart avoids unconditional draw");
        for (String targetGoal : new String[]{"NearestAttackableTargetGoalCustom", "NearestAttackableTargetGoalWithHumanLimiter"}) {
            check(calls(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(GOALS + targetGoal), "canContinueToUse", "isAlive"), "dead target rejected " + targetGoal);
            String searchEntry = targetGoal.equals("NearestAttackableTargetGoalWithHumanLimiter") ? "findCandidate" : "canUse";
            check(calls(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(GOALS + targetGoal), searchEntry, "hhu$shouldFastGunRetarget"), "bounded rapid reacquisition " + targetGoal);
        }
        String limitedGoal = "dev/felix/hostilehumans/perception/NearestAttackableTargetGoalWithHumanLimiter";
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
        check(calls("dev/felix/hostilehumans/perception/PerceptionTargetGoal", "tick", "select"), "ordinary target goal delegates common selection");
        check(calls("dev/felix/hostilehumans/perception/HumanPerception", "canTarget", "valid"), "reacquisition revalidates current permission");
        check(calls("dev/felix/hostilehumans/perception/HumanPerception", "valid", "observable"), "invalid/dead records cannot be selected");
        check(calls(HUMAN, "setTarget", "canControlledHumanAttack"), "target assignment rejects controlled allies");
        check(calls(MOD + "HumanRuntime", "onLivingTick", "canControlledHumanAttack"),
                "ownership changes invalidate already assigned friendly targets");
        for (String entry : new String[]{"canContinueToUse", "tick"})
            check(calls("dev/felix/hostilehumans/behavior/combat/AdaptiveCombatGoal", entry, "isValidThreat"), "cached tactical threat revalidated: " + entry);
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunnerGoal", "tick", "canAttack"), "live gun tick rechecks attack permission");
        check(index(method("dev/felix/hostilehumans/compat/tacz/TaczGunMechanics", "handleShootResult"), "isUsingItem")
                < index(method("dev/felix/hostilehumans/compat/tacz/TaczGunMechanics", "handleShootResult"), "reload"),
                "ordinary item-use wait is checked before reload attempts");
        check(!calls("dev/felix/hostilehumans/compat/tacz/TaczHumanReloadTransactions", "isPaidReloading", "end"),
                "reload-state queries never cancel native reload state");
        String mixin = MOD + "mixin/TaczHumanReloadMixin";
        MethodNode publish = method(mixin, "hhu$paidStart");
        check(index(publish, "start") < index(publish, "sendToTrackingEntity"),
                "native NPC reload notification follows successful authorization");
        boolean successGuard = false;
        for (var instruction : publish.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("sendToTrackingEntity")) break;
            if (instruction.getOpcode() == org.objectweb.asm.Opcodes.IFEQ) successGuard = true;
        }
        check(successGuard, "failed NPC start cannot publish a reload notification");
        check(calls(mixin, "hhu$discardFinishedPayment", "isUsingItem")
                && calls(mixin, "hhu$discardFinishedPayment", "canReload"),
                "preflight rejects item use and insufficient reserves before native notifications");
        check(calls(mixin, "hhu$deferReloadNotification", "sendToTrackingEntity"),
                "non-Human native notification forwarding remains present");
        System.out.println("GunCombatPolicyTest: " + checks + " checks passed");
    }

    private static ClassNode node(String name) throws Exception {
        ClassNode result = new ClassNode();
        try (var stream = GunCombatPolicyTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(name) + ".class")) {
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
        return node(owner).methods.stream().filter(method -> method.name.equals(entry))
                .anyMatch(method -> index(method, name) != Integer.MAX_VALUE);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
