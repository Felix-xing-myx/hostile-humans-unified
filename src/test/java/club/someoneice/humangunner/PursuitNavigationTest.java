package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Moving-target cadence, failure backoff and actual weapon pursuit entrypoints. */
public final class PursuitNavigationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(!PursuitRefreshPolicy.due(3, false, true, 100, false), "every trigger respects minimum interval");
        check(PursuitRefreshPolicy.due(4, false, false, 0, false), "finished route refreshes without old random cooldown");
        check(PursuitRefreshPolicy.due(4, true, true, 0.25, false), "moving target refreshes before last nodes finish");
        check(PursuitRefreshPolicy.due(4, true, false, 2.25, false), "large displacement updates a live route");
        check(!PursuitRefreshPolicy.due(19, true, false, 1, false), "small displacement avoids repeated A-star");
        check(PursuitRefreshPolicy.due(20, true, false, 1, false), "slow displacement eventually updates");
        check(!PursuitRefreshPolicy.due(19, false, false, 0, true), "unreachable stationary target backs off");
        check(PursuitRefreshPolicy.due(20, false, false, 0, true), "unreachable target retries after bounded backoff");
        check(PursuitRefreshPolicy.due(4, false, false, 3, true), "moved target can reopen a previously failed route");
        int stationary = 0, moving = 0, failure = 0, last = 0, lastFailure = 0;
        double sampled = 0;
        for (int tick = 1; tick <= 200; tick++) {
            if (PursuitRefreshPolicy.due(tick, true, false, 0, false)) stationary++;
            double x = tick * 0.2;
            if (PursuitRefreshPolicy.due(tick - last, true, false, (x - sampled) * (x - sampled), false)) {
                moving++; last = tick; sampled = x;
            }
            if (PursuitRefreshPolicy.due(tick - lastFailure, false, false, 0, true)) {
                failure++; lastFailure = tick;
            }
        }
        check(stationary == 0, "stationary live route needs no periodic search");
        check(moving > 0 && moving <= 50, "moving target remains bounded to at most five searches per second");
        check(failure == 10, "stationary unreachable target only retries once per second");
        String root = "club/someoneice/humangunner/";
        for (String goal : new String[]{"MeleeAttackGoal", "BowAttack", "CrossbowGoal"})
            check(calls("com/craftix/hostile_humans/entity/ai/goal/" + goal, "tick", root + "PursuitNavigation", "update"), "native weapon pursuit wired: " + goal);
        check(calls(root + "GunnerGoal", "approachTarget", root + "PursuitNavigation", "update"), "gun approach uses same continuity gate");
        check(calls(root + "TridentHybridGoal", "tick", root + "PursuitNavigation", "update"), "trident pursuit uses same gate");
        check(calls(root + "PursuitNavigation", "update", root + "BasicNavigationBudget", "tryAcquire"), "all refresh work shares FIFO budget");
        check(calls(root + "PursuitNavigation", "update", root + "HumanTerrainSafety", "safePath"), "replacement retains terrain safety");
        check(calls(root + "PursuitNavigation", "update", "com/craftix/hostile_humans/entity/ai/HumanNavigation", "createFreshPath"), "moving target refresh bypasses obsolete cached route");
        System.out.println("PursuitNavigationTest: " + checks + " checks passed; pursuit gameplay remains to be tested");
    }
    private static boolean calls(String owner, String method, String target, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var input = PursuitNavigationTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
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
