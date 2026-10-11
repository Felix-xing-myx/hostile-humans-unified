"""Melee cooldown scope and integration contracts, not Java/Mixin gameplay tests."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'
def read(name): return (PKG / (name + '.java')).read_text(encoding='utf-8')
class MeleeCooldownContract(unittest.TestCase):
    def test_only_actual_human_melee_qualifies(self):
        policy = read('HumanAttackCooldownPolicy')
        self.assertIn('source.getEntity() instanceof Human', policy)
        self.assertIn('source.getDirectEntity() == source.getEntity()', policy)
        self.assertIn('source.is(DamageTypes.MOB_ATTACK)', policy)
        self.assertIn('source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)', policy)
        self.assertIn('&& !projectile && !explosion', policy)
        self.assertIn('victim.level().isClientSide || UnifiedConfig.get().vanillaMode()', policy)
        self.assertIn('return ProjectileCooldownPolicy.bypassesCooldown(victim, source)', policy)
    def test_one_existing_hook_and_no_attack_clock_changes(self):
        hook = read('mixin/LivingEntityProjectileCooldownMixin')
        self.assertIn('source.is(tag) || HumanAttackCooldownPolicy.bypassesCooldown', hook)
        self.assertEqual(hook.count('@Redirect('), 1)
        policy = read('HumanAttackCooldownPolicy')
        for unrelated in ('invulnerableTime', 'hurtTime', 'MeleeAttackTiming', 'advanceUseTime', 'setHealth(', 'doHurtTarget('):
            self.assertNotIn(unrelated, policy)
        # Player target exclusion stays in the projectile branch only.
        self.assertNotIn('victim instanceof Player', policy)
        self.assertIn('victim instanceof Player', read('ProjectileCooldownPolicy'))
    def test_creeper_forecast_follows_cooldown_policy(self):
        forecast = read('CreeperMeleeCommitment')
        self.assertIn('HumanAttackCooldownPolicy.bypassesCooldown(creeper, human.damageSources().mobAttack(human))', forecast)
        self.assertIn('if (!bypassesCooldown) firstImpact', forecast)
        self.assertIn('bypassesCooldown ? hitIntervalTicks : Math.max(10, hitIntervalTicks)', read('CreeperFinishPolicy'))
