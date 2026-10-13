"""Structural checks for the version boundary; no Minecraft classes are loaded."""
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import tomllib

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools"))
from audit_tacz_compatibility import disassemble


class TaczVersionCompatibility(unittest.TestCase):
    def test_javap_overloads_keep_invocations_in_their_own_body(self):
        output = """public class Example {
  public Result shoot(java.util.function.Supplier, java.util.function.Supplier, long);
    descriptor: (Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)LResult;
    Code:
       0: invokespecial #1 // Method shoot:(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)LResult;
       3: areturn

  private Result shoot(java.util.function.Supplier, java.util.function.Supplier, long, float, boolean);
    descriptor: (Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)LResult;
    Code:
       0: invokeinterface #2, 2 // InterfaceMethod com/tacz/guns/api/item/IGun.useInventoryAmmo:(Lnet/minecraft/world/item/ItemStack;)Z
       3: areturn
}
"""
        with patch("audit_tacz_compatibility.subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=output)):
            methods = disassemble(Path("fixture.jar"), "Example", Path("javap"))
        self.assertEqual(2, len(methods))
        legacy = methods["shoot", "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)LResult;"]
        modern = methods["shoot", "(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)LResult;"]
        self.assertNotIn("useInventoryAmmo", legacy)
        self.assertIn("useInventoryAmmo", modern)

    def test_metadata_and_required_ammo_hook_open_the_same_version_boundary(self):
        metadata = tomllib.loads((ROOT / "src/main/resources/META-INF/mods.toml").read_text(encoding="utf-8"))
        dependency = next(d for d in metadata["dependencies"]["hostile_humans_unified"] if d["modId"] == "tacz")
        self.assertEqual("[1.1.5,)", dependency["versionRange"])
        self.assertFalse(dependency["mandatory"])
        hook = (ROOT / "src/main/java/club/someoneice/humangunner/mixin/TaczHumanShootMixin.java").read_text(encoding="utf-8")
        self.assertIn("{TaczAmmoMixinContract.SHOOT, TaczAmmoMixinContract.SHOOT_LEGACY}", hook)
        self.assertIn("require = 1, allow = 1", hook)

    def test_sound_compatibility_preserves_new_api_and_only_falls_back_for_missing_method(self):
        client = ROOT / "src/main/java/com/craftix/hostile_humans/client/renderer/ClientAnimationCompatibility.java"
        source = client.read_text(encoding="utf-8")
        self.assertIn('manager.getMethod("playAnimationSound", parameters)', source)
        self.assertIn('catch (NoSuchMethodException olderApi)', source)
        self.assertIn('manager.getMethod("playClientSound", parameters)', source)
        source = (ROOT / "src/main/java/club/someoneice/humangunner/TaczReloadSoundClient.java").read_text(encoding="utf-8")
        self.assertNotIn("SoundPlayManager.playAnimationSound(", source)
        self.assertEqual(2, source.count("ClientAnimationCompatibility.playTaczReloadSound("))


if __name__ == "__main__":
    unittest.main()
