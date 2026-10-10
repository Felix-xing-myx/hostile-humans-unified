"""Static menu and shield custody integration checks, not gameplay simulation."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"
MENU = (PKG / "HumanInventoryMenu.java").read_text(encoding="utf-8")
PICKUP = (PKG / "ShieldPickupPolicy.java").read_text(encoding="utf-8")
LOOT = (PKG / "HumanLootManager.java").read_text(encoding="utf-8")
COMPAT = (PKG / "SpartanEquipmentCompat.java").read_text(encoding="utf-8")
RECOVERY = (PKG / "RecoverySupplies.java").read_text(encoding="utf-8")

class EquipmentMenuShieldContract(unittest.TestCase):
    def test_native_empty_armor_and_shield_slot_icons(self):
        for part in ("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "SHIELD"):
            self.assertIn("InventoryMenu.EMPTY_ARMOR_SLOT_" + part, MENU)
        self.assertIn("getNoItemIcon()", MENU)
        self.assertIn("Pair.of(InventoryMenu.BLOCK_ATLAS, icon)", MENU)

    def test_shift_equip_empty_slots_first_without_overwrite(self):
        quick = MENU.split("public ItemStack quickMoveStack", 1)[1].split("private static int quickEquipmentSlot", 1)[0]
        self.assertIn("!slots.get(equipment).hasItem()", quick)
        self.assertLess(quick.index("moveItemStackTo(source, equipment"), quick.index("moveItemStackTo(source, 0"))
        self.assertIn("slot.onTake(player, source)", quick)
        for part in ("HEAD", "CHEST", "LEGS", "FEET", "OFF_HAND", "MAIN_HAND"):
            self.assertIn("HumanInventoryContainer." + part, MENU)

    def test_cap_is_on_ground_pickup_not_owner_storage(self):
        self.assertIn("BACKPACK_LIMIT = 3", PICKUP)
        self.assertIn("count += stored.getCount()", PICKUP)
        inspect = PICKUP.split("private static Reserve inspect", 1)[1].split("private static boolean canEquipEmptyHand", 1)[0]
        self.assertNotIn("getOffhandItem", inspect)
        self.assertNotIn("ShieldPickupPolicy", MENU)
        container = (PKG / "HumanInventoryContainer.java").read_text(encoding="utf-8")
        self.assertNotIn("ShieldPickupPolicy", container)
        self.assertIn("return ShieldPickupPolicy.collect(human, entity)", LOOT)
        self.assertIn("return ShieldPickupPolicy.benefit(human, stack, capacity)", LOOT)

    def test_at_cap_replacement_is_lossless_and_rechecked(self):
        self.assertIn("Reserve reserve = inspect(human, ground)", PICKUP)
        self.assertIn("if (reserve.replaceSlot() < 0) return false", PICKUP)
        self.assertIn("HumanSpawnEquipment.isBoundGear(stored)", PICKUP)
        self.assertIn("if (!human.level().addFreshEntity(dropped)) return false", PICKUP)
        self.assertLess(PICKUP.index("addFreshEntity(dropped)"), PICKUP.index("data.setInventoryItem(reserve.replaceSlot()"))
        self.assertIn("ground.shrink(1)", PICKUP)
        self.assertIn("incoming.copyWithCount(1)", PICKUP)

    def test_equipped_shield_order_ignores_wear_and_namespace(self):
        rank = COMPAT.split("public static long shieldEquipRank", 1)[1].split("public static double shieldValue", 1)[0]
        self.assertIn("shield.getMaxDamage()", rank)
        self.assertIn("Long.MAX_VALUE", rank)
        self.assertIn("ToolActions.SHIELD_BLOCK", rank)
        self.assertIn("UseAnim.BLOCK", rank)
        self.assertNotIn("getDamageValue", rank)
        self.assertNotIn("getNamespace", rank)
        self.assertIn("shieldEquipRank(candidate) > shieldEquipRank(current)", rank)
        self.assertIn("SpartanEquipmentCompat.betterShield(data.getInventoryItem(bestShieldSlot), offhand)", RECOVERY)
        self.assertIn("return SpartanEquipmentCompat.shieldEquipRank(shield)", RECOVERY)

if __name__ == "__main__": unittest.main()
