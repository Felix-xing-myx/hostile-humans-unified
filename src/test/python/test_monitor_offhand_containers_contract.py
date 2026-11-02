"""Empty consumption vessels must use atomic custody, never a potion fallback or item deletion."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
class MonitorOffhandContainersContract(unittest.TestCase):
    def source(self, name): return (PKG/name).read_text(encoding='utf-8')
    def test_shared_classification_excludes_edible_potions_and_recognizes_vessels(self):
        policy=self.source('inventory/ControlledEquipmentPolicy.java')
        food=policy.split('public static boolean offhandFood',1)[1].split('public static boolean supplyRemainder',1)[0]
        self.assertIn('stack.isEdible()',food)
        self.assertIn('!(stack.getItem() instanceof PotionItem)',food)
        vessels=policy.split('public static boolean supplyRemainder',1)[1]
        self.assertIn('Items.GLASS_BOTTLE',vessels)
        self.assertIn('Items.BOWL',vessels)
    def test_toggle_can_stow_container_without_fabricating_combat_equipment(self):
        loadout=self.source('monitor/combat/MonitorLoadout.java')
        toggle=loadout.split('public HumanEquipmentExchange.Result toggleOffhand()',1)[1].split('private ItemStack findOffhand',1)[0]
        self.assertIn('ControlledEquipmentPolicy.supplyRemainder(current)',toggle)
        self.assertIn('HumanEquipmentExchange.Need.empty(EquipmentSlot.OFFHAND)',toggle)
        select=loadout.split('private ItemStack findOffhand',1)[1].split('public static boolean prepare',1)[0]
        self.assertIn('ControlledEquipmentPolicy.offhandFood(item)',select)
        self.assertIn('item == human.getMainHandItem() || item == human.getOffhandItem()',select)
        self.assertIn('ControlledEquipmentPolicy.oneHandWeapon(human.getMainHandItem())',select)
        self.assertIn('SpartanEquipmentCompat.isShield(item)',select)
        for forbidden in ['setItemInHand(', 'setItemSlot(', '.shrink(', '.discard(', 'new ItemStack(']:
            self.assertNotIn(forbidden,toggle+select)
    def test_supply_press_can_replace_vessel_but_not_drink_or_equipment(self):
        recovery=self.source('behavior/combat/recovery/RecoverySupplies.java')
        refill=recovery.split('private static ItemStack prepareManualSupply',1)[1].split('/** Held food repeats',1)[0]
        self.assertIn('!ControlledEquipmentPolicy.supplyRemainder(held)',refill)
        self.assertIn('!ControlledEquipmentPolicy.offhandFood(candidate)',refill)
        self.assertIn('HumanEquipmentExchange.request',refill)
        self.assertIn('item -> item == candidate',refill)
        finish=recovery.split('public static void finishUse',1)[1].split('public static boolean',1)[0]
        self.assertIn('if (receipt && session.food())',finish)
if __name__=='__main__': unittest.main()
