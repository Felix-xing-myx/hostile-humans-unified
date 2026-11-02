"""Manual-only physical direction error, no camera or autonomous accuracy override."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/dev/felix/hostilehumans"
class TaczControlledHipSpreadContract(unittest.TestCase):
    def test_error_is_sampled_once_before_native_shoot_suppliers(self):
        text = (PKG / "compat/tacz/TaczMonitorWeapons.java").read_text(encoding="utf-8")
        self.assertEqual(text.count("TaczMonitorSpread.sample("), 1)
        self.assertLess(text.index("GunFireAuthorization.allows"), text.index("TaczMonitorSpread.sample("))
        self.assertIn("operator.getSynAimingProgress()", text)
        self.assertIn("HumanGrowth.gunSpreadMultiplier(human)", text)
        self.assertIn("GunCombatProfile.spread(human, weapon)", text)
        self.assertIn(".gunSpreadDegrees(", text)
        self.assertIn("human.getXRot() + error.pitch()", text)
        self.assertIn("human.getYRot() + error.yaw()", text)
        for forbidden in ("setXRot(", "setYRot(", "new Projectile", "setHealth("):
            self.assertNotIn(forbidden, text)
    def test_policy_has_no_optional_types_or_persistent_gameplay_writers(self):
        text = (PKG / "compat/tacz/TaczMonitorSpread.java").read_text(encoding="utf-8")
        self.assertIn("HIP_STANDARD_DEVIATION_DEGREES = 3.0", text)
        self.assertIn("(1 - aim) * (1 - aim)", text)
        self.assertIn("GAUSSIAN_LIMIT = 2.5", text)
        for forbidden in ("com.tacz", "net.minecraft", "getPersistentData", "System.nanoTime"):
            self.assertNotIn(forbidden, text)
if __name__ == "__main__": unittest.main()
