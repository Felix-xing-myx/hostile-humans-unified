"""Default values and spawn integration; not Java compilation or live acceptance."""
from source_paths import SourcePath as Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/dev/felix/hostilehumans"


class BirthSurvivalSuppliesContract(unittest.TestCase):
    def setUp(self):
        self.defaults = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))

    def test_direct_rank_probabilities_and_no_legacy_totem_controls(self):
        for rank, totem, apple in [("roamer", .05, .02), ("tier1", .05, .10), ("tier2", .10, .25), ("tier3", .20, .65)]:
            with self.subTest(rank=rank):
                self.assertEqual(self.defaults["equipment_loadouts"][rank]["totem_chance"], totem)
                self.assertEqual(self.defaults["tiers"][rank]["food"]["enchanted_golden_apple_chance"], apple)
                self.assertNotIn("early_totem_keep_chance", self.defaults["tiers"][rank]["growth_profile"])
                self.assertFalse(any(e.get("item") == "minecraft:totem_of_undying"
                                     for e in self.defaults["equipment_loadouts"][rank]["offhand"]["entries"]))

    def test_apple_quantity_distributions_are_preserved(self):
        expected = {"roamer": {"1": 1}, "tier1": {"1": 1, "2": 1}, "tier2": {"2": 1, "3": 1}, "tier3": {"2": 20, "3": 80}}
        for rank, counts in expected.items():
            self.assertEqual(self.defaults["tiers"][rank]["food"]["enchanted_golden_apple_counts"], counts)

    def test_totem_uses_birth_lease_and_cannot_repeat_or_replace_items(self):
        grant = (PKG / "SpawnTotemSupply.java").read_text(encoding="utf-8")
        self.assertIn("human.level().isClientSide || human.getData() == null", grant)
        self.assertLess(grant.index("if (tag.getBoolean(ROLLED)) return"), grant.index("nextDouble()"))
        self.assertLess(grant.index("tag.putBoolean(ROLLED, true)"), grant.index("nextDouble()"))
        self.assertIn("new ItemStack(Items.TOTEM_OF_UNDYING)", grant)
        self.assertIn("RecoverySupplies.insertInventory", grant)
        self.assertNotIn("setItemSlot", grant)
        pipeline = (PKG / "HumanSpawnEquipment.java").read_text(encoding="utf-8")
        self.assertLess(pipeline.index("if (tag.getBoolean(AUTHORED))"), pipeline.index("SpawnTotemSupply.grantOnce(human)"))
        self.assertLess(pipeline.index("NaturalEquipmentGrowth.applyOnce(human)"), pipeline.index("SpawnTotemSupply.grantOnce(human)"))
        self.assertLess(pipeline.index("SpawnTotemSupply.grantOnce(human)"), pipeline.index("tag.putBoolean(GENERATED, true)"))

    def test_no_secondary_totem_growth_or_hand_pool_entry(self):
        for name in ("NaturalEquipmentGrowth.java", "EquipmentGrowthPolicy.java", "GrowthConfig.java"):
            self.assertNotIn("earlyTotemKeepChance", (PKG / name).read_text(encoding="utf-8"))
        gear = (PKG / "ConfiguredHumanEquipment.java").read_text(encoding="utf-8")
        self.assertIn("!item.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)", gear)
        migration = (PKG / "SpawnSupplyMigration.java").read_text(encoding="utf-8")
        self.assertIn('growth.remove(key)', migration)
        self.assertIn('retireTotemPool', migration)
        self.assertNotIn('addProperty("totem_chance"', migration)


if __name__ == "__main__":
    unittest.main()
