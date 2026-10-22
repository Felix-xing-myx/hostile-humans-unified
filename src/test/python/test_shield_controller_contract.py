"""Shield controller ownership and integration contracts; not gameplay tests."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"


class ShieldControllerContract(unittest.TestCase):
    def test_goals_share_entity_controller(self):
        adaptive = (PKG / "AdaptiveCombatGoal.java").read_text(encoding="utf-8")
        guard = (PKG / "BodyguardMovementGoal.java").read_text(encoding="utf-8")
        self.assertIn("this.shields = human.humanGunner$shieldDefense()", adaptive)
        for entry in ("wantsCombat", "beginCombat", "tickCombat", "endCombat"):
            self.assertIn("shields." + entry + "(", adaptive)
        self.assertIn("humanGunner$shieldDefense().tickBodyguard(owner, threat)", guard)
        self.assertIn("humanGunner$shieldDefense().endBodyguard()", guard)
        for goal in (adaptive, guard):
            self.assertNotIn("humanGunner$startMovementShield", goal)
            self.assertNotIn("humanGunner$startProjectileShield", goal)
            self.assertNotIn("arrowDefenseDecisions", goal)

    def test_release_checks_owner_and_actual_stack(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        release = source.split("private void release(Owner requestedOwner)", 1)[1]
        self.assertLess(release.index("if (owner != requestedOwner) return"), release.index("human.stopUsingItem()"))
        self.assertIn("released != null && human.isUsingItem() && human.getUseItem() == released", release)
        self.assertLess(release.index("owner = null; ownedShield = null"), release.index("human.stopUsingItem()"))
        self.assertIn("finally", release)
        self.assertIn("actions.finish(releasedToken", release)
        self.assertIn("human.getUseItem() == ownedShield", source)
        raise_block = source.split("private void raise(", 1)[1].split("private boolean ownsCurrentShield", 1)[0]
        self.assertLess(raise_block.index("!human.isUsingItem() || human.getUsedItemHand() != hand || human.getUseItem() != candidate"), raise_block.index("owner = requestedOwner"))

    def test_recovery_reassesses_threat_without_forced_reblock(self):
        recovery = (PKG / "RecoverySupplies.java").read_text(encoding="utf-8")
        controller = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertNotIn("requestShieldReblock", recovery)
        self.assertNotIn("requestRecoveryReblock", controller)
        self.assertNotIn("shield_reblock_until", controller)
        self.assertIn("for (LivingEntity candidate : nearbyMelee) if (predictsIncomingMelee(candidate))", controller)
        self.assertIn("return !meleeBearings().isEmpty()", controller)

    def test_projectile_scan_is_bounded_and_not_random(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertNotIn("projectileDefenseChance", source)
        self.assertNotIn("arrowDefenseDecisions", source)
        self.assertIn("lastProjectileProbeTick == human.tickCount", source)
        self.assertIn("human.tickCount >= nextProjectileScan", source)
        self.assertIn("getEntitiesOfClass(Projectile.class", source)
        self.assertIn("bounds.inflate(32, 16, 32)", source)
        self.assertIn("ShieldProjectileBackend.describe(arrow)", source)
        self.assertIn("model == null || !model.shieldable()", source)
        self.assertIn("!inFront(arrow.position())", source)
        self.assertIn("!visibleArrow(arrow)", source)
        self.assertIn("human.getDeltaMovement(), model", source)
        self.assertIn("trajectoryClear(arrow, impact)", source)

    def test_warning_and_actual_block_feedback(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertIn("ShieldBlockEvent event", source)
        self.assertIn("!event.isCanceled() && event.getBlockedDamage() > 0.0F", source)
        self.assertIn("!human.getSensing().hasLineOfSight(shooter)", source)
        self.assertIn("shooter.isUsingItem()", source)
        blocked = source.split("public void observeBlockedHit()", 1)[1].split("private LivingEntity preparingKnownShot", 1)[0]
        self.assertIn("owner == Owner.COMBAT && !pressureCycleActive && !defendingProjectile", blocked)
        self.assertIn("meleeBlocks++", blocked)
        self.assertIn("human.getRandom().nextInt(3) == 0", blocked)
        self.assertIn("recoveryPressure.executingEvent", blocked)

    def test_melee_guard_ends_on_three_blocks_or_quiet_time_without_reset_on_repeat(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        begin = source.split('int beginCombat(', 1)[1].split('void endCombat()', 1)[0]
        self.assertLess(begin.index('if (ownsCombatDefense()) return 41'), begin.index('meleeBlocks = 0'))
        tick = source.split('CombatUpdate tickCombat(', 1)[1].split('private void updateDefensePressure', 1)[0]
        self.assertIn('meleeCounterRequested || meleeBlocks >= 3 || human.tickCount - defensePressureTick >= 40', tick)
        self.assertLess(tick.index('endCombat()'), tick.index('attackObligation = true'))
        self.assertIn('return new CombatUpdate(2, false)', tick)
        self.assertIn('shieldResources(), priority, true, 2410', source)

    def test_controller_does_not_create_competing_navigation_or_attacks(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        for prohibited in ("extends Goal", "getNavigation()", "doHurtTarget(", "performRangedAttack(", "addCombatGoal("):
            self.assertNotIn(prohibited, source)
        human = (ROOT / "src/main/java/com/craftix/hostile_humans/entity/entities/Human.java").read_text(encoding="utf-8")
        self.assertEqual(human.count("new club.someoneice.humangunner.ShieldDefenseController(this)"), 1)

    def test_pressure_direction_survives_final_navigation_facing(self):
        attention = (PKG / "HumanAttention.java").read_text(encoding="utf-8")
        finish = attention.split("boolean action=", 1)[1]
        self.assertLess(finish.index("if(action&&(request.pressure||human.humanGunner$shieldDefense().defenseOwnsFacing()))route=null"),
                        finish.index("float body=route==null"))
        self.assertIn('40, "高压平滑盾面", true', attention)
        controller = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        block = controller.split("public void observeAttack(", 1)[1].split("public void observeBlockedHit", 1)[0]
        self.assertLess(block.index("recordPressureDirection(source)"), block.index("rememberedShooter ="))


if __name__ == "__main__":
    unittest.main()
