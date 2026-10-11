"""Core targeting isolation and ownership contracts; actual AI requires game acceptance."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'


class HostileTargetingContract(unittest.TestCase):
    def test_core_hook_has_no_optional_mod_dependency(self):
        code = (PKG / 'HostileHumanTargeting.java').read_text(encoding='utf-8')
        for optional in ('top.ribs', 'com.tacz', 'ModList', 'ScgunsCompat', 'GunSupport'):
            self.assertNotIn(optional, code)
        main = (PKG / 'HumanGunner.java').read_text(encoding='utf-8')
        start = main.index('private static void onEntityJoin(')
        end = main.index('// Internal launchers', start)
        self.assertIn('HostileHumanTargeting.onJoin(mob)', main[start:end])
        self.assertIn('event.getLevel().isClientSide', main[start:end])

    def test_native_goal_and_existing_target_ownership(self):
        code = (PKG / 'HostileHumanTargeting.java').read_text(encoding='utf-8')
        self.assertIn('extends NearestAttackableTargetGoal<Human>', code)
        self.assertIn('mob.getTarget() == null || !mob.getTarget().isAlive()', code)
        self.assertIn('Math.min(32, super.getFollowDistance())', code)
        for forbidden in ('setTarget(', '.hurt(', 'getNavigation()', 'setPos(', 'getEntitiesOfClass('):
            self.assertNotIn(forbidden, code)

    def test_relationship_and_duplicate_guards(self):
        code = (PKG / 'HostileHumanTargeting.java').read_text(encoding='utf-8')
        for guard in ('mob instanceof Human', 'mob instanceof OwnableEntity', 'mob instanceof NeutralMob',
                      'attacker.isAlliedTo(target)', 'target.isAlliedTo(attacker)', 'attacker.canAttack(target)',
                      'getAvailableGoals()', 'instanceof HumanTargetGoal', 'mob.isNoAi()'):
            self.assertIn(guard, code)
        self.assertLess(code.index('config.excludedEntities()'), code.index('config.includedEntities()'))
        self.assertNotIn('getPersistentData()', code)

    def test_global_defaults_and_restart_scoped_config(self):
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        config = defaults['hostile_mob_targeting']
        self.assertTrue(config['enabled'])
        self.assertEqual(config['included_entities'], [])
        self.assertEqual(config['excluded_entities'], [])
        projection = (PKG / 'TierConfigProjection.java').read_text(encoding='utf-8')
        self.assertEqual(projection.count('"hostile_mob_targeting"'), 1)
        unified = (PKG / 'UnifiedConfig.java').read_text(encoding='utf-8')
        self.assertIn('private final HostileTargetingConfig hostileTargeting;', unified)


if __name__ == '__main__':
    unittest.main()
