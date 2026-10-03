package club.someoneice.humangunner;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Geometry/policy and compiled wiring checks, not a live-world acceptance test. */
public final class DefensiveBehaviorTest {
    private static final String HUMAN = "com/craftix/hostile_humans/entity/entities/Human";
    private static final String BASE = "com/craftix/hostile_humans/entity/HumanEntity";
    private static final String ADAPTIVE = "club/someoneice/humangunner/AdaptiveCombatGoal";
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(RetreatRouteContinuity.shouldExtend(16, 0.1, 100, 1), "plan before reaching endpoint");
        check(!RetreatRouteContinuity.shouldExtend(81, 0.1, 100, 1), "healthy escape route retained");
        check(RetreatRouteContinuity.shouldExtend(100, 0.1, 100, -0.5), "close pursuer invalidates reverse route");
        check(!RetreatRouteContinuity.shouldExtend(100, 0.1, 625, -0.5), "distant pursuer does not cause route churn");
        check(RetreatRouteContinuity.shouldExtend(36, 0.3, 100, 1), "sprint gets planning lead time");
        check(!RetreatRouteContinuity.shouldExtend(81, 2, 100, 1), "lead time has bounded radius");
        check(calls(ADAPTIVE, "tickRetreat", "needsExtension"),
                "adaptive retreat anticipates endpoint and moving threat");
        check(calls("com/craftix/hostile_humans/entity/ai/goal/RunFromTarget", "tick",
                "needsExtension"), "native retreat also anticipates");
        AABB body = new AABB(-0.3D, 0.0D, -0.3D, 0.3D, 1.8D, 0.3D);
        Vec3 incoming = new Vec3(-10.0D, 1.0D, 0.0D);
        Vec3 velocity = new Vec3(2.0D, 0.1D, 0.0D);
        check(Double.isFinite(impact(incoming, velocity, body, Vec3.ZERO)), "real approaching hit predicted");
        for (int i = 0; i < 30; i++) {
            double miss = 0.46D + 0.03D * i;
            check(Double.isNaN(impact(new Vec3(-10, 1, miss), velocity, body, Vec3.ZERO)),
                    "nearby parallel arrow is not a threat: " + miss);
            check(Double.isNaN(impact(new Vec3(-10, 1, -miss), velocity, body, Vec3.ZERO)),
                    "nearby parallel arrow on other side is not a threat");
        }
        check(Double.isNaN(impact(new Vec3(-10, 4, 0), new Vec3(2, 0, 0), body, Vec3.ZERO)),
                "shot over the head does not trigger guard");
        check(Double.isNaN(impact(new Vec3(5, 1, 0), new Vec3(1, 0, 0), body, Vec3.ZERO)),
                "outgoing arrow does not trigger guard");
        check(Double.isNaN(impact(incoming, velocity, body, new Vec3(0, 0, 1))),
                "defender already sidestepping away is not considered a future hit");
        check(Double.isFinite(impact(new Vec3(-10, 1, 2), velocity, body, new Vec3(0, 0, 0.4))),
                "defender advancing into a shot is considered a future hit");
        check(Double.isNaN(impact(new Vec3(-40, 1, 0), new Vec3(0.5, 0, 0), body, Vec3.ZERO)),
                "prediction horizon bounded to twelve ticks");
        check(Double.isFinite(ProjectileShieldPolicy.impactTicks(new Vec3(-2, 1, 0),
                new Vec3(1, 0, 0), body, Vec3.ZERO, 0.6D, 0.0D)), "water/no-gravity shot supported");
        for (int held = 0; held <= 15; held++) {
            for (int quiet = 0; quiet <= 15; quiet++) {
                check(CombatPressurePolicy.shouldReleaseUnpressuredBlock(held, quiet, false, false)
                                == (held >= 6 && quiet >= 4), "release requires a completed block and quiet gap");
                check(!CombatPressurePolicy.shouldReleaseUnpressuredBlock(held, quiet, true, false),
                        "actual incoming shot preserves guard");
                check(!CombatPressurePolicy.shouldReleaseUnpressuredBlock(held, quiet, false, true),
                        "actual committed melee threat preserves guard");
            }
        }

