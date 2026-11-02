package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.testutil.ModuleTestSources;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.defense.GuardedRangedCharge;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat;
import dev.felix.hostilehumans.inventory.HumanInventoryContainer;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Compiled entrypoint audit; actual mob/inventory/entity insertion needs in-game acceptance. */
public final class HumanArrowCombatTest {
    private static final String HUMAN = "dev/felix/hostilehumans/entity/Human";
    private static final String AMMO = "dev/felix/hostilehumans/behavior/combat/ranged/HumanAmmo";
    private static final String ARROWS = "dev/felix/hostilehumans/behavior/combat/ranged/HumanArrowCombat";
    private static final String SPARTAN = "dev/felix/hostilehumans/compat/spartan/SpartanRangedCompat";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        dev.felix.hostilehumans.behavior.combat.ranged.VolleyContinuationTest.main(args);
        check(calls(HUMAN, "getProjectile", ARROWS, "projectileFor"), "projectile requests use actual reserve");
        for (String method : List.of("projectileFor", "prepareCrossbowVolley", "canShoot")) {
            check(!calls(ARROWS, method, AMMO, "consumeArrow"), "queries/loading do not charge: " + method);
        }
        check(calls(ARROWS, "projectileFor", AMMO, "hasArrows"), "no free projectile without reserve");
        check(calls(ARROWS, "canShoot", AMMO, "hasArrows"), "shot eligibility requires reserve");
        for (String mode : List.of("hhu$isBowMode", "hhu$isCrossbowMode", "hhu$isMeleeMode"))
            check(calls(HUMAN, mode, AMMO, "hasArrows"), "static combat mode respects reserves: " + mode);
        check(callsName(ARROWS, "canShoot", "isAlive") && callsName(ARROWS, "canShoot", "isRemoved")
                && callsName(ARROWS, "canShoot", "getDifficulty"), "invalid/dead/peaceful attacks rejected");
        check(field(ARROWS, "canShoot", "isClientSide"), "client cannot execute a shot");
        check(calls(HUMAN, "performCrossbowAttack", ARROWS, "fireNativeCrossbow"),
                "native arrow crossbow goes through the shared paid volley");
        check(calls(HUMAN, "performRangedAttack", ARROWS, "fireVolley"), "bow uses shared paid volley");
        check(calls(SPARTAN, "fire", ARROWS, "fireVolley"), "optional native bolt uses paid volley");
        check(callsName(ARROWS, "executeVolley", "addFreshEntity"), "one publishing boundary");
        for (String lifecycle : List.of("request", "acquire", "finish"))
            check(callsName(ARROWS, "publishVolley", lifecycle), "public shot transaction lifecycle: " + lifecycle);
        check(callsName(ARROWS, "publishVolley", "add") && callsName(ARROWS, "publishVolley", "remove"),
                "synchronous reentry guard always released");
        check(callsName(ARROWS, "executeVolley", "shotStillValid"), "factory and insertion revalidate live authority");
        check(!calls(HUMAN, "shootCrossbowProjectile", AMMO, "consumeArrow"), "callback cannot double pay");
        check(!calls(SPARTAN, "fire", AMMO, "consumeArrow"), "bolt backend cannot double pay");
        check(callsAnyMethodName(ARROWS, "createArrow"), "actual native ArrowItem factory retained");
        check(callsAnyMethodName(ARROWS, "setPierceLevel"), "native piercing retained");
        check(field(ARROWS, "executeVolley", "DISALLOWED"), "all paid NPC volleys cannot be recovered");
        check(callsName(ARROWS, "markNpcLoad", "remove"), "unpaid native payloads removed at charge completion");
        String snapshot = ARROWS + "$ProjectileSnapshot";
        String ballistics = "dev/felix/hostilehumans/bootstrap/HumanRuntime";
        check(callsName(snapshot, "recreate", "load") && callsName(snapshot, "recreate", "setUUID")
                && callsName(snapshot, "recreate", "setOwner"), "continuations retain native payload with fresh identity");
        check(callsName(snapshot, "recreate", "setPos")
                && calls(snapshot, "recreate", ballistics, "reaimArrowBallistics"),
                "continuation origin and current target feed the explicit re-aim seam");
        check(callsName(ballistics, "reaimArrowBallistics", "isAddedToWorld"),
                "re-aim rejects live flying arrows");
        check(field(ballistics, "reaimArrowBallistics", "isClientSide"), "re-aim is server only");
        check(callsName(ballistics, "reaimArrowBallistics", "improveHumanProjectile")
                && callsName(ballistics, "improveHumanProjectile", "solveArrowBallistics")
                && callsName(ballistics, "improveHumanProjectile", "getDeltaMovement")
                && callsName(ballistics, "improveHumanProjectile", "applyAngularSpread"),
                "first and second shots share live target lead, ballistic solver and independent spread");
        check(callsName(ballistics, "reaimArrowBallistics", "isFinite"), "invalid new launch fails before payment");
        String inventory = "dev/felix/hostilehumans/inventory/HumanInventoryContainer";
        for (String method : List.of("<init>", "removeItem", "removeItemNoUpdate"))
            check(calls(inventory, method, ARROWS, "clearNpcLoad"), "NPC action marker cleared on inventory transfer: " + method);
        for (String goal : List.of("BowAttack", "CrossbowGoal")) {
            String owner = "dev/felix/hostilehumans/entity/ai/goal/" + goal;
            check(calls(owner, goal.equals("BowAttack") ? "isHoldingBow" : "isHoldingCrossbow", AMMO, "hasArrows"),
                    "goal exits without reserve: " + goal);
            check(calls(owner, "tick", owner, "canContinueToUse"), "mid-tick ammo/weapon/target changes rechecked: " + goal);
            check(calls(owner, "tickShot", AMMO, "hasArrows") && !calls(owner, "tickShot", AMMO, "consumeArrow"),
                    "draw/charge changes cannot prematurely pay: " + goal);
        }
        for (String owner : List.of(HUMAN, SPARTAN, ARROWS)) {
            for (MethodNode method : node(owner).methods) {
                if (owner.equals(HUMAN) && !List.of("getProjectile", "performRangedAttack",
                        "performCrossbowAttack", "shootCrossbowProjectile").contains(method.name)) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && (call.name.equals("isHired")
                            || call.name.equals("isInfinity"))) {
                        throw new AssertionError("Hired/Infinity exemption in " + owner + "." + method.name);
                    }
                }
            }
        }
        check(calls(ARROWS, "fireVolley", ARROWS, "publishVolley"), "initial shot uses common transaction");
        check(calls(ARROWS, "tickFollowup", ARROWS, "publishVolley"), "delayed shot uses same paid transaction");
        check(callsName(ARROWS, "tickFollowup", "followupIdentityValid")
                && callsName(ARROWS, "tickFollowup", "hasFollowupShot"), "delayed shot rechecks identity separately from temporary release geometry");
        check(callsName(ARROWS, "followupIdentityValid", "shotContextValid")
                && callsName(ARROWS, "hasFollowupShot", "shotStillValid"), "identity retention never bypasses release safety");
        check(calls("dev/felix/hostilehumans/behavior/HumanBehaviorControl", "cancel", ARROWS, "clear")
                || callsAnyOwner("dev/felix/hostilehumans/behavior/HumanBehaviorControl", ARROWS, "clear"),
                "root lifecycle cancels pending release through lambda cleanup");
        check(calls("dev/felix/hostilehumans/behavior/combat/defense/GuardedRangedCharge", "tick", ARROWS, "beginCrossbowLoad"),
                "background loader requests continuation permission");
        String crossbow = "dev/felix/hostilehumans/behavior/combat/ranged/CrossbowGoal";
        check(calls(crossbow, "tick", crossbow, "tickShot")
                && calls(crossbow, "tickShot", ARROWS, "beginCrossbowLoad"),
                "foreground tick delegates to a shot boundary that requests continuation permission");
        check(method(ARROWS, "beginCrossbowLoad").desc.endsWith(")Z"), "loading boundary returns permission");
        check(callsName(ARROWS, "beginCrossbowLoad", "containsKey"), "pending continuation prevents reload");
        System.out.println("HumanArrowCombatTest: " + assertions + " shot, goal and pickup wiring checks passed");
    }

    private static ClassNode node(String owner) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = HumanArrowCombatTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(owner) + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }

    private static MethodNode method(String owner, String name) throws Exception {
        return node(owner).methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean calls(String owner, String name, String calledOwner, String calledName) throws Exception {
        for (var instruction : method(owner, name).instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(calledOwner))
                    && call.name.equals(calledName)) return true;
        return false;
    }

    private static boolean callsAnyOwner(String owner, String calledOwner, String name) throws Exception {
        for (var method : node(owner).methods) for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(calledOwner)) && call.name.equals(name)) return true;
        return false;
    }
    private static boolean callsAnyMethodName(String owner, String calledName) throws Exception {
        for (var method : node(owner).methods)
            for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals(calledName)) return true;
        return false;
    }

    private static boolean callsName(String owner, String name, String calledName) throws Exception {
        for (var instruction : method(owner, name).instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(calledName)) return true;
        return false;
    }

    private static boolean field(String owner, String name, String fieldName) throws Exception {
        for (var instruction : method(owner, name).instructions)
            if (instruction instanceof FieldInsnNode field && field.name.equals(fieldName)
                    && (field.getOpcode() == Opcodes.GETFIELD || field.getOpcode() == Opcodes.GETSTATIC)) return true;
        return false;
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
