"""Owner damage entrypoint contracts; runtime effects need an authorized build/game test."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = ROOT / 'club/someoneice/humangunner'


class OwnerDamageProtectionContract(unittest.TestCase):
    def test_native_damage_is_rejected_before_combat_or_gear_mutation(self):
        source = (ROOT / 'com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        hurt = source.split('public boolean hurt(@NotNull DamageSource', 1)[1].split('public void knockback', 1)[0]
        self.assertLess(hurt.index('HumanOwnerProtection.blocksDamage'), hurt.index('this.lastCombatTime'))
        self.assertLess(hurt.index('HumanOwnerProtection.blocksDamage'), hurt.index('super.hurt'))

    def test_owner_check_is_directional_and_independent_of_pvp(self):
        source = (PKG / 'HumanOwnerProtection.java').read_text(encoding='utf-8')
        self.assertIn('target instanceof Human human && attacker instanceof Player player', source)
        self.assertIn('HumanRelations.isOwnedBy(human, player)', source)
        self.assertNotIn('allowHiredPvpDamage', source)
        self.assertNotIn('IdentityBadge', source)
        self.assertIn('source.getEntity()', source)
        self.assertIn('source.getDirectEntity() instanceof Projectile projectile', source)
        self.assertIn('projectile.getOwner()', source)

    def test_collision_and_explosion_share_the_same_owner_rule(self):
        source = (PKG / 'HumanProjectileRelations.java').read_text(encoding='utf-8')
        collision = source.split('public static boolean passesThrough', 1)[1]
        self.assertLess(collision.index('HumanOwnerProtection.protects'), collision.index('instanceof Human shooter'))
        self.assertIn('getIndirectSourceEntity()', source)
        self.assertIn('removeIf(candidate -> HumanOwnerProtection.protects(candidate, attacker)', source)
        self.assertIn('HumanOwnerProtection.blocksDamage(human, event.getSource())', source)

    def test_tacz_cancels_before_pressure_shield_or_badge_updates(self):
        source = (PKG / 'TaczIntegration.java').read_text(encoding='utf-8')
        pre = source.split('private static void onGunDamagePre', 1)[1].split('private static void onGunDamagePost', 1)[0]
        owner = pre.split('if (event.getHurtEntity() instanceof Human defender', 1)[0]
        self.assertIn('HumanOwnerProtection.protects(event.getHurtEntity(), event.getAttacker())', owner)
        self.assertIn('event.setCanceled(true)', owner)
        self.assertIn('return;', owner)

    def test_player_attack_event_is_registered(self):
        source = (PKG / 'HumanGunner.java').read_text(encoding='utf-8')
        self.assertIn('EventPriority.HIGHEST, HumanOwnerProtection::onPlayerAttack', source)
        policy = (PKG / 'HumanRelationshipPolicy.java').read_text(encoding='utf-8')
        self.assertIn('hired && owner != null && owner.equals(player)', policy)