        MethodNode riding = method(BASE, "startRiding");
        var opcodes = new java.util.ArrayList<Integer>();
        for (var instruction : riding.instructions)
            if (instruction.getOpcode() >= 0) opcodes.add(instruction.getOpcode());
        check(opcodes.size() >= 2
                        && opcodes.stream().filter(op -> op == Opcodes.IRETURN).count() == 1
                        && opcodes.get(opcodes.size() - 2) == Opcodes.ICONST_0
                        && opcodes.get(opcodes.size() - 1) == Opcodes.IRETURN,
                "all vehicles, including forced seat captures, refused at entrypoint");
        check(calls(BASE, "tick", "isPassenger") && calls(BASE, "tick", "stopRiding"),
                "old saved passengers are detached without deleting vehicle or furniture");
        check(calls(BASE, "tick", "setOrderedToSit") && calls(BASE, "tick", "setInSittingPose"),
                "a rejected seat mount cannot leave behind an involuntary sitting state");
        for (var instruction : method(HUMAN, "registerGoals").instructions)
            check(!(instruction instanceof TypeInsnNode type
                            && type.desc.equals("net/minecraft/world/entity/monster/EnderMan")),
                    "autonomous goal has no enderman exclusion");
        check(calls(HUMAN, "tick", "hasShieldFacing") && calls(HUMAN, "tick", "applyShieldFacing"),
                "defensive facing restored after navigation and body controls");
        check(calls(HUMAN + "$HumanMoveControl", "tick", "moveWhileShieldFacing"),
                "movement keeps target-facing shield orientation before travel, not only visually");
        check(calls(ADAPTIVE, "tickDefend", "markShieldFacing")
                        && calls(ADAPTIVE, "tickDefend", "shouldReleaseUnpressuredBlock"),
                "defensive goal uses one-tick facing lease and timely release policy");
        check(!calls(ADAPTIVE, "tickDefend", "jump"), "defense phase never requests random combat jumps");
        check(calls(ADAPTIVE, "tickDefend", "moveWhileDefendingRanged"),
                "ranged defense restores an interrupted route instead of assuming it survived");
        check(calls(ADAPTIVE, "tickDefend", "control"),
                "close guarded melee shares impact spacing instead of forcing a separate standstill");
        check(calls("club/someoneice/humangunner/MeleeSpacing", "control", "getLastHurtByMobTimestamp"),
                "recent close-range damage opens a small movement reaction without resetting attack cooldown");
        check(calls(ADAPTIVE, "startShieldBlockIfAvailable", "humanGunner$startMovementShield"),
                "ordinary combat defense uses mobile shield allowance");
        check(calls(HUMAN, "recoverStalledNavigation", "tick"),
                "navigation recovery uses the unified continuity module");
        String continuity = "club/someoneice/humangunner/MovementContinuity";
        check(calls(continuity, "tick", "stalled") && calls(continuity, "tick", "setWantedPosition")
                        && calls(continuity, "tick", "claim"),
                "fast input recovery precedes budgeted path repair");
        check(!calls(continuity, "tick", "stop") && !calls(continuity, "tick", "stopUsingItem")
                        && !calls(continuity, "tick", "setTarget"),
                "continuity never cancels a route, food custody or combat target");
        check(calls(ADAPTIVE, "tickRetreat", "escapeWhilePlanning")
                        && calls(HUMAN + "$HumanMoveControl", "tick", "applyStep"),
                "safe local escape survives the wait for an incremental route planner");
        check(!calls("com/craftix/hostile_humans/entity/ai/goal/RunFromTarget",
                "canContinueToUse", "stop"),
                "landing/continuation checks cannot stop native escape navigation");
        check(calls(HUMAN, "knockback", "knockback"),
                "shorter hit reaction still delegates to vanilla knockback and resistance");
        check(calls(HUMAN, "doHurtTarget", "facePendingBetterCombatAttack")
                        && calls(HUMAN, "tick", "facePendingBetterCombatAttack"),
                "delayed Better Combat attacks face their opponent at start and throughout windup");
        check(calls("club/someoneice/humangunner/BetterCombatMeleeCombat", "canStrike", "aimPoint"),
                "spacing tests reachable aimed geometry rather than stale navigation pitch");
        Vec3 attackOrigin = new Vec3(0.0D, 1.53D, 0.0D);
        Vec3 above = MeleeCombatRange.aimPoint(attackOrigin, new AABB(1, 2, -0.3, 1.6, 3.8, 0.3));
        Vec3 below = MeleeCombatRange.aimPoint(attackOrigin, new AABB(1, -2, -0.3, 1.6, -0.2, 0.3));
        check(above.y > attackOrigin.y && below.y < attackOrigin.y,
                "elevated and lowered opponents produce opposite pitch directions");
        Vec3 tall = MeleeCombatRange.aimPoint(attackOrigin, new AABB(1, 0, -0.3, 1.6, 10, 0.3));
        check(Math.abs(tall.y - attackOrigin.y) < 1.0E-6D,
                "tall opponents are aimed at their reachable body instead of high center");
        Vec3 overhead = MeleeCombatRange.aimPoint(attackOrigin, new AABB(-0.3, 2, -0.3, 0.3, 3, 0.3));
        check(overhead.x == 0.0D && overhead.z == 0.0D && overhead.y > attackOrigin.y,
                "directly overhead aim is finite and vertical");
        check(calls(HUMAN + "$HumanMoveControl", "tick", "facePendingBetterCombatAttack"),
                "navigation cannot rotate pending attacks toward a path node");
        check(calls(ADAPTIVE, "findIncomingArrow", "impactTicks")
                        && calls(ADAPTIVE, "findIncomingArrow", "shouldGuardRangedUser"),
                "live arrow entrypoint uses swept collision prediction and ranged interruption bound");
        check(calls(HUMAN, "readAdditionalSaveData", "remove"),
                "reload clears tick-count based gunfire memory instead of treating it as fresh pressure");
        System.out.println("DefensiveBehaviorTest: " + checks + " trajectory, pressure and entrypoint checks passed");
    }

    private static double impact(Vec3 from, Vec3 velocity, AABB body, Vec3 defender) {
        return ProjectileShieldPolicy.impactTicks(from, velocity, body, defender, 0.99D, 0.05D);
    }

    private static MethodNode method(String owner, String name) throws Exception {
        ClassNode node = new ClassNode();
        try (var stream = DefensiveBehaviorTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + owner);
            new ClassReader(stream).accept(node, 0);
        }
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static boolean calls(String owner, String method, String called) throws Exception {
        for (var instruction : method(owner, method).instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals(called)) return true;
        return false;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
