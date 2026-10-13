"""Static wiring checks; live left-handed TaCZ rendering still needs game acceptance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / 'src/main/java'
PKG = JAVA / 'club/someoneice/humangunner'


class PickupAndLeftHandContract(unittest.TestCase):
    def test_tacz_actual_main_hand_uses_supported_draw_context_and_restores_effect_scope(self):
        source = (JAVA / 'com/craftix/hostile_humans/client/renderer/HumanAnimatedItemInHandLayer.java').read_text(encoding='utf-8')
        branch = source.split('if (GunSupport.isTaczGunItem(item) && arm == human.getMainArm())', 1)[1].split('} else {', 1)[0]
        self.assertIn('itemRenderer.renderItem(human, item, visualContext', branch)
        self.assertIn('visualArm == HumanoidArm.LEFT', branch)
        self.assertIn('translateToHand(visualArm, poseStack)', source)
        self.assertIn('ScgunsNpcAnimator.render(human, item, visualContext', source)
        self.assertIn('finally { TaczNpcEffects.end(previous); }', branch)
        self.assertNotIn('setLeftHanded', source)
        self.assertNotIn('setItemSlot', source)

    def test_both_firearms_share_right_hand_presentation_without_inventory_mutation(self):
        base = JAVA / 'com/craftix/hostile_humans/client/renderer'
        policy = (base / 'HumanGunPresentation.java').read_text(encoding='utf-8')
        self.assertIn('GunSupport.isGunItem(stack)', policy)
        identity = (PKG / 'GunSupport.java').read_text(encoding='utf-8')
        for backend in ('TACZ', 'SCGUNS', 'JEG'):
            self.assertIn(backend + '.test(stack)', identity)
        self.assertIn('? HumanoidArm.RIGHT : human.getMainArm()', policy)
        for name in ('TaczNativeAnimation.java', 'ScgunsNativePose.java', 'ScgunsNpcAnimator.java', 'HumanRenderer.java'):
            self.assertIn('HumanGunPresentation.mainArm(human)', (base / name).read_text(encoding='utf-8'))
        self.assertNotIn('setLeftHanded', policy)
        self.assertNotIn('setItemSlot', policy)

    def test_food_comparison_and_eviction_share_whole_stack_cost(self):
        source = (PKG / 'SupplyInventoryPolicy.java').read_text(encoding='utf-8')
        compare = source.split('boolean canReplace(', 1)[1].split('double replacementCost(', 1)[0]
        self.assertIn('incoming.kind() == Kind.FOOD && stored.kind() == Kind.FOOD', compare)
        self.assertIn('incoming.supplyValue() > stored.supplyValue()', compare)
        commit = (PKG / 'HumanLootManager.java').read_text(encoding='utf-8').split('static boolean storeWithEviction(', 1)[1].split('static int lowestEvictableSlot(', 1)[0]
        self.assertIn('reservation.replacementCost(i, candidate)', commit)
        self.assertIn('reservation.canReplace(i, candidate)', commit)

    def test_shield_wear_and_enchantments_are_not_counted_twice(self):
        source = (PKG / 'SpartanEquipmentCompat.java').read_text(encoding='utf-8')
        score = source.split('public static double shieldValue(', 1)[1].split('static void equipOrStoreShield(', 1)[0]
        self.assertIn('ShieldQualityPolicy.fullValue', score)
        self.assertNotIn('getDamageValue', score)
        self.assertNotIn('getNamespace', score)
        loot = (PKG / 'HumanLootManager.java').read_text(encoding='utf-8')
        self.assertIn('if (!SpartanEquipmentCompat.isShield(stack)) score += enchantmentLevels * 2.5D', loot)
        self.assertIn('return EquipmentCondition.value(stack, fullEquipmentScore(stack, slot))', loot)
