"""Shared display clock and server/manual adapter ownership; does not claim runtime rendering."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/dev/felix/hostilehumans"

class MonitorOpticsAndBackendsContract(unittest.TestCase):
    def source(self, name):
        return (PKG / name).read_text(encoding="utf-8")

    def test_fov_model_and_input_share_one_aim_curve(self):
        optics = self.source("client/render/tacz/TaczNativeOptics.java")
        model = self.source("client/render/tacz/TaczNativePresentation.java")
        client = self.source("monitor/client/HumanMonitorClient.java")
        self.assertIn("var view = aim(human, (float) event.getPartialTick())", optics)
        self.assertIn("TaczNativeOptics.aim(human, partialTick).aim()", model)
        self.assertNotIn("getSynAimingProgress", model)
        self.assertIn("TaczSpectatorFirstPerson.lookScale(human)", client)
        self.assertIn("(dx * scale)", client)
        self.assertIn("(dy * scale)", client)

    def test_scope_sensitivity_uses_native_user_settings_and_no_player_operator(self):
        optics = self.source("client/render/tacz/TaczNativeOptics.java")
        self.assertIn("MathUtil.zoomSensitivityRatio(fov, base, coefficient)", optics)
        self.assertIn("SCREEN_DISTANCE_COEFFICIENT.get()", optics)
        self.assertIn("ZOOM_SENSITIVITY_BASE_MULTIPLIER.get()", optics)
        self.assertIn("gun.getAimingZoom(stack)", optics)
        self.assertNotIn("client.player", optics)
        self.assertNotIn("System.nanoTime", optics)
        self.assertIn("TaczNativeOptics.clearAim()", self.source("client/render/tacz/TaczSpectatorFirstPerson.java"))

    def test_all_gun_menu_and_session_selection_use_same_factory(self):
        factory = self.source("monitor/combat/MonitorGunBackends.java")
        for backend in ("tacz", "scguns", "jeg"):
            self.assertIn('case "' + backend + '"', factory)
        self.assertIn("NativeMonitorGunCompatibility.available", factory)
        self.assertIn("MonitorGunBackends.supports(item)", self.source("monitor/combat/MonitorLoadout.java"))
        session = self.source("monitor/combat/MonitorCombatSession.java")
        self.assertIn("gun = MonitorGunBackends.create(human, this)", session)
        self.assertIn("dispatchGun = gun.fireIntent(attack)", session)
        self.assertIn("finally { dispatchGun = false; }", session)

    def test_null_target_bridges_preserve_normal_targets_and_have_capability_gates(self):
        plugin = self.source("compat/OptionalMixinPlugin.java")
        for prefix in ("Scguns", "Jeg"):
            mixin = self.source("mixin/" + prefix + "MonitorShotMixin.java")
            self.assertIn("target == null && shooter instanceof Human human", mixin)
            self.assertIn("GunFireAuthorization.allows(human, stack, null)", mixin)
            self.assertIn("return target.hasEffect(effect)", mixin)
            self.assertIn(prefix + "MonitorShotMixin", plugin)
            self.assertNotIn("new Projectile", mixin)
        self.assertIn("supportsMonitorShot", plugin)
        self.assertIn("nativeCalls(attack", plugin)

    def test_optional_input_lives_behind_backend_identities(self):
        input = self.source("monitor/client/MonitorGunInput.java")
        for prefix in ("Tacz", "Scguns", "Jeg"):
            self.assertIn("GunSupport.is" + prefix + "GunItem", input)
            self.assertIn(prefix + "MonitorInput.attackDown()", input)
            self.assertIn(prefix + "MonitorInput.reloadDown()", input)
        mixins = json.loads((ROOT / "src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        for prefix in ("Scguns", "Jeg"):
            self.assertIn(prefix + "MonitorShotMixin", mixins["mixins"])
            for kind in ("Shooting", "Aiming", "Reload"):
                self.assertIn(prefix + "Monitor" + kind + "InputMixin", mixins["client"])

if __name__ == "__main__":
    unittest.main()
