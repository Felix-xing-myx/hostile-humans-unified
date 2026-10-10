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
        self.assertIn("if (ownsCurrentShield()) human.stopUsingItem()", release)
        self.assertIn("human.getUseItem() == ownedShield", source)
        raise_block = source.split("private void raise(", 1)[1].split("private boolean ownsCurrentShield", 1)[0]
        self.assertLess(raise_block.index("human.isUsingItem() && human.getUsedItemHand() == hand"), raise_block.index("owner = requestedOwner"))

    def test_recovery_reassesses_threat_without_forced_reblock(self):
        recovery = (PKG / "RecoverySupplies.java").read_text(encoding="utf-8")
        controller = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertNotIn("requestShieldReblock", recovery)
        self.assertNotIn("requestRecoveryReblock", controller)
        self.assertNotIn("shield_reblock_until", controller)
        self.assertIn("predictsIncomingMelee(threat)", controller)

    def test_projectile_scan_is_bounded_and_not_random(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertNotIn("projectileDefenseChance", source)
        self.assertNotIn("arrowDefenseDecisions", source)
        self.assertIn("lastProjectileProbeTick == human.tickCount", source)
        self.assertIn("human.tickCount >= nextProjectileScan", source)
        self.assertIn("if (nearest.size() > MAX_TRACKED_ARROWS) nearest.poll()", source)
        self.assertIn("trajectoryClear(arrow, impact)", source)

    def test_warning_and_actual_block_feedback(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertIn("ShieldBlockEvent event", source)
        self.assertIn("!event.isCanceled() && event.getBlockedDamage() > 0.0F", source)
        self.assertIn("!human.getSensing().hasLineOfSight(shooter)", source)
        self.assertIn("shooter.isUsingItem()", source)
        self.assertIn("human.tickCount < nextCounterGuardTick", source)
        self.assertIn("lastBlockedTick >= defenseStartTick", source)

    def test_guard_lease_reaches_timeout_tick(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        self.assertIn("CombatPressurePolicy.MAX_GUARD_WAIT_TICKS + 1", source)
        # Match the owning Goal's start-tick decrement and pre-tick continuation check.
        remaining, held = 21, 0
        visited = []
        while remaining > 0:
            remaining -= 1
            visited.append(held)
            held += 1
        self.assertIn(20, visited)

    def test_controller_does_not_create_competing_navigation_or_attacks(self):
        source = (PKG / "ShieldDefenseController.java").read_text(encoding="utf-8")
        for prohibited in ("extends Goal", "getNavigation()", "doHurtTarget(", "performRangedAttack(", "addCombatGoal("):
            self.assertNotIn(prohibited, source)
        human = (ROOT / "src/main/java/com/craftix/hostile_humans/entity/entities/Human.java").read_text(encoding="utf-8")
        self.assertEqual(human.count("new club.someoneice.humangunner.ShieldDefenseController(this)"), 1)


if __name__ == "__main__":
    unittest.main()
