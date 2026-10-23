package club.someoneice.humangunner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.Opcodes;
import java.util.ArrayDeque;
import java.util.HashSet;

/** Backend availability, airborne preparation and compiled real-entrypoint regressions. */
public final class MeleeBackendAndJumpTest {
    public static void main(String[] args) throws Exception {
        var nativeBackend = BetterCombatMeleeCombat.Backend.NATIVE;
        var bc = BetterCombatMeleeCombat.Backend.BETTER_COMBAT;
        var wait = BetterCombatMeleeCombat.Backend.WAIT;
        check(BetterCombatMeleeCombat.Backend.choose(false,false,false,false,0) == nativeBackend, "absent optional integration retains native attacks");
        check(BetterCombatMeleeCombat.Backend.choose(true,false,false,false,0) == nativeBackend, "unsupported bridge retains explicit fallback");
        check(BetterCombatMeleeCombat.Backend.choose(true,true,true,false,0) == nativeBackend, "confirmed unsupported weapon is a native weapon");
        check(BetterCombatMeleeCombat.Backend.choose(true,true,false,false,1) == wait, "temporary registry read failure does not become a native hit");
        check(BetterCombatMeleeCombat.Backend.choose(true,true,true,true,0) == bc, "supported weapon selects BC");
        check(BetterCombatMeleeCombat.Backend.choose(true,true,false,false,2) == wait, "second failed read still waits");
        check(BetterCombatMeleeCombat.Backend.choose(true,true,false,false,3) == nativeBackend, "persistent broken optional API has bounded explicit fallback, not an inert soldier");
        check(BetterCombatMeleeCombat.Backend.choose(true,true,true,true,3) == bc, "successful recovery resumes supported BC attacks");
        check(BetterCombatMeleeCombat.resolveAnimationName("custom:slash",false).equals("custom:slash"), "authored animation is retained");
        check(BetterCombatMeleeCombat.resolveAnimationName("",false).equals("bettercombat:one_handed_slash_horizontal_right"), "blank single-handed animation uses BC fallback, not empty metadata");
        check(BetterCombatMeleeCombat.resolveAnimationName(null,true).equals("bettercombat:two_handed_slash_horizontal_right"), "two-handed fallback keeps BC presentation");
        for (int upswing : new int[]{0,2,5,7,15,100}) {
            int lead = MeleeJumpPolicy.attackLeadTicks(upswing);
            check(lead >= 3 && lead <= 6, "bounded visible takeoff for each windup");
            for (int elapsed = 0; elapsed < lead; elapsed++)
                check(MeleeJumpPolicy.waitingForAttack(elapsed,false,lead), "airborne cannot immediately start a swing");
            check(!MeleeJumpPolicy.waitingForAttack(lead,false,lead), "ground-independent attack clock opens at exact boundary");
            check(MeleeJumpPolicy.waitingForAttack(lead,true,lead), "blocked takeoff cannot masquerade as airborne attack");
        }
        check(MeleeJumpPolicy.attackLeadTicks(2) > MeleeJumpPolicy.attackLeadTicks(7), "shorter weapon windup allows longer visible jump preparation");
        String human = "com/craftix/hostile_humans/entity/entities/Human";
        var attack = method(human,"doHurtTarget");
        JumpInsnNode nullPlan = null;
        boolean sawPlan = false;
        for (var i : attack.instructions) {
            if (i instanceof MethodInsnNode call && call.name.equals("createAttackPlan")) sawPlan = true;
            if (sawPlan && i instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNULL) { nullPlan = jump; break; }
        }
        check(nullPlan != null, "real BC dispatch explicitly rejects an unavailable plan");
        assertNoNativeHit(attack, nullPlan.label);
        check(calls(human,"finishBetterCombatUpswing","createAttackPlan","Profile;"), "impact consumes the committed attack profile");
        check(calls(human,"tick","hasWeaponProfile",null), "sync publishes capability rather than temporary shield/attack permission");
        String renderer = "com/craftix/hostile_humans/client/renderer/BetterCombatNpcAnimator";
        check(!calls(renderer,"isApplicable","isMeleeWeapon",null), "client does not veto a server-supported third-party weapon by sword/axe class");
        check(calls("club/someoneice/humangunner/MeleeCombatRange","canStartAttack","awaitingAttackWindow",null), "all direct melee entries respect intentional jump preparation");
        System.out.println("MeleeBackendAndJumpTest passed");
    }
    private static MethodNode method(String owner, String name) throws Exception {
        var node = new ClassNode();
        try (var stream = MeleeBackendAndJumpTest.class.getClassLoader().getResourceAsStream(owner+".class")) {
            if (stream == null) throw new AssertionError(owner);
            new ClassReader(stream).accept(node,0);
        }
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static boolean calls(String owner, String method, String name, String descriptorPart) throws Exception {
        for (var i : method(owner,method).instructions)
            if (i instanceof MethodInsnNode call && call.name.equals(name)
                    && (descriptorPart == null || call.desc.contains(descriptorPart))) return true;
        return false;
    }
    /** Follow actual normal control flow from plan==null, through the finally cleanup to return. */
    private static void assertNoNativeHit(MethodNode method, AbstractInsnNode start) {
        var pending = new ArrayDeque<AbstractInsnNode>(); var seen = new HashSet<AbstractInsnNode>();
        pending.add(start); boolean returned = false;
        while (!pending.isEmpty()) {
            var i = pending.removeFirst(); if (!seen.add(i)) continue;
            check(!(i instanceof MethodInsnNode call && call.name.equals("doHurtTargetSingle")), "missing BC plan cannot reach native damage fallback");
            if (i.getOpcode() == Opcodes.IRETURN) { returned = true; continue; }
            if (i.getOpcode() == Opcodes.ATHROW) continue;
            if (i instanceof JumpInsnNode jump) {
                pending.add(jump.label); if (jump.getOpcode() == Opcodes.GOTO) continue;
            }
            if (i.getNext() != null) pending.add(i.getNext());
        }
        check(returned, "unavailable plan returns after normal cleanup");
    }
    private static void check(boolean pass,String reason) { if (!pass) throw new AssertionError(reason); }
}
