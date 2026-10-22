package club.someoneice.humangunner;

import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Compiled entrypoint audit; actual mob/inventory/entity insertion needs in-game acceptance. */
public final class HumanArrowCombatTest {
    private static final String HUMAN = "com/craftix/hostile_humans/entity/entities/Human";
    private static final String AMMO = "club/someoneice/humangunner/HumanAmmo";
    private static final String ARROWS = "club/someoneice/humangunner/HumanArrowCombat";
    private static final String SPARTAN = "club/someoneice/humangunner/SpartanRangedCompat";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        check(calls(HUMAN, "getProjectile", ARROWS, "projectileFor"), "projectile requests use actual reserve");
        for (String method : List.of("projectileFor", "prepareCrossbowVolley", "canShoot")) {
            check(!calls(ARROWS, method, AMMO, "consumeArrow"), "queries/loading do not charge: " + method);
        }
        check(calls(ARROWS, "projectileFor", AMMO, "hasArrows"), "no free projectile without reserve");
        check(calls(ARROWS, "canShoot", AMMO, "hasArrows"), "shot eligibility requires reserve");
        for (String mode : List.of("humanGunner$isBowMode", "humanGunner$isCrossbowMode", "humanGunner$isMeleeMode"))
            check(calls(HUMAN, mode, AMMO, "hasArrows"), "static combat mode respects reserves: " + mode);
        check(callsName(ARROWS, "canShoot", "isAlive") && callsName(ARROWS, "canShoot", "isRemoved")
                && callsName(ARROWS, "canShoot", "getDifficulty"), "invalid/dead/peaceful attacks rejected");
        check(field(ARROWS, "canShoot", "isClientSide"), "client cannot execute a shot");
        check(calls(HUMAN, "performCrossbowAttack", ARROWS, "fireNativeCrossbow"),
                "native arrow crossbow goes through the shared paid volley");
        check(calls(HUMAN, "performRangedAttack", ARROWS, "fireVolley"), "bow uses shared paid volley");
        check(calls(SPARTAN, "fire", ARROWS, "fireVolley"), "optional native bolt uses paid volley");
        check(callsName(ARROWS, "fireVolley", "addFreshEntity"), "one publishing boundary");
        check(!calls(HUMAN, "shootCrossbowProjectile", AMMO, "consumeArrow"), "callback cannot double pay");
        check(!calls(SPARTAN, "fire", AMMO, "consumeArrow"), "bolt backend cannot double pay");
        check(callsAnyMethodName(ARROWS, "createArrow"), "actual native ArrowItem factory retained");
        check(callsAnyMethodName(ARROWS, "setPierceLevel"), "native piercing retained");
        check(field(ARROWS, "fireVolley", "DISALLOWED"), "all paid NPC volleys cannot be recovered");
        check(callsName(ARROWS, "markNpcLoad", "remove"), "unpaid native payloads removed at charge completion");
        String inventory = "club/someoneice/humangunner/HumanInventoryContainer";
        for (String method : List.of("<init>", "removeItem", "removeItemNoUpdate"))
            check(calls(inventory, method, ARROWS, "clearNpcLoad"), "NPC action marker cleared on inventory transfer: " + method);
        for (String goal : List.of("BowAttack", "CrossbowGoal")) {
            String owner = "com/craftix/hostile_humans/entity/ai/goal/" + goal;
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
        System.out.println("HumanArrowCombatTest: " + assertions + " shot, goal and pickup wiring checks passed");
    }

    private static ClassNode node(String owner) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = HumanArrowCombatTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
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
            if (instruction instanceof MethodInsnNode call && call.owner.equals(calledOwner)
                    && call.name.equals(calledName)) return true;
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
