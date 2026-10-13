"""Static integration contracts. These do not execute Forge events or prove live behavior."""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = JAVA / 'club/someoneice/humangunner'


class ReviewStateBoundaries(unittest.TestCase):
    def test_legacy_commands_cannot_bypass_current_orders(self):
        network = (JAVA / 'com/craftix/hostile_humans/network/NetworkHandler.java').read_text(encoding='utf-8')
        self.assertNotIn('MessageCommandHuman', network)
        self.assertFalse((JAVA / 'com/craftix/hostile_humans/network/message/MessageCommandHuman.java').exists())
        order = (PKG / 'SoldierOrder.java').read_text(encoding='utf-8')
        for signature in ('static void initialize(Human human)', 'static void set(Human human, SoldierOrder order, boolean preserveNavigation)'):
            method = order.split(signature, 1)[1].split('\n    }', 1)[0]
            self.assertIn('setOrderedToSit(false)', method)
            self.assertIn('setInSittingPose(false)', method)
        startup = (PKG / 'HumanGunner.java').read_text(encoding='utf-8')
        self.assertIn('if (human.hasOwner()) SoldierOrder.initialize(human)', startup)

    def test_recovery_reservations_protect_automatic_weapon_selectors(self):
        for name in ('AmmoWeaponSelection', 'GunCustody', 'RangedWeaponCustody', 'HumanLootManager', 'HumanInventoryCustody', 'HumanManagedLoadout'):
            source = (PKG / (name + '.java')).read_text(encoding='utf-8')
            self.assertIn('isReservedForRecovery(data,', source, name)
        recovery = (PKG / 'RecoverySupplies.java').read_text(encoding='utf-8')
        reservation = recovery.split('static boolean reservesInventorySlot(', 1)[1].split('\n    }', 1)[0]
        self.assertIn('ACTIVE_USES.get(human)', reservation)
        self.assertIn('PENDING_FINISHES.get(human)', reservation)
        settlement = recovery.split('private static void settleExchange(', 1)[1].split('private static void rollbackExchange(', 1)[0]
        self.assertNotIn('session.displacedHandItem().copy()', settlement)
        self.assertIn('if (!stowCurrentHand(human, session.hand())) return', settlement)

    def test_combat_detours_share_order_path_checks(self):
        for name in ('CombatTerrainNavigation', 'WaterCombatNavigation', 'ShieldDefenseMovement', 'RangedHybridMeleeGoal', 'CloseRangedTactics', 'RangedFiringPosition'):
            source = (PKG / (name + '.java')).read_text(encoding='utf-8')
            self.assertIn('SoldierOrder.allowsCombatPath(human,', source, name)
        for name in ('WaterCombatNavigation', 'CloseDefenseHold'):
            source = (PKG / (name + '.java')).read_text(encoding='utf-8')
            self.assertNotIn('humanGunner$canMeleeAttack()', source)
            self.assertIn('!GunSupport.isGunItem(human.getMainHandItem())', source)

    def test_animation_uses_atomic_server_timeline_and_playable_fallback(self):
        metadata = (JAVA / 'com/craftix/hostile_humans/entity/HumanMobEntityData.java').read_text(encoding='utf-8')
        self.assertIn('DATA_BETTER_COMBAT_ATTACK_VISUAL', metadata)
        self.assertNotIn('DATA_BETTER_COMBAT_ATTACK_ANIMATION_SEQUENCE', metadata)
        self.assertIn('snapshot.putLong("started_at", this.level().getGameTime())', metadata)
        animator = (JAVA / 'com/craftix/hostile_humans/client/renderer/BetterCombatNpcAnimator.java').read_text(encoding='utf-8')
        self.assertIn('if (!visual.active(now))', animator)
        self.assertIn('int elapsed = visual.elapsed(now)', animator)
        seek = animator.split('private static void advancePlayback(', 1)[1].split('/** The reference', 1)[0]
        self.assertLess(seek.index('resolved.setTickDelta.invoke'), seek.index('resolved.stackTick.invoke'))
        self.assertIn('advancePlayback(playback, human, elapsedTicks)', animator)
        self.assertIn('advancePlayback(playback, human, Math.min(elapsedTicks, 20))', animator)
        self.assertIn('playbackWindowTicks(duration)', metadata)
        self.assertIn('return human.swinging && hasActiveCustomAnimation(human)', animator)
        renderer = (JAVA / 'com/craftix/hostile_humans/client/renderer/ClientRenderer.java').read_text(encoding='utf-8')
        self.assertIn('BetterCombatNpcAnimator.resetPlayback()', renderer)
