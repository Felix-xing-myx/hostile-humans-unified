"""Durability integration checks; Java item-level cases run only in an authorized build."""
from pathlib import Path
import json
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"


class EquipmentConditionContract(unittest.TestCase):
    def test_armor_shared_gate_and_success_only_timer(self):
        code = (PKG / "HumanLootManager.java").read_text(encoding="utf-8")
        self.assertEqual(code.count("ArmorSwapPolicy.allows(human, slot,"), 2)
        self.assertEqual(code.count("ArmorSwapPolicy.equipped(human, slot)"), 2)
        policy = (PKG / "ArmorSwapPolicy.java").read_text(encoding="utf-8")
        self.assertIn("HumanManagedLoadout.isArmourLocked", policy)
        self.assertIn("HumanSpawnEquipment.isBoundGear", policy)
        self.assertNotIn("LAST_SWAP.put", policy.split("static void equipped", 1)[0])
        config = json.loads((ROOT / "src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))["armor_swap"]
        expected = {"upgrade_min_gain_percent": 10, "upgrade_min_durability_percent": 50,
                    "replace_below_durability_percent": 25, "replacement_durability_advantage_percent": 25,
                    "emergency_below_durability_percent": 10, "emergency_min_quality_percent": 80, "cooldown_ticks": 200}
        self.assertEqual({k: v for k, v in config.items() if not k.startswith("_")}, expected)
        projection = (PKG / "TierConfigProjection.java").read_text(encoding="utf-8")
        ownership = projection.split('for (String key : List.of("recruitment"', 1)[1].split('data.remove(key)', 1)[0]
        self.assertIn('"armor_swap"', ownership)

    def test_whole_equipment_value_loses_wear_without_fixed_floor_or_double_scaling(self):
        code = (PKG / "HumanLootManager.java").read_text(encoding="utf-8")
        self.assertNotIn("0.55D + 0.45D", code)
        self.assertIn("return EquipmentCondition.value(stack, fullEquipmentScore(stack, slot))", code)
        self.assertIn("EquipmentCondition.value(stack, 35.0D)", code)
        self.assertNotIn("return 35.0D + equipmentScore", code)

    def test_search_and_commit_both_reject_nearly_broken_equipment(self):
        code = (PKG / "HumanLootManager.java").read_text(encoding="utf-8")
        for entry, end in (("static boolean collect(", "private static double benefit("),
                           ("private static double benefit(Human human, ItemStack stack,", "private static boolean shouldEquip")):
            block = code.split(entry, 1)[1].split(end, 1)[0]
            self.assertIn("EquipmentCondition.canAutomaticallyCollect", block)
        shield = (PKG / "ShieldPickupPolicy.java").read_text(encoding="utf-8")
        self.assertEqual(shield.count("EquipmentCondition.canAutomaticallyCollect"), 2)
        self.assertIn("HumanLootManager.inventoryValue(human, stored)", shield)
        self.assertIn("HumanLootManager.inventoryValue(human, incoming) - lowest", shield)

    def test_manual_inventory_and_stable_shield_equipping_are_preserved(self):
        for name in ("HumanInventoryMenu", "HumanInventoryContainer"):
            self.assertNotIn("EquipmentCondition", (PKG / (name + ".java")).read_text(encoding="utf-8"))
        compat = (PKG / "SpartanEquipmentCompat.java").read_text(encoding="utf-8")
        rank = compat.split("public static long shieldEquipRank", 1)[1].split("public static double shieldValue", 1)[0]
        self.assertNotIn("getDamageValue", rank)
        policy = (PKG / "EquipmentCondition.java").read_text(encoding="utf-8")
        self.assertIn("!stack.isDamageableItem() || stack.getMaxDamage() <= 0", policy)
        self.assertIn("MIN_AUTOMATIC_PICKUP_PERCENT = 10", policy)
        self.assertNotIn("discard(", policy)
