"""Static integration contracts. These do not execute Forge events or prove live behavior."""
from pathlib import Path
import unittest
import re

JAVA = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = JAVA / 'club/someoneice/humangunner'


class ReviewStateBoundaries(unittest.TestCase):
    def test_legacy_commands_cannot_bypass_current_orders(self):
        network = (JAVA / 'com/craftix/hostile_humans/network/NetworkHandler.java').read_text(encoding='utf-8')
        self.assertNotIn('MessageCommandHuman', network)
        self.assertFalse((JAVA / 'com/craftix/hostile_humans/network/message/MessageCommandHuman.java').exists())
        order = (PKG / 'SoldierOrder.java').read_text(encoding='utf-8')
        for name in ('initialize', 'set'):
            match = re.search(r'static void ' + name + (r'\(Human \w+, SoldierOrder \w+, boolean \w+\)\s*\{' if name == 'set' else r'\(Human \w+\)\s*\{'), order)
            self.assertIsNotNone(match)
            start = match.end(); depth = 1; end = start
            while depth:
                depth += (order[end] == '{') - (order[end] == '}'); end += 1
            method = order[start:end]
            self.assertIn('setOrderedToSit(false)', method)
            self.assertIn('setInSittingPose(false)', method)
        startup = (PKG / 'HumanGunner.java').read_text(encoding='utf-8')
        self.assertIn('if (human.hasOwner()) SoldierOrder.initialize(human)', startup)

    def test_recovery_reservations_protect_automatic_weapon_selectors(self):
        for name in ('AmmoWeaponSelection', 'GunCustody', 'RangedWeaponCustody', 'HumanLootManager', 'HumanInventoryCustody', 'HumanManagedLoadout'):
            source = (PKG / (name + '.java')).read_text(encoding='utf-8')
            self.assertIn('isReservedForRecovery(data,', source, name)
        recovery = (PKG / 'RecoverySupplies.java').read_text(encoding='utf-8')
        # Recovery no longer reserves a fixed backpack slot: its real item moves once through exchange.
        self.assertIn('static boolean reservesInventorySlot(Human human, int slot) { return false; }', recovery)
        begin = recovery.split('private static UseSession beginInventoryUse(', 1)[1].split('public static UseSession continueFoodUse', 1)[0]
        self.assertIn('candidate -> candidate == actual', begin)
        self.assertIn('HumanEquipmentExchange.request', begin)
        self.assertIn('EnumSet.of(ActionCoordinator.Resource.OFF_HAND)', begin)
        self.assertIn('if (!parallelFood) resources.add(ActionCoordinator.Resource.USE_ITEM)', begin)
        settlement = recovery.split('public static void finishUse(', 1)[1].split('static boolean hasRecoverableShield', 1)[0]
        self.assertNotIn('setItemInHand', settlement)
        self.assertNotIn('.copy()', settlement)
        self.assertLess(settlement.index('ACTIVE_USES.remove(human, session)'), settlement.index('human.stopUsingItem()'))

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
