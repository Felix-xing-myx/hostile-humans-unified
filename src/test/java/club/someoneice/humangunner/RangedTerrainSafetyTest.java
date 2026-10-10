package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Health budgets, terrain failure hysteresis and every production movement/weapon entrypoint. */
public final class RangedTerrainSafetyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        RangedMeleeCycle cycle = new RangedMeleeCycle();
        for (int tick = 0; tick < 79; tick++) cycle.tick(true, true);
        check(!cycle.searching(), "fight before voluntarily separating");
        cycle.interrupt();
        cycle.tick(true, true);
        check(cycle.searching(), "shield interruption preserves accumulated fight time");
        for (int tick = 0; tick < 39; tick++) cycle.tick(true, true);
        check(cycle.searching(), "budgeted search retains its session while waiting");
        cycle.tick(true, true);
        check(!cycle.searching() && !cycle.separating(), "search timeout returns to melee");
        for (int tick = 0; tick < 80; tick++) cycle.tick(true, true);
        check(cycle.searching(), "failed separation can be retried after more fighting");
        cycle.beginSeparation();
        for (int tick = 0; tick < 19; tick++) cycle.tick(true, false);
        check(cycle.separating(), "brief positional pause does not replace route");
        cycle.tick(true, false);
        check(!cycle.separating(), "sustained stalled separation resumes melee");
        cycle.beginSeparation();
        for (int tick = 0; tick < 79; tick++) cycle.tick(true, true);
        check(cycle.separating(), "progressing separation keeps its assigned route");
        cycle.tick(true, true);
        check(!cycle.separating(), "separation has a bounded duration even if pursued");
        cycle.beginSeparation();
        cycle.tick(false, true);
        check(!cycle.separating(), "commands and survival preempt voluntary separation");
        cycle.reset();
        for (int tick = 0; tick < 100; tick++) cycle.tick(false, true);
        check(!cycle.searching(), "held positions or committed attacks cannot initiate separation");
        cycle.tick(true, true);
        check(cycle.searching(), "completed fight timer waits for a safe attack boundary");
        check(HumanFallPolicy.damageBudget(1000) == 6, "high-health units never gain unlimited descent");
        check(HumanFallPolicy.damageBudget(20) == 4, "twenty health risks at most twenty percent");
        check(HumanFallPolicy.damageBudget(5) == 1, "reserve four health when low");
        check(HumanFallPolicy.damageBudget(2) == 0, "critical health accepts only zero-damage drops");
        check(HumanFallPolicy.damageBudget(Double.NaN) == 0, "invalid health never creates a budget");
        check(HumanFallPolicy.accepts(20, 7, 4), "exact health-budget boundary");
        check(!HumanFallPolicy.accepts(20, 8, 5), "damage above budget rejected");
        check(!HumanFallPolicy.accepts(1000, 10, 7), "absolute damage cap survives health mods");
        check(!HumanFallPolicy.accepts(1000, 13, 0), "absolute geometric cap survives fall immunity");
        check(!HumanFallPolicy.accepts(20, Double.NaN, 0), "invalid drop rejected");
        check(!HumanFallPolicy.accepts(20, 3, Double.POSITIVE_INFINITY), "invalid damage rejected");
        for (int health = 1; health <= 200; health++) {
            check(HumanFallPolicy.accepts(health, 3, 0), "ordinary safe step never needs remaining health");
            check(!HumanFallPolicy.accepts(health, 12, health), "lethal planned fall never allowed");
        }
        check(!RangedTerrainPolicy.switchAfterFailures(39, 2), "short occlusion does not switch weapon");
        check(!RangedTerrainPolicy.switchAfterFailures(100, 1), "one failed lane does not abandon ranged mode");
        check(RangedTerrainPolicy.switchAfterFailures(40, 2), "repeated confirmed lane failure permits melee");
        check(!RangedTerrainPolicy.shouldReportBlockedSteps(19) && RangedTerrainPolicy.shouldReportBlockedSteps(20), "cramped orbit reports only sustained blockage");
        check(RangedTerrainPolicy.keepMelee(79, 100), "finish attack cycles before returning to ranged");
        check(RangedTerrainPolicy.keepMelee(80, 19), "briefly open lane does not oscillate weapon");
        check(!RangedTerrainPolicy.keepMelee(100, 20), "sustained clear open terrain restores ranged");
        check(!RangedTerrainPolicy.keepMelee(400, 0), "unreachable chase periodically retries ranged");
        String root = "club/someoneice/humangunner/";
        String human = "com/craftix/hostile_humans/entity/entities/Human";
        check(calls(human, "travel", root + "HumanTerrainSafety", "guardTravel"), "all locomotion inputs use final safety guard");
        check(calls(human, "getMaxFallDistance", root + "HumanFallPolicy", "accepts"), "A-star descent uses live health budget");
        check(calls(human, "expectedRawFallDamage", null, "calculateFallDamage"), "fall assessment never causes real damage");
        check(calls("com/craftix/hostile_humans/entity/ai/HumanNavigation", "moveTo", root + "HumanTerrainSafety", "assessPath"), "installed routes return terrain reasons");
        check(calls(root + "HumanSafeJumpControl", "jump", root + "HumanTerrainSafety", "safeCombatJump"), "voluntary jumps check landing including extra height");
        check(calls(root + "RetreatPathSafety", "isSafe", root + "HumanTerrainSafety", "safePath"), "retreat shares height safety");
        for (String goal : new String[]{"BowAttack", "CrossbowGoal"}) {
            String type = "com/craftix/hostile_humans/entity/ai/goal/" + goal;
            check(calls(type, "strafe", root + "RangedTerrainCombat", "strafe"), goal + " cannot orbit blindly");
            check(calls(type, "tick", root + "RangedTerrainCombat", "searchCompleted"), goal + " distinguishes failed lane from budget waiting");
            check(calls(type, "tickShot", root + "RangedWeaponCustody", "isActive"), goal + " never shoots after melee hand switch");
        }
        check(calls(root + "RangedWeaponCustody", "shouldReturnToRanged", root + "RangedTerrainCombat", "holdsMelee"), "terrain melee is a real sustained custody lease");
        check(calls(human, "humanGunner$isMeleeMode", root + "RangedTerrainCombat", "isTerrainMelee"), "terrain melee enables native obstacle pursuit");
        check(calls(root + "RangedHybridMeleeGoal", "canUse", root + "RangedTerrainCombat", "isTerrainMelee"), "terrain leases retain the native melee goal");
        check(calls(root + "RangedHybridMeleeGoal", "tick", "com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal", "tick"), "temporary fighters reuse ordinary melee movement and attacks");
        check(calls(root + "RangedHybridMeleeGoal", "tick", root + "RangedFiringPosition", "findVisiblePath"), "voluntary separation shares the budgeted firing-lane planner");
        System.out.println("RangedTerrainSafetyTest: " + checks + " checks passed; voxel terrain gameplay remains to be tested");
    }
    private static boolean calls(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = RangedTerrainSafetyTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (input == null) throw new AssertionError("Missing " + owner);
            new ClassReader(input).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method))
            for (var instruction : entry.instructions)
                if (instruction instanceof MethodInsnNode call && (target == null || call.owner.equals(target)) && call.name.equals(name)) return true;
        return false;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
