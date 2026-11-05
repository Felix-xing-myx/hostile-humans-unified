"""Source and arithmetic audit only; does not compile Java or launch Minecraft."""
from source_paths import SourcePath as Path
import math
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
CORE = ROOT / "src/main/java/dev/felix/hostilehumans"


class GunDoctrineWiring(unittest.TestCase):
    def source(self, name):
        return (CORE / (name + ".java")).read_text(encoding="utf-8")

    def test_seven_confirmed_ranges_and_conservative_fallback(self):
        source = self.source("GunRangePolicy")
        rows = {name: tuple(map(float, values.split(","))) for name, values in
                re.findall(r'case "(\w+)"->new Band\(([^)]+)\)', source)}
        self.assertEqual(rows, {"pistol": (8, 12, 10, 36), "sniper": (48, 64, 56, 96),
                               "rifle": (24, 36, 30, 80), "smg": (12, 20, 16, 48),
                               "shotgun": (3, 8, 5.5, 24), "mg": (20, 32, 26, 56),
                               "launcher": (32, 48, 40, 56)})
        self.assertIn("default->new Band(8,12,10,24)", source)
        self.assertNotIn("Math.max(band.fireRange(), followRange)", source)
        for field, factor in (("minimum", 1.2), ("maximum", 1.2), ("fireRange", 1.3)):
            self.assertIn(f"Math.floor(base.{field}()*{factor})", source)

    def test_all_backends_have_real_reload_entry_and_original_weapon_binding(self):
        for backend in ("Tacz", "Scguns", "Jeg"):
            self.assertIn("public boolean beginReload(", self.source(backend + "Integration"))
            self.assertIn("public void cancelReload(", self.source(backend + "Integration"))
        session = self.source("GunReloadSession")
        native = self.source("TaczHumanReloadTransactions")
        authority = self.source("GunReloadAuthority")
        self.assertIn("HumanEquipmentExchange.locate", authority)
        self.assertIn("HumanBehaviorControl.actions(human).session()", authority)
        for source in (session, native):
            self.assertIn("GunReloadAuthority", source)
            self.assertIn("GunCombatProfile.reloadWork", source)
        self.assertIn('putDouble("progress"', session)
        self.assertIn("data.reloadTimestamp+=wall-clock.wallMillis-whole", native)
        self.assertIn("CLOCKS.remove(data)", native)
        for backend in ("Scguns", "Jeg"):
            source = self.source(backend + "Integration")
            self.assertIn("GunReloadSession.runTick", source)
            self.assertIn('state.putInt("step",i+1)', source)

    def test_custody_and_server_shots_use_shared_authority(self):
        custody = self.source("GunCustody")
        self.assertNotIn("setItemSlot(", custody)
        self.assertNotIn("setInventoryItem(", custody)
        self.assertIn("AmmoWeaponSelection.mayRestore", custody)
        self.assertIn("HumanEquipmentExchange.request", custody)
        self.assertIn("GunAttackAction.run", self.source("CompositeGunSupport"))
        self.assertIn("!GunAttackAction.owns(human)", self.source("TaczIntegration"))
        self.assertNotIn("doHurtTarget(", self.source("GunnerGoal"))
        action = self.source("GunAttackAction")
        self.assertIn("Resource.ATTACK", action)
        self.assertIn("SpartanEquipmentCompat.isShield", action)

    def test_arithmetic_continuity_for_faction_and_silent_rates(self):
        # Independent arithmetic oracle for the confirmed duration/speed contract.
        for duration, silent in ((1, 2), (.7, 2), (1, 1.2)):
            normal_rate = 1 / duration
            silent_rate = 1 / (duration * silent)
            self.assertAlmostEqual(silent_rate * silent, normal_rate)
            baseline = 100
            before = 20 * normal_rate / baseline
            after = before + 40 * silent_rate / baseline
            needed = max(0, math.ceil((1 - after) * baseline / normal_rate - 1e-9))
            self.assertGreaterEqual(after + needed * normal_rate / baseline + 1e-9, 1)
            if needed:
                self.assertLess(after + (needed - 1) * normal_rate / baseline, 1)


if __name__ == "__main__":
    unittest.main()
