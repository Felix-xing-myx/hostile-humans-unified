"""Final damage bonus has one server entry and requires actual pressure-owned shield execution."""
from source_paths import SourcePath as Path
import unittest
PKG=Path(__file__).resolve().parents[3]/'src/main/java/dev/felix/hostilehumans'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
class PressureGuardMitigationContract(unittest.TestCase):
    def test_only_owned_actual_pressure_execution_is_eligible(self):
        code=source('ShieldDefenseController').split('public double pressureDamageMultiplier()',1)[1].split('public double defenseSpeedFactor()',1)[0]
        for boundary in ('owner == Owner.PRESSURE', 'ownsCurrentShield()', '!human.isNoAi()', '!human.isSleeping()', '!HumanInventorySession.isEditing', '!NavigationDebugService.controls'):
            self.assertIn(boundary,code)
        self.assertNotIn('wantsHighPressureGuard()',code)
        self.assertNotIn('getNavigation()',code)
    def test_bonus_is_once_at_final_damage_without_source_filter(self):
        code=source('HumanRuntime')
        self.assertEqual(code.count('.pressureDamageMultiplier()'),1)
        hurt=code.split('private static void onLivingHurt(',1)[1].split('private static void onLivingDamage(',1)[0]
        self.assertNotIn('pressureDamageMultiplier',hurt)
        final=code.split('private static void onLivingDamage(',1)[1].split('if (event.getAmount() > 0.0F &&',1)[0]
        self.assertIn('!defender.level().isClientSide',final)
        self.assertIn('event.setAmount(event.getAmount() *',final)
        self.assertNotIn('getSource()',final)
        self.assertNotIn('.hurt(',final)
    def test_default_and_runtime_share_the_configured_coefficient(self):
        import json
        # Root is explicit to avoid relying on runtime configuration or an instance's custom files.
        root=Path(__file__).resolve().parents[3]
        data=json.loads((root/'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertEqual(data['shield_pressure']['damage_reduction'],.2)
        self.assertIn('"damage_reduction", .20, 0, 1',source('ShieldPressureConfig'))
        self.assertIn('enabled && executing ? 1 - damageReduction : 1',source('ShieldPressureConfig'))
if __name__=='__main__':unittest.main()
