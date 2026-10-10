package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Decision boundaries plus production wiring; does not claim voxel-world acceptance. */
public final class CombatTerrainPolicyTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
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
        String root = "club/someoneice/humangunner/";
        check(calls(root + "PursuitNavigation", "update", root + "CombatTerrainNavigation", "update"), "ordinary pursuit consumes the shared decision");
        for (String goal : new String[]{"BowAttack", "CrossbowGoal"})
            check(calls("com/craftix/hostile_humans/entity/ai/goal/" + goal, "tick", root + "CombatTerrainNavigation", "update"), "ranged tick consumes decision: " + goal);
        check(calls(root + "GunnerGoal", "moveForFiringPosition", root + "CombatTerrainNavigation", "update"), "gun movement consumes decision without replacing its firing controller");
        check(calls(root + "TridentHybridGoal", "tickRanged", root + "CombatTerrainNavigation", "update"), "trident movement consumes decision");
        check(calls(root + "AdaptiveCombatGoal", "tickDefend", root + "CombatTerrainNavigation", "update"), "shield defense consumes the same decision");
        check(calls(root + "MovementContinuity", "tick", root + "CombatTerrainNavigation", "ownsRoute"), "generic repair yields to the combat route");
        check(calls(root + "RangedTerrainCombat", "beginTerrainMelee", root + "CombatTerrainNavigation", "canApproachForMelee"), "weapon switch requires a reachable approach");
        check(calls(root + "CombatTerrainNavigation", "usefulPath", "net/minecraft/world/level/pathfinder/Path", "getEndNode"), "partial progress uses physical endpoint");
        check(!calls(root + "CombatTerrainNavigation", "usefulPath", "net/minecraft/world/level/pathfinder/Path", "getTarget"), "requested destination never substitutes for endpoint");
        check(calls(root + "CombatTerrainNavigation", "update", root + "OptionalPathBudget", "claim"), "detours share the server budget");
        check(calls(root + "CombatTerrainNavigation", "allowedPosition", root + "SoldierOrder", "allowsCombatPosition"), "detour nodes obey soldier commands");
        System.out.println("CombatTerrainPolicyTest: " + checks + " checks passed; voxel terrain gameplay remains unverified");
    }
    private static boolean calls(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = CombatTerrainPolicyTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (input == null) throw new AssertionError("Missing " + owner);
            new ClassReader(input).accept(node, 0);
        }
        for (var entry : node.methods) if (entry.name.equals(method))
            for (var instruction : entry.instructions)
                if (instruction instanceof MethodInsnNode call && call.owner.equals(target) && call.name.equals(name)) return true;
        return false;
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
