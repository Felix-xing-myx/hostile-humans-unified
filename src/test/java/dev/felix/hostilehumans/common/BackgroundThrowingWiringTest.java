package dev.felix.hostilehumans.common;

import dev.felix.hostilehumans.testutil.ModuleTestSources;
import dev.felix.hostilehumans.faction.StandingIncidents;

import dev.felix.hostilehumans.behavior.combat.recovery.CombatConsumableGoal;
import dev.felix.hostilehumans.behavior.combat.throwable.PotionThrowing;
import dev.felix.hostilehumans.behavior.combat.throwable.TacticalThrowables;
import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableBallistics;
import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableTransactions;
import dev.felix.hostilehumans.compat.scguns.ScgunsThrowables;
import dev.felix.hostilehumans.damage.HumanProjectileRelations;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.util.List;

/** Compiled entrypoint audit; entity-insertion cancellation and reloads still need server acceptance. */
public final class BackgroundThrowingWiringTest {
    private static final String ROOT = "dev/felix/hostilehumans/";
    public static void main(String[] args) throws Exception {
        String transaction = "dev/felix/hostilehumans/behavior/combat/throwable/ThrowableTransactions";
        check(calls(transaction, "submit", "request") && calls(transaction, "submit", "acquire")
                && calls(transaction, "submit", "finish"), "every public transaction settles an action token");
        for (var method : node(transaction).methods) for (var instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field
                    && field.owner.equals(dev.felix.hostilehumans.testutil.ModuleTestSources.owner("dev/felix/hostilehumans/behavior/ActionCoordinator$Resource")))
                check(field.name.equals("INVENTORY"), "throw only reserves inventory: " + field.name);
        }
        check(calls(transaction, "submit", "split") && calls(transaction, "submit", "addFreshEntity"), "one real item before publication");
        check(calls(transaction, "submit", "capture") && calls(transaction, "submit", "running"), "origin and session retained");
        for (String guard : List.of("canTarget", "allowsTarget", "isEditing", "isAlive", "isRemoved"))
            check(calls(transaction, "available", guard), "invalid prelaunch state rejected: " + guard);
        for (String owner : List.of("ScgunsThrowables", "PotionThrowing", "ThrowableTransactions")) {
            for (var method : node(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(ROOT + owner)).methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call)
                    check(!List.of("startUsingItem", "setItemSlot", "setItemInHand", "swing", "setYRot", "setSprinting").contains(call.name),
                            "background throw cannot claim hands/body: " + owner + "." + call.name);
            }
        }
        check(calls("dev/felix/hostilehumans/behavior/combat/throwable/TacticalThrowables", "tick", "tick"), "shared living tick owns background throw scheduling");
        check(calls("dev/felix/hostilehumans/behavior/combat/throwable/PotionThrowing", "launchDebuff", "submit")
                && calls("dev/felix/hostilehumans/compat/scguns/ScgunsThrowables", "tick", "submit"), "all backends use the same real payment");
        check(!calls("dev/felix/hostilehumans/behavior/combat/recovery/CombatConsumableGoal", "start", "addFreshEntity")
                && calls("dev/felix/hostilehumans/behavior/combat/recovery/CombatConsumableGoal", "start", "beginCombatBuffUse"), "buff drinks keep common recovery custody");
        check(calls("dev/felix/hostilehumans/damage/HumanProjectileRelations", "passesThroughEntity", "protectsWithoutOwner"), "missing owner uses saved source");
        var incidents = new dev.felix.hostilehumans.faction.StandingIncidents();
        var player = java.util.UUID.randomUUID();
        incidents.groupAlert(player, 100);
        var snapshot = incidents.retaliationExpiries(100);
        check(snapshot.get(player) == 700L, "original incident deadline captured");
        check(incidents.retaliationExpiries(700).isEmpty(), "expiry does not extend for a projectile");
        check(incidents.retaliationExpiries(100).equals(snapshot), "read-only export does not prune live memory");
        try { snapshot.clear(); throw new AssertionError("mutable incident snapshot"); }
        catch (UnsupportedOperationException expected) { }
        var permission = new SoldierCombatMode.OriginPermissions(SoldierCombatMode.AUTHORIZED,
                List.of(new SoldierCombatMode.OriginAuthorization(player, 250, "OWNER_ASSAULT")));
        check(permission.permits(player, 250) && !permission.permits(player, 251), "original command deadline preserved");
        check(SoldierCombatMode.OriginPermissions.load(permission.save()).equals(permission), "bounded source permissions persist");
        var shot = ThrowableBallistics.solve(32, -.5, 0, .05, 1.8, 120);
        check(shot != null, "potion arc can actually reach the approved 32-block boundary");
        check(Math.abs(Math.sqrt(shot.x()*shot.x()+shot.y()*shot.y()+shot.z()*shot.z())-1.8)<1e-8,
                "potion uses the declared launch speed");
        System.out.println("BackgroundThrowingWiringTest passed");
    }
    private static ClassNode node(String owner) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = BackgroundThrowingWiringTest.class.getClassLoader().getResourceAsStream(dev.felix.hostilehumans.testutil.ModuleTestSources.owner(owner) + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
    private static boolean calls(String owner, String method, String called) throws Exception {
        for (var candidate : node(owner).methods) if (candidate.name.equals(method))
            for (var instruction : candidate.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals(called)) return true;
        return false;
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
