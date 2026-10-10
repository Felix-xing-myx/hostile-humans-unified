"""Integration contracts only; native Unbreaking/axe behavior requires game acceptance."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'


class ShieldDurabilityContract(unittest.TestCase):
    def test_all_wear_uses_one_native_enchanted_transaction(self):
        source = (PKG / 'ShieldDurability.java').read_text(encoding='utf-8')
        self.assertEqual(source.count('shield.hurtAndBreak('), 1)
        self.assertIn('wear(human, shield, InteractionHand.OFF_HAND, incomingDamage)', source)
        self.assertIn('wear(human, shield, hand, blockedDamage)', source)
        self.assertNotIn('setDamageValue(', source)
        self.assertNotIn('hasOwner()', source)
        self.assertIn('1.0D + Math.floor((double) damage * 1.5D)', source)

    def test_shared_wear_cooldown_requires_actual_wear(self):
        source = (PKG / 'ShieldDurability.java').read_text(encoding='utf-8')
        self.assertIn('WEAR_COOLDOWN_TICKS = 4', source)
        self.assertIn('Map<Human, Long>', source)
        self.assertIn('now < NEXT_WEAR.getOrDefault(human, Long.MIN_VALUE)', source)
        self.assertIn('if (actuallyWorn(before.getDamageValue(), before.getCount(), shield.getDamageValue(), shield.getCount()))\n'
                      '            NEXT_WEAR.put(human, now + WEAR_COOLDOWN_TICKS);', source)
        self.assertIn('newDamage > oldDamage || newCount < oldCount', source)

    def test_axe_disable_applies_to_active_shields_without_truncating_cooldown(self):
        source = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        self.assertIn('canDisableShield(this.getUseItem(), this, entityIn)', source)
        self.assertIn('this.shieldCoolDown = Math.max(this.shieldCoolDown, 100)', source)
        self.assertIn('(holdingGun || this.shieldCoolDown > 0)', source)
        self.assertIn('this.shieldCoolDown = Math.max(this.shieldCoolDown, 8)', source)
        passive = (PKG / 'GunnerShieldProtection.java').read_text(encoding='utf-8')
        self.assertNotIn('shieldCoolDown', passive)
        self.assertNotIn('isUsingItem()', passive)

    def test_broken_passive_shield_does_not_interrupt_mainhand_reload(self):
        source = (PKG / 'ShieldDurability.java').read_text(encoding='utf-8')
        self.assertIn('human.isUsingItem() && human.getUsedItemHand() == hand', source)
