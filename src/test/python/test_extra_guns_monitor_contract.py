"""Optional monitor boundaries and exact installed client hook descriptors; no compilation."""
import os
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/dev/felix/hostilehumans"


class ExtraGunsMonitorContract(unittest.TestCase):
    def test_monitor_enters_existing_shot_and_paid_reload_kernels(self):
        for package, prefix in (("scguns", "Scguns"), ("jeg", "Jeg")):
            text = (PKG / f"compat/{package}/{prefix}MonitorWeapons.java").read_text(encoding="utf-8")
            self.assertIn("GunAttackAction.run(human", text)
            self.assertIn("control.shoot(weapon, null, human.getXRot(), human.getYRot())", text)
            self.assertIn("session.reloadDispatch(() -> backend.beginReload(human, weapon))", text)
            self.assertIn("backend.cancelReload(human, weapon)", text)
            for forbidden in ("new Projectile", "new ServerPlayer", "FakePlayer", "performGunAttack", "setTarget("):
                self.assertNotIn(forbidden, text)

    def test_client_binding_isolation_and_capture_before_cancellation(self):
        for package, prefix in (("scguns", "Scguns"), ("jeg", "Jeg")):
            text = (PKG / f"compat/{package}/{prefix}MonitorInput.java").read_text(encoding="utf-8")
            self.assertNotIn("com.tacz", text)
            for binding in ("KeyBinds.getShootMapping()", "KeyBinds.getAimMapping()", "KeyBinds.KEY_RELOAD"):
                self.assertIn(binding, text)
            for handler in ("Shooting", "Aiming", "Reload"):
                mixin = (PKG / f"mixin/{prefix}Monitor{handler}InputMixin.java").read_text(encoding="utf-8")
                self.assertIn("HumanMonitorClient.active()", mixin)
                self.assertNotIn("require = 0", mixin)
                self.assertNotIn("setDown(", mixin)
                if handler == "Reload":
                    self.assertRegex(mixin, r"captureKey\(event\);\s+callback.cancel\(\)")

    def test_release_intent_has_a_paid_charge_and_cancellation_boundary(self):
        text = (PKG / "compat/jeg/JegMonitorWeapons.java").read_text(encoding="utf-8")
        self.assertIn("!attack && heldTicks > 5", text)
        self.assertIn("backend.hasLoadedAmmo(weapon)", text)
        self.assertIn("heldTicks = 0", text.split("public void cancel()", 1)[1])

    def test_native_hook_descriptors_when_api_jars_are_supplied(self):
        javap = os.environ.get("JAVAP", r"D:\Java\17\bin\javap.exe")
        available = 0
        for prefix, variable in (("Scguns", "SCGUNS_API_JAR"), ("Jeg", "JEG_API_JAR")):
            jar = os.environ.get(variable)
            if not jar:
                continue
            available += 1
            for handler in ("Shooting", "Aiming", "Reload"):
                text = (PKG / f"mixin/{prefix}Monitor{handler}InputMixin.java").read_text(encoding="utf-8")
                target = re.search(r'@Mixin\(targets = "([^"]+)"', text).group(1)
                bytecode = subprocess.check_output([javap, "-p", "-s", "-classpath", jar, target], text=True)
                methods = set(re.findall(r"\b(\w+)\([^\n]*\);\s+descriptor: ([^\s]+)", bytecode))
                selectors = re.findall(r'"(\w+)(\([^"\n]+\)V)"', text)
                self.assertTrue(selectors)
                for name, descriptor in selectors:
                    self.assertIn((name, descriptor), methods, f"{target}: {name}{descriptor}")
            package = "top.ribs.scguns" if prefix == "Scguns" else "ttv.migami.jeg"
            target = package + ".entity.ai.AIGunEvent"
            bytecode = subprocess.check_output([javap, "-p", "-s", "-c", "-classpath", jar, target], text=True)
            self.assertEqual(bytecode.count("Method net/minecraft/world/entity/LivingEntity.m_21023_:(Lnet/minecraft/world/effect/MobEffect;)Z"), 1)
            if prefix == "Scguns":
                direction = re.split(r"\n  (?:public|private|protected)", bytecode.split(" getDirection(", 1)[1], maxsplit=1)[0]
                self.assertEqual(direction.count("Method net/minecraft/world/entity/LivingEntity.m_20184_:()Lnet/minecraft/world/phys/Vec3;"), 1)
            mixin = (PKG / f"mixin/{prefix}MonitorShotMixin.java").read_text(encoding="utf-8")
            methods = set(re.findall(r"\b(\w+)\([^\n]*\);\s+descriptor: ([^\s]+)", bytecode))
            for name, descriptor in re.findall(r'method = "(\w+)(\([^"\n]+\)[^"\n]+)"', mixin):
                self.assertIn((name, descriptor), methods, f"{target}: {name}{descriptor}")
        if not available:
            self.skipTest("Set SCGUNS_API_JAR/JEG_API_JAR for installed public API verification")


if __name__ == "__main__":
    unittest.main()
