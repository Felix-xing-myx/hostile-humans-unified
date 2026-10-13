"""Defaults and installed-backend damage contracts; no compilation or game launch."""
import json
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]


class ThrowableDamageTests(unittest.TestCase):
    def test_defaults_hit_requested_damage_without_changing_secondary_effects(self):
        config = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        factors = config['scguns']['throwables']['damage_multipliers']
        self.assertAlmostEqual(19 * factors['scguns:grenade'], 32)
        self.assertAlmostEqual(14 * factors['scguns:nail_bomb'], 24)
        for item in ('molotov_cocktail', 'hellfire_bomb', 'shotball'):
            self.assertEqual(factors['scguns:' + item], 1)
        for item in ('gas_grenade', 'swarm_bomb', 'choke_bomb', 'beacon_grenade'):
            self.assertNotIn('scguns:' + item, factors)

    @unittest.skipUnless(os.environ.get('SCGUNS_API_JAR'), 'installed backend required')
    def test_native_direct_damage_inputs_and_owner_api(self):
        def native(name):
            return subprocess.check_output([r'D:\Java\17\bin\javap.exe', '-classpath', os.environ['SCGUNS_API_JAR'],
                '-c', '-p', 'top.ribs.scguns.entity.throwable.' + name], text=True, encoding='utf-8', errors='replace')
        grenade = native('ThrowableGrenadeEntity')
        self.assertIn('float 19.0f', grenade)
        self.assertIn('createRocketExplosion:', grenade)
        nail = native('ThrowableNailBombEntity')
        self.assertIn('float 14.0f', nail)
        self.assertIn('ThrowableItemEntity.m_19749_', nail)  # native Projectile#getOwner
        self.assertIn('DamageSources.m_269036_', nail)  # owner-bearing damage source
        base = native('ThrowableItemEntity')
        self.assertIn('public net.minecraft.world.item.ItemStack getItem();', base)
        for name in ('ThrowableMolotovCocktailEntity', 'ThrowableHellfireBombEntity'):
            self.assertIn('Explosion:', native(name))
        self.assertIn('DamageSources.m_269390_', native('ThrowableShotballEntity'))


if __name__ == '__main__':
    unittest.main()
