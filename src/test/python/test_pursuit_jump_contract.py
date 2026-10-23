"""Source integration checks, not compilation or in-game acceptance."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java"
PKG = JAVA / "club/someoneice/humangunner"
JUMP = (PKG / "PursuitJumpAttack.java").read_text(encoding="utf-8")
GOAL = (JAVA / "com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal.java").read_text(encoding="utf-8")
HUMAN = (JAVA / "com/craftix/hostile_humans/entity/entities/Human.java").read_text(encoding="utf-8")

class PursuitJumpContract(unittest.TestCase):
    def test_optional_hops_use_short_runway_without_blocking_step_jumps(self):
        movement=(PKG/"PlayerLikeMovementGoal.java").read_text(encoding="utf-8")
        self.assertIn('NavigationSupport.hasHopRunway(human)',movement)
        self.assertLess(movement.index('if (pathSuggestsStepUp())'),movement.index('NavigationSupport.hasHopRunway(human)'))
        support=(PKG/"NavigationSupport.java").read_text(encoding="utf-8")
        runway=support.split('static boolean hasHopRunway',1)[1].split('static boolean openBlockingPassage',1)[0]
        self.assertIn('Math.min(8,',runway)
        self.assertNotIn('createPath',runway)
        self.assertNotIn('getBlockState',runway)

    def test_bc_pursuit_stop_and_sprint_share_movement_buffer(self):
        movement=(PKG/"PlayerLikeMovementGoal.java").read_text(encoding="utf-8")
        pursuit=(PKG/"PursuitNavigation.java").read_text(encoding="utf-8")
        spacing=(PKG/"MeleeSpacing.java").read_text(encoding="utf-8")
        self.assertIn('MeleeSpacing.needsChaseBuffer(human,target)',movement)
        self.assertIn('MeleeSpacing.needsChaseBuffer(human,target)',pursuit)
        self.assertIn('human.humanGunner$pendingMeleeTicks(target)',spacing)
        self.assertIn('BetterCombatMeleeCombat.upswingTicks(human)',spacing)
        self.assertIn('MeleeSpacing.preferredDistance(human,target)',GOAL)
    def test_distance_and_time_share_weapon_budget(self):
        for condition in ("attackCooldown > 0", "12 - upswing - MeleeJumpPolicy.attackLeadTicks(upswing)", "closing * approachTicks",
                          "reach - .5", "closing < .04", "Math.abs(target.getY() - human.getY()) > jump.maxHeightDifference()"):
            self.assertIn(condition, JUMP)
        self.assertIn("BetterCombatMeleeCombat.upswingTicks(human)", JUMP)
        self.assertIn("jump.chance()", JUMP)
        self.assertIn("jump.intervalMinTicks()", JUMP)

    def test_only_safe_intentional_pursuit(self):
        for condition in ("human.onGround()", "human.isFleeing", "human.isUsingItem()",
                          "SoldierOrder.isHoldingPosition", "HumanTerrainSafety.safeCombatJump",
                          "CombatTerrainNavigation.hasWork", "route.dot(direction) < .80"):
            self.assertIn(condition, JUMP)
        movement = (PKG / "PlayerLikeMovementGoal.java").read_text(encoding="utf-8")
        self.assertIn("boolean movementHop = isRetreatingFrom(target)", movement)
        self.assertIn("|| (!RecoverySupplies.isEatingFood(human) && isDistantPursuit(target))", movement)
        self.assertNotIn("if (movingWithCombatIntent && human.getRandom()", movement)

    def test_distant_movement_hops_do_not_require_attack_readiness(self):
        movement = (PKG / "PlayerLikeMovementGoal.java").read_text(encoding="utf-8")
        distant = movement.split("private boolean isDistantPursuit", 1)[1].split("private boolean pathSuggestsStepUp", 1)[0]
        for condition in ("isPursuing(target)", "!human.humanGunner$hasPendingMeleeAttack()",
                          "!PursuitJumpAttack.active(human)", "MeleeCombatRange.reach(human, target) + 1.25D"):
            self.assertIn(condition, distant)
        self.assertNotIn("attackCooldown", distant)
        self.assertIn("if (movementHop && human.isSprinting()", movement)
        self.assertIn("human.getRandom().nextDouble() < config.combatJumpChance()", movement)

    def test_first_approach_and_reinforced_roll_share_one_admitted_opportunity(self):
        start = JUMP.split('public static void tryStart(', 1)[1].split('private static boolean safeTakeoff', 1)[0]
        self.assertIn('HumanCombatDirector.strategies(human).reinforcedJump(identity.core(), identity.enabled())', start)
        self.assertIn('if (!first)', start)
        self.assertLess(start.index('state.opportunity.rolled('), start.index('human.getRandom().nextDouble() >= jump.chance()'))
        self.assertLess(start.index('if (!actions.acquire(state.token))'), start.index('human.getJumpControl().jump()'))
        self.assertIn('ActionCoordinator.Resource.MOVE_MODIFIER', start)
        self.assertIn('Math.min(1.25D, jump.approachExtra())', start)
        safety = JUMP.split('private static boolean safeTakeoff', 1)[1].split('private static boolean firstApproach', 1)[0]
        for gate in ('context.insideCombat', 'HumanTerrainSafety.safeStep', 'human.level().noCollision'):
            self.assertIn(gate, safety)

    def test_single_actual_impact_hook_and_cleanup(self):
        self.assertEqual(HUMAN.count("PursuitJumpAttack.strike(this,"), 1)
        self.assertIn("() -> super.doHurtTarget(entityIn)", HUMAN)
        self.assertIn('"Pursuit jump critical", multiplier - 1.0D', JUMP)
        self.assertIn('jumpAttack().damageMultiplier()', JUMP)
        self.assertIn("AttributeModifier.Operation.MULTIPLY_TOTAL", JUMP)
        self.assertIn("finally { damage.removeModifier(CRITICAL); }", JUMP)
        self.assertIn("UnifiedConfig.get().vanillaMode()", JUMP)
        self.assertNotIn("fallDistance", JUMP)
        self.assertNotIn("getDeltaMovement().y", JUMP)

    def test_airborne_lifecycle_and_target_weapon_identity(self):
        for condition in ("state.airborne = true", "else if (state.airborne", "state.started > 3",
                          "state.started > 30", "state.target.equals(target.getUUID())",
                          "state.weapon != human.getMainHandItem()", "state.orderSignature != OrderDistances.signature(human)", "!HumanBehaviorControl.actions(human).running(state.token)", "MeleeJumpPolicy.waitingForAttack(human.tickCount-state.started, human.onGround(), state.attackLead)"):
            self.assertIn(condition, JUMP)
        self.assertIn("PursuitJumpAttack.awaitingAttackWindow(human)", GOAL)
        self.assertIn("!club.someoneice.humangunner.PursuitJumpAttack.active(human)", GOAL)

if __name__ == "__main__": unittest.main()
