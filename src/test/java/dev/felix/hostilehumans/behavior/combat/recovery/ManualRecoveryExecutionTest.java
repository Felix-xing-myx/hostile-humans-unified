package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import java.util.EnumSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Real execution-lease longevity plus compiled ownership/consumption boundaries. No world is booted. */
public final class ManualRecoveryExecutionTest {
    private static final String RECOVERY = "dev/felix/hostilehumans/behavior/combat/recovery/RecoverySupplies";
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static long calls(MethodNode method, String owner, String name) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(i -> i instanceof MethodInsnNode m && (owner == null || m.owner.equals(owner)) && m.name.equals(name)).count();
    }
    public static void main(String[] args) throws Exception {
        ActionCoordinator actions = new ActionCoordinator();
        actions.advance(0);
        var token = actions.requestLease("recovery:manual-use", EnumSet.of(ActionCoordinator.Resource.OFF_HAND), 70, true, 20);
        check(actions.acquire(token), "manual recovery obtains its action resource atomically");
        for (int tick = 1; tick <= 10_000; tick++) {
            actions.advance(tick);
            check(actions.running(token) && actions.heartbeat(token), "extended Forge duration remains live through successful execution");
        }
        actions.advance(10_020);
        check(!actions.running(token), "a stopped executor cannot leave a permanent hand reservation");

        ClassNode recovery = new ClassNode();
        try (var stream = ManualRecoveryExecutionTest.class.getClassLoader().getResourceAsStream(RECOVERY + ".class")) {
            check(stream != null, "compiled recovery implementation exists");
            new ClassReader(stream).accept(recovery, 0);
        }
        MethodNode begin = method(recovery, "beginManualOffhandUse");
        check(calls(begin, RECOVERY, "beginHeldOffhandUse") == 1, "manual use enters the shared real-offhand kernel");
        check(calls(method(recovery, "beginInventoryUse"), RECOVERY, "beginHeldOffhandUse") == 1,
                "automatic use still enters the same kernel after its equipment exchange");
        check(calls(begin, "dev/felix/hostilehumans/behavior/combat/HumanCombatAuthority", "live") == 1,
                "manual entry is authorized by the shared controller");
        for (String forbidden : new String[]{"findBestSlot", "candidateAt", "canUsePotionNow", "needsFood", "forHuman", "request"})
            check(calls(begin, null, forbidden) == 0, "manual entry skips autonomous selection/exchange policy: " + forbidden);
        MethodNode kernel = method(recovery, "beginHeldOffhandUse");
        check(calls(kernel, "dev/felix/hostilehumans/behavior/ActionCoordinator", "requestLease") == 1,
                "manual kernel uses a liveness lease");
        check(calls(kernel, "dev/felix/hostilehumans/behavior/ActionCoordinator", "request") == 1,
                "automatic kernel retains its original bounded action request");
        MethodNode tick = method(recovery, "tickManualSession");
        check(calls(tick, "dev/felix/hostilehumans/behavior/ActionCoordinator", "heartbeat") == 1,
                "valid manual execution renews the lease rather than extending an estimated duration");
        check(calls(tick, RECOVERY, "actualReferences") == 1 && calls(tick, "net/minecraft/world/item/ItemStack", "matches") == 1,
                "manual execution validates actual reference uniqueness, NBT and count before consumption");
        MethodNode cancel = method(recovery, "cancelManualUse");
        check(calls(cancel, RECOVERY, "finishUse") == 1 && calls(cancel, RECOVERY, "clear") == 0,
                "manual cancel settles only its own receipt and never resets effect clocks");
        check(calls(cancel, "java/util/Map", "remove") == 0, "cancellation keeps the press latched until a neutral frame");
        MethodNode pending = method(recovery, "pendingFoodHealing");
        check(calls(pending, "dev/felix/hostilehumans/behavior/combat/recovery/MedicalSupplies", "pendingHealing") == 0,
                "food queue observation does not add a medicine pool");
        for (MethodNode m : new MethodNode[]{begin, tick, cancel, pending}) for (var instruction : m.instructions) {
            if (instruction instanceof FieldInsnNode field)
                check(!field.name.equals("REGENERATION_CLOCKS"), "manual lifecycle preserves existing potion cadence");
            if (instruction instanceof MethodInsnNode call)
                check(!java.util.Set.of("heal", "setHealth", "shrink", "split", "setItemInHand").contains(call.name),
                        "manual lifecycle delegates payment and healing to existing native consumption");
        }
        System.out.println("ManualRecoveryExecutionTest passed");
    }
}
