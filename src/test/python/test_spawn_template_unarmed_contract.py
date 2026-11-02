"""Guard load-boundary isolation and the shared unarmed cooldown integration."""
from source_paths import SourcePath as Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java"
PKG = JAVA / "dev/felix/hostilehumans"


class SpawnTemplateUnarmedContract(unittest.TestCase):
    def test_forge_data_is_detached_before_any_load_hook(self):
        human = (JAVA / "dev/felix/hostilehumans/entity/Human.java").read_text(encoding="utf-8")
        body = re.search(r"public void load\(CompoundTag compound\)\s*\{([^}]+)\}", human)
        self.assertIsNotNone(body)
        # Copying in readAdditionalSaveData is too late: Forge already installed the alias.
        self.assertRegex(body.group(1), r"super\.load\(compound\.copy\(\)\);")
        self.assertNotIn("compound.remove", body.group(1))

    def test_only_empty_mainhand_uses_cap_at_common_cooldown_entry(self):
        timing = (PKG / "MeleeAttackTiming.java").read_text(encoding="utf-8")
        self.assertIn("BetterCombatMeleeCombat.hasWeaponProfile(human)", timing)
        self.assertIn("EffectiveAttributes.attackSpeedMultiplier(human), 1, human.getMainHandItem().isEmpty()", timing)

    def test_spawner_still_consumes_local_marker_and_preserves_authored_gear(self):
        content = (PKG / "FactionContent.java").read_text(encoding="utf-8")
        self.assertIn("data.remove(SPAWNER_BIRTH)", content)
        self.assertIn("HumanSpawnEquipment.preserveAuthored(human)", content)
        self.assertIn("HumanSpawnEquipment.generate(human, false)", content)


if __name__ == "__main__":
    unittest.main()
