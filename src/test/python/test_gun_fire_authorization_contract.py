"""Final-shot contracts and read-only native API checks, not gameplay tests."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"


class GunFireAuthorizationContract(unittest.TestCase):
    def test_loot_and_stale_or_unloaded_targets_cannot_fire(self):
        source = (PKG / "GunFireAuthorization.java").read_text(encoding="utf-8")
        for guard in ("human.isActivelyCollectingLoot()", "target == null", "target != human.getTarget()",
                      "!target.isAlive()", "target.isRemoved()", "target.isSpectator()", "target.level() != level",
                      "level.getEntity(target.getUUID()) != target", "weapon != human.getMainHandItem()",
                      "!human.canAttack(target)", "HumanRelations.allied(human, target)",
                      "BodyguardProtection.attack(human) != target", "RangedShotClearance.canShoot(human, target)"):
            self.assertIn(guard, source)
        self.assertNotIn("getEntitiesOfClass", source)

    def test_all_callers_pass_the_target_they_aim_at(self):
        ordinary = (PKG / "GunnerGoal.java").read_text(encoding="utf-8")
        retreat = (PKG / "AdaptiveCombatGoal.java").read_text(encoding="utf-8")
        self.assertIn("operator.shoot(weapon, target, shotAim.pitch(), shotAim.yaw())", ordinary)
        self.assertIn("gunOperator.shoot(weapon, threat, pitch, yaw)", retreat)
        for name in ("CompositeGunSupport", "TaczIntegration", "ScgunsIntegration"):
            source = (PKG / (name + ".java")).read_text(encoding="utf-8")
            self.assertIn("GunFireAuthorization.allows(human, weapon, target)", source)
        scguns = (PKG / "ScgunsIntegration.java").read_text(encoding="utf-8")
        self.assertIn("AIGunEvent.performGunAttack(human, target, weapon", scguns)

    def test_native_event_rechecks_target_before_any_native_shot(self):
        source = (PKG / "TaczIntegration.java").read_text(encoding="utf-8")
        self.assertIn("EventPriority.HIGHEST, TaczIntegration::onGunShoot", source)
        event = source.split("private static void onGunShoot(", 1)[1].split("private static", 1)[0]
        self.assertIn("event.getShooter() instanceof Human", event)
        self.assertIn("LogicalSide.SERVER", event)
        self.assertIn("event.setCanceled(true)", event)
        self.assertIn("event.getGunItemStack(), human.getTarget()", event)

    def test_native_event_is_cancelable_and_precedes_shot_notification(self):
        jar = os.environ.get("TACZ_API_JAR")
        if not jar:
            self.skipTest("TaCZ API jar not supplied")
        javap = "D:/Java/17/bin/javap.exe"
        source = subprocess.check_output([javap, "-classpath", jar, "-p", "-c",
                                          "com.tacz.guns.entity.shooter.LivingEntityShoot"], text=True)
        self.assertLess(source.index("IEventBus.post:"), source.index("ServerMessageGunShoot"))
        self.assertIn("ShootResult.FORGE_EVENT_CANCEL", source)
        api = subprocess.check_output([javap, "-classpath", jar, "-c",
                                       "com.tacz.guns.api.event.common.GunShootEvent"], text=True)
        self.assertIn("iconst_1", api.split("boolean isCancelable();", 1)[1].split("public", 1)[0])
