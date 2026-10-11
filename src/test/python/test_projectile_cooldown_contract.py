"""Source wiring checks only; not a Java, Mixin startup or gameplay test."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/club/someoneice/humangunner"


class ProjectileCooldownContractTest(unittest.TestCase):
    def test_one_registered_cooldown_mixin_and_narrow_slice(self):
        config = json.loads((ROOT / "src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        self.assertEqual(config["mixins"].count("LivingEntityProjectileCooldownMixin"), 1)
        mixin = (JAVA / "mixin/LivingEntityProjectileCooldownMixin.java").read_text(encoding="utf-8")
        self.assertIn('m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z', mixin)
        self.assertIn('DamageTypeTags;f_273918_:Lnet/minecraft/tags/TagKey;', mixin)
        self.assertIn('LivingEntity;f_20898_:F", ordinal = 0', mixin)
        self.assertIn('DamageSource;m_269533_(Lnet/minecraft/tags/TagKey;)Z', mixin)
        self.assertIn('remap = false, require = 1, allow = 1', mixin)
        self.assertIn('return source.is(tag) || HumanAttackCooldownPolicy.bypassesCooldown', mixin)

    def test_server_vanilla_player_and_explosion_guards(self):
        policy = (JAVA / "ProjectileCooldownPolicy.java").read_text(encoding="utf-8")
        for guard in ('victim.level().isClientSide', 'UnifiedConfig.get().vanillaMode()',
                      'victim instanceof Player', 'source.is(DamageTypeTags.IS_EXPLOSION)'):
            self.assertIn(guard, policy)
        self.assertIn('source.is(DamageTypeTags.IS_PROJECTILE) || GunSupport.get().isBullet(source)', policy)
        self.assertIn('if (!humanVictim && !humanShooter) return false;', policy)
        self.assertIn('attacker == null && source.getDirectEntity() instanceof Projectile', policy)
        self.assertNotIn('com.tacz', policy)

    def test_original_damage_and_shared_timer_are_not_mutated(self):
        for name in ('HumanAttackCooldownPolicy.java', 'ProjectileCooldownPolicy.java', 'mixin/LivingEntityProjectileCooldownMixin.java'):
            source = (JAVA / name).read_text(encoding="utf-8")
            for mutation in ('.invulnerableTime', '.hurtTime', '.setHealth(', '.hurt(', 'new DamageSource('):
                self.assertNotIn(mutation, source)

    def test_java_scope_and_real_bytecode_contract_is_registered(self):
        build = (ROOT / 'build.gradle').read_text(encoding='utf-8')
        self.assertIn("'ProjectileCooldownPolicyTest'", build)
        regression = (ROOT / 'src/test/java/club/someoneice/humangunner/ProjectileCooldownPolicyTest.java').read_text(encoding='utf-8')
        self.assertIn('queries == 1', regression)
        self.assertIn('"net/minecraft/world/entity/LivingEntity"', regression)


if __name__ == '__main__':
    unittest.main()
