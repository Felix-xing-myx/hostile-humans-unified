package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Compiled seams only; native-selector lifecycle and route handoffs still require server acceptance. */
public final class MovementAuthorityWiringTest {
    private static final String BRIDGE = "club/someoneice/humangunner/HumanGoalExecutionBridge";
    public static void main(String[] args) throws Exception {
        check(calls(BRIDGE, "start", "acquire") && calls(BRIDGE, "start", "start"), "permission precedes native start");
        check(calls(BRIDGE, "start", "unlock"), "denied native start releases candidate flags");
        check(calls(BRIDGE, "tickDelegate", "heartbeat") && !calls(BRIDGE, "snapshot", "heartbeat"), "only real delegate execution renews lease");
        check(calls(BRIDGE, "stopDelegate", "finish") && calls(BRIDGE, "stopDelegate", "unlock"), "native stop releases lease and flags");
        check(calls(BRIDGE, "cancelExecution", "equals"), "late cancellation matches the exact token");
        var state = node(BRIDGE + "$State");
        check(state.fields.stream().anyMatch(field -> field.name.equals("human") && field.desc.equals("Ljava/lang/ref/WeakReference;")), "selector does not retain Human strongly");
        for (var method : node(BRIDGE).methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                check(!call.name.equals("canUse") && !call.name.equals("canContinueToUse"), "bridge cannot reevaluate native admission");
                check(!call.name.equals("getNavigation") && !call.name.equals("createPath") && !call.name.equals("moveTo"), "bridge cannot replace native navigation");
            }
            if (instruction instanceof FieldInsnNode field && field.owner.endsWith("ActionCoordinator$Resource"))
                check(field.name.equals("BASE_MOVE"), "goal adapter cannot claim unrelated action resources");
        }
        check(calls("club/someoneice/humangunner/mixin/HumanGoalSelectorExecutionMixin", "hostileHumans$admitSelectedGoal", "start"), "selector owns admission seam");
        check(calls("club/someoneice/humangunner/mixin/HumanWrappedGoalExecutionMixin", "hostileHumans$stopActualDelegate", "stopDelegate"), "native delegate stop is scoped");
        String gunner = "club/someoneice/humangunner/GunnerGoal";
        String gunMovement = "club/someoneice/humangunner/GunMovementExecution";
        check(calls(gunner, "moveForFiringPosition", "gunPositioning")
                && calls(gunner, "moveForFiringPosition", "moveForFiringPosition"), "attack facts pass hub command to movement execution");
        check(calls(gunner, "start", "start") && calls(gunner, "stop", "stop"), "native goal retains movement lifecycle");
        check(!node(gunMovement).superName.equals("net/minecraft/world/entity/ai/goal/Goal"), "movement execution adds no native scheduler");
        for (var method : node(gunMovement).methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                check(!call.name.equals("shoot") && !call.name.equals("beginReload")
                        && !call.name.equals("startUsingItem") && !call.name.equals("doHurtTarget"), "movement cannot submit weapon outcomes");
                check(!call.name.equals("request") && !call.name.equals("acquire"), "movement reuses original goal admission");
            }
        }
        System.out.println("MovementAuthorityWiringTest passed");
    }
    private static ClassNode node(String owner) throws Exception {
        var node = new ClassNode();
        try (var stream = MovementAuthorityWiringTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static boolean calls(String owner, String method, String target) throws Exception {
        for (var candidate : node(owner).methods) if (candidate.name.equals(method))
            for (var instruction : candidate.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals(target)) return true;
        return false;
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
