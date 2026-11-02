package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.behavior.combat.retreat.RetreatEscapeProgressTest;
import dev.felix.hostilehumans.testutil.ModuleTestSources;

import dev.felix.hostilehumans.behavior.combat.AdaptiveCombatGoal;
import dev.felix.hostilehumans.behavior.combat.gun.GunMovementExecution;
import dev.felix.hostilehumans.behavior.combat.melee.TridentMovementExecution;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Decision boundaries plus production wiring; does not claim voxel-world acceptance. */
public final class CombatTerrainPolicyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        NavigationHandoffPolicyTest.run();
        dev.felix.hostilehumans.behavior.combat.retreat.RetreatEscapeProgressTest.main(args);
        check(ShoreSeekingPolicy.canExitToDryHeight(64, 64), "level dry bank remains reachable");
        check(ShoreSeekingPolicy.canExitToDryHeight(64, 65), "one-block bank remains reachable");
        check(!ShoreSeekingPolicy.canExitToDryHeight(64, 66), "two-block cliff is not a bank");
        check(!ShoreSeekingPolicy.canExitToDryHeight(64, 80), "tall cliff never becomes a shore step");
        check(ShoreSeekingPolicy.canExitToDryHeight(63.25, 65), "flowing water keeps one dry block allowance");
        check(!ShoreSeekingPolicy.canExitToDryHeight(Double.NaN, 65), "unknown surface fails closed");
        check(!CombatTerrainPolicy.partialUseful(0, 100, 0, false), "logical target cannot disguise a stationary endpoint");
        check(!CombatTerrainPolicy.partialUseful(3.99, 100, 50, false), "tiny partial paths are not progress");
        check(CombatTerrainPolicy.partialUseful(4, 100, 98, false), "actual useful frontier accepted");
        check(!CombatTerrainPolicy.partialUseful(20, 100, 99, false), "strict progress margin");
        check(!CombatTerrainPolicy.partialUseful(20, 100, 50, true), "visited partial endpoint rejected");
        check(!CombatTerrainPolicy.needsAlternative(1, 39, false), "one ordinary failure does not preempt pursuit");
        check(CombatTerrainPolicy.needsAlternative(2, 0, false), "confirmed repeated failures open alternatives");
        check(CombatTerrainPolicy.needsAlternative(0, 40, false), "actual stall opens alternatives");
        check(CombatTerrainPolicy.needsAlternative(0, 0, true), "unsafe travel immediately opens alternatives");
        for (boolean canFire : new boolean[]{false, true})
            for (boolean obstructed : new boolean[]{false, true})
                check(CombatTerrainPolicy.holdFire(canFire, obstructed) == (canFire && obstructed), "stationary answer needs a real shot and terrain failure");
        check(!CombatTerrainPolicy.meleeAllowed(false, false), "cannot melee across an unreachable cliff");
        check(CombatTerrainPolicy.meleeAllowed(true, false), "actual close defense remains possible");
        check(CombatTerrainPolicy.meleeAllowed(false, true), "safe complete approach permits melee");
        check(!CombatTerrainPolicy.completedSearch(0), "no executed work is a deferral, never failure");
        check(CombatTerrainPolicy.completedSearch(1), "completed rings with executed work produce one failure");
        check(!CombatTerrainNavigation.Decision.EXHAUSTED.ownsMovement(), "completed failure releases movement");
        check(!CombatTerrainNavigation.Decision.NORMAL.ownsMovement(), "normal pursuit keeps movement");
        check(CombatTerrainNavigation.Decision.PENDING.ownsMovement(), "active budget wait reserves movement");
        String root = "dev/felix/hostilehumans/";
        check(calls("dev/felix/hostilehumans/navigation/PursuitNavigation", "update", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "update"), "ordinary pursuit consumes the shared decision");
        for (String goal : new String[]{"BowMovementExecution", "CrossbowMovementExecution"})
            check(calls("dev/felix/hostilehumans/entity/ai/goal/" + goal, "tick", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "update"), "ranged tick consumes decision: " + goal);
        check(calls("dev/felix/hostilehumans/behavior/combat/gun/GunMovementExecution", "moveForFiringPosition", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "update"), "gun movement consumes decision without replacing its firing controller");
        check(calls("dev/felix/hostilehumans/behavior/combat/melee/TridentMovementExecution", "ranged", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "update"), "trident movement consumes decision");
        check(calls("dev/felix/hostilehumans/behavior/combat/AdaptiveCombatGoal", "tickDefend", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "update"), "shield defense consumes the same decision");
        check(calls("dev/felix/hostilehumans/navigation/MovementContinuity", "tick", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "ownsRoute"), "generic repair yields to the combat route");
        check(calls("dev/felix/hostilehumans/behavior/combat/ranged/RangedTerrainCombat", "beginTerrainMelee", "dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "canApproachForMelee"), "weapon switch requires a reachable approach");
        check(calls("dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "usefulPath", "net/minecraft/world/level/pathfinder/Path", "getEndNode"), "partial progress uses physical endpoint");
        check(!calls("dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "usefulPath", "net/minecraft/world/level/pathfinder/Path", "getTarget"), "requested destination never substitutes for endpoint");
        check(calls("dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "update", "dev/felix/hostilehumans/navigation/OptionalPathBudget", "claim"), "detours share the server budget");
        check(calls("dev/felix/hostilehumans/navigation/CombatTerrainNavigation", "allowedPosition", "dev/felix/hostilehumans/soldier/SoldierOrder", "allowsCombatPosition"), "detour nodes obey soldier commands");
        System.out.println("CombatTerrainPolicyTest: " + checks + " checks passed; voxel terrain gameplay remains unverified");
    }
    private static boolean calls(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = CombatTerrainPolicyTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(owner) + ".class")) {
            if (input == null) throw new AssertionError("Missing " + owner);
            new ClassReader(input).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method))
            for (var instruction : entry.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(target)) && call.name.equals(name)) return true;
        return false;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
