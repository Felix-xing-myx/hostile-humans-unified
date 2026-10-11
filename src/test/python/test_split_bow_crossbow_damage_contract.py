"""Independent bow/crossbow defaults, config ownership and saved firing-origin contracts."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'
def source(name): return (PKG / (name + '.java')).read_text(encoding='utf-8')
class SplitBowCrossbowDamageContract(unittest.TestCase):
    def test_damage_uses_projectile_origin_not_current_weapon(self):
        damage = source('HumanDamageScaling')
        arrow = damage.split('instanceof AbstractArrow arrow)', 1)[1].split('} else {', 1)[0]
        self.assertIn('HumanArrowCombat.isCrossbowProjectile(arrow)', arrow)
        self.assertIn('.crossbowDamage()', arrow)
        self.assertIn('.bowDamage()', arrow)
        self.assertNotIn('getMainHandItem', arrow)
        self.assertLess(damage.index('instanceof ThrownTrident'), damage.index('instanceof AbstractArrow'))
        classifier = source('HumanArrowCombat').split('public static boolean isCrossbowProjectile(', 1)[1].split('/** Native loading', 1)[0]
        self.assertIn('arrow.shotFromCrossbow()', classifier)
        self.assertIn('HumanGunner.NPC_CROSSBOW_PROJECTILE', classifier)
        self.assertIn('SpartanRangedCompat.NPC_BOLT', classifier)
    def test_new_keys_are_independent_and_old_shared_key_is_retired(self):
        config = source('UnifiedConfig')
        for key in ('bow_damage_multiplier', 'crossbow_damage_multiplier'):
            self.assertIn('"' + key + '"', config)
        self.assertNotIn('"arrow_damage_multiplier"', config)
        cleanup = source('TierConfigProjection').split('List<String> retired = rank', 1)[1].split('for (String key : retired)', 1)[0]
        rank = cleanup.split(': List.of(', 1)[0]
        self.assertIn('"arrow_damage_multiplier"', rank)
        self.assertNotIn('"bow_damage_multiplier"', rank)
    def test_existing_files_receive_missing_new_controls_before_write(self):
        storage = source('SplitConfigFiles')
        self.assertIn('List.of("bow_damage_multiplier", "crossbow_damage_multiplier")', storage)
        self.assertLess(storage.index('if (!damage.has(key)) damage.add(key, template.get(key).deepCopy())'), storage.index('writeCurrent(path, original, arranged)'))
