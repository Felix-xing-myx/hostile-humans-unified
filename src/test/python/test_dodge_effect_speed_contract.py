"""Real incoming protection differs from targeting relations; effects compose after the human cap."""
from source_paths import SourcePath as Path
import unittest
ROOT=Path(__file__).resolve().parents[3]/'src/main/java/dev/felix/hostilehumans'
def code(name):return (ROOT/(name+'.java')).read_text(encoding='utf-8')
class DodgeEffectSpeedContract(unittest.TestCase):
    def test_neutral_players_are_not_mistaken_for_damage_immune_allies(self):
        dodge=code('DodgeAbility')
        self.assertNotIn('HumanRelations.allied(h, attacker)',dodge)
        self.assertIn('HumanOwnerProtection.blocksDamage(h, source)',dodge)
        self.assertIn('HumanRuntime.shouldBlockHumanDamage(other, h)',dodge)
    def test_dodge_reuses_existing_physical_damage_compatibility(self):
        self.assertIn('HumanDamagePipeline.physical(source)',code('DodgeAbility'))
        self.assertIn('nativeBullet(source)',code('HumanDamagePipeline'))
    def test_observer_does_not_run_randomness_or_change_ability(self):
        ability=code('DodgeAbility')
        observe=ability.split('String describe(Human h)',1)[1].split('DodgeAbility(JsonObject',1)[0]
        self.assertNotIn('getRandom',observe)
        self.assertNotIn('computeIfAbsent',observe)
        self.assertIn('FactionAbilities.describe(h)',code('HumanPerception'))
    def test_effects_come_from_real_attribute_contract_not_item_names(self):
        speed=code('MovementSpeedController')
        self.assertIn('human.getActiveEffects()',speed)
        self.assertIn('getAttributeModifiers().get(Attributes.MOVEMENT_SPEED)',speed)
        self.assertIn('MovementSpeedLimitPolicy.project',speed)
        self.assertNotIn('Items.POTION',speed)
        self.assertIn('Math.max(0, wanted) / raw - 1',speed)
if __name__=='__main__':unittest.main()
