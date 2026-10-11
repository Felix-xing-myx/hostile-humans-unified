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
        self.assertIn('1.0D + Math.floor(damage * 1.5D)', source)

    def test_window_aggregates_hits_instead_of_dropping_them(self):
        source = (PKG / 'ShieldDurability.java').read_text(encoding='utf-8')
        self.assertIn('WEAR_WINDOW_TICKS = 2', source)
        self.assertIn('entry.shield == shield', source)
        self.assertIn('entry.damage + damage', source)
        self.assertIn('now + WEAR_WINDOW_TICKS', source)
        self.assertIn('wearCost(entry.damage)', source)
        self.assertNotIn('NEXT_WEAR', source)
        self.assertLess(source.index('pending.removeIf'), source.index('applyWear(human, entry)'))

    def test_debts_flush_before_equipment_transfer_and_save(self):
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        self.assertIn('if (this.getItemBySlot(slot) != stack) ShieldDurability.flush(this);', human)
        self.assertIn('ShieldDurability.flush(this);\n        super.addAdditionalSaveData(compound);', human)
        session = (PKG / 'HumanInventorySession.java').read_text(encoding='utf-8')
        self.assertLess(session.index('ShieldDurability.flush(human)'), session.index('human.stopUsingItem()'))
        main = (PKG / 'HumanGunner.java').read_text(encoding='utf-8')
        for hook in ('onTick', 'onLeave', 'onDeath', 'onStopping'):
            self.assertIn('ShieldDurability::' + hook, main)
        shield = (PKG / 'ShieldDurability.java').read_text(encoding='utf-8')
        self.assertIn('shield.isEmpty() && human.getItemInHand(hand) == shield', shield)

    def test_tacz_preserves_ap_sources_and_records_once_before_split(self):
        tacz = (PKG / 'TaczIntegration.java').read_text(encoding='utf-8')
        self.assertEqual(tacz.count('ShieldDurability.damageShield('), 1)
        self.assertNotIn('setDamageSource(', tacz)
        self.assertLess(tacz.index('ShieldDurability.damageShield(defender, event.getBaseAmount())'),
                        tacz.index('gunSetting("shield_damage_multiplier"'))

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
