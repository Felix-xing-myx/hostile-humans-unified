"""Cross-module source contracts; these do not execute Minecraft or TaCZ."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'

def source(name):
    return (PKG / name).read_text(encoding='utf-8')

class SuppressiveShieldReloadContract(unittest.TestCase):
    def test_gunfire_is_separate_from_bounded_melee_guard(self):
        shield = source('ShieldDefenseController.java')
        pressure = shield.index('if (underGunfirePressure()) {')
        counter = shield.index('CombatPressurePolicy.shouldCounterAfterGuard(')
        self.assertLess(pressure, counter)
        self.assertIn('Math.max(phaseTicks, 2)', shield[pressure:counter])
        self.assertIn('if (defendingGunfire)', shield[pressure:counter])
        goal = source('AdaptiveCombatGoal.java')
        self.assertIn('phase == Phase.DEFEND && shouldEnterRetreat(threat)', goal)
        self.assertIn('if (!moved && !shields.underGunfirePressure())', goal)

    def test_prediction_filters_rear_arrows_before_candidate_budget(self):
        shield = source('ShieldDefenseController.java')
        self.assertLess(shield.index('if (!inFront(arrow.position())) continue;'),
                        shield.index('nearest.offer(arrow)'))
        self.assertIn('|| !inFront(arrow.position())) continue;', shield)
        self.assertIn('!inFront(shooter.position())', shield)

    def test_gun_shield_is_passive_and_not_an_active_block(self):
        shield = source('ShieldDefenseController.java')
        self.assertIn('return GunSupport.isGunItem(human.getMainHandItem())', shield)
        passive = source('GunnerShieldProtection.java')
        self.assertIn('LivingDamageEvent event', passive)
        self.assertIn('event.isCanceled()', passive)
        self.assertIn('!GunSupport.isGunItem(human.getMainHandItem())', passive)
        self.assertIn('!isUsableShield(human.getOffhandItem())', passive)
        self.assertNotIn('isUsingItem()', passive)
        integration = source('TaczIntegration.java')
        self.assertIn('return !GunSupport.isGunItem(human.getMainHandItem())', integration)
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertEqual(defaults['tacz']['offhand_shield_damage_reduction'], .2)

    def test_background_reload_uses_native_ticks_and_same_owned_stack(self):
        custody = source('GunCustody.java')
        self.assertIn('ItemStack gun = human.getMainHandItem();', custody)
        self.assertIn('gun = data.getInventoryItem(gunSlot);', custody)
        reload = source('TaczHumanReloadTransactions.java')
        self.assertIn('data.currentGunItem = () -> weapon', reload)
        self.assertIn('gun.tickReload(data, weapon, shooter)', reload)
        self.assertIn('getInventoryItem(slot) == weapon', reload)
        self.assertIn('!GunCustody.isCloseDefenseActive(human)', reload)
        self.assertIn('!sameGun(data, weapon)', reload)
        self.assertEqual(reload.count('HumanAmmo.consumeMagazines(human, weapon)'), 1)
        mixin = source('mixin/TaczHumanReloadMixin.java')
        self.assertIn('TaczAmmoMixinContract.TICK_RELOAD, at = @At("HEAD")', mixin)
        self.assertIn('prepareNativeTick(human, data)', mixin)

    def test_guarded_crossbow_uses_existing_native_ammo_path(self):
        charge = source('GuardedCrossbowCharge.java')
        self.assertIn('crossbow.releaseUsing(', charge)
        self.assertIn('CrossbowItem.getChargeDuration(weapon)', charge)
        self.assertIn('HumanArrowCombat.markNpcLoad(weapon)', charge)
        self.assertNotIn('startUsingItem(', charge.split('void tick(Human human)', 1)[0])
        self.assertNotIn('consumeArrow(', charge)

if __name__ == '__main__':
    unittest.main()
