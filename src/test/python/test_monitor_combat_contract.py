"""Manual intent must reuse server weapon execution, without coupling combat to the monitor UI."""
from pathlib import Path
import unittest
import json

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/dev/felix/hostilehumans'
def code(path): return (PKG / (path + '.java')).read_text(encoding='utf-8')

class MonitorCombatContract(unittest.TestCase):
    def test_public_combat_has_no_monitor_session_dependency(self):
        for path in ['behavior/combat/gun/GunFireAuthorization', 'behavior/combat/melee/MeleeActionControl',
                     'behavior/combat/melee/MeleeCombatRange', 'behavior/combat/ranged/HumanArrowCombat',
                     'compat/tacz/TaczHumanReloadTransactions', 'entity/Human']:
            self.assertNotIn('monitor.combat.MonitorCombatSession', code(path))
        authority = code('behavior/combat/HumanCombatAuthority')
        self.assertNotIn('monitor.', authority)
        self.assertIn('controller.active()', authority)
        self.assertIn('CONTROLLERS.remove(human, controller)', authority)
        self.assertIn('HumanPerception.canTarget(human, target)', authority)

    def test_lease_owns_and_releases_authority(self):
        control = code('monitor/HumanMonitorControl')
        self.assertIn('HumanCombatAuthority.bind(human, lease.combat)', control)
        self.assertIn('HumanCombatAuthority.release(human, entry.getValue().combat)', control)
        self.assertIn('lease.active && lease.inputs.fresh', control)
        session = code('monitor/combat/MonitorCombatSession')
        self.assertIn('inputGate.accept(true, attack || supply, use, reload)', session)
        self.assertIn('inputGate.reset()', session)
        self.assertIn('human.hhu$cancelMeleeAttackForDefense()', session)
        self.assertIn('HumanArrowCombat.clear(human)', session)
        self.assertIn('human.doHurtTarget(target)', session)
        self.assertNotIn('.hurt(', session)
        self.assertIn('ProjectileUtil.getEntityHitResult', session)

    def test_native_gun_path_keeps_paid_authoritative_execution(self):
        gun = code('compat/tacz/TaczMonitorWeapons')
        for seam in ['GunAttackAction.run', 'GunFireAuthorization.allows', 'session.reloadDispatch(operator::reload)',
                     'operator.shoot(', 'ShootResult.SUCCESS',
                     'trigger.accepted(continuous)', 'TaczHumanReloadTransactions.end']:
            self.assertIn(seam, gun)
        self.assertNotIn('handleShootResult', gun)  # AI recovery must not auto-reload or override manual intent.
        self.assertNotIn('ClientShoot', gun)

    def test_autonomous_pressure_does_not_veto_manual_melee(self):
        shield = code('behavior/combat/defense/ShieldDefenseController')
        available = shield.split('private boolean pressureDefenseAvailable()', 1)[1].split('public boolean wantsHighPressureGuard()', 1)[0]
        self.assertIn('!dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.bound(human)', available)
        # Disable at the policy admission point: ordinary melee and release gates keep sharing the same rule.
        self.assertIn('if (!pressureDefenseAvailable())', shield)
        self.assertIn('return shouldSuppressRecovery() || human.isUsingItem()', shield)

    def test_arrows_keep_payment_and_native_heavy_bolt_boundary(self):
        arrows = code('monitor/combat/MonitorArrowActions')
        for seam in ['HumanArrowCombat.fireVolley', 'HumanArrowCombat.fireNativeCrossbow',
                     'SpartanRangedCompat.fire(human, null)', 'SpartanRangedCompat.markChargedForNpc',
                     'human.setChargingCrossbow(false)']:
            self.assertIn(seam, arrows)
        self.assertNotIn('addFreshEntity', arrows)
        self.assertNotIn('.shrink(', arrows)
        combat = code('behavior/combat/ranged/HumanArrowCombat')
        self.assertIn('HumanCombatAuthority.arrowIntent(human, weapon, followup)', combat)

    def test_real_player_input_isolated_and_custom_bindings_preserved(self):
        client = code('monitor/client/HumanMonitorClient')
        interaction = client.split('public static void interaction(', 1)[1].split('public static void queueWeaponInput', 1)[0]
        self.assertIn('MonitorGunInput.nativeBindings', interaction)
        self.assertIn('event.setCanceled(true)', interaction)
        self.assertIn('GLFW_KEY_F6', client)
        mixins = json.loads((ROOT/'src/main/resources/hostile_humans_unified.mixins.json').read_text())
        for name in ['TaczMonitorShootInputMixin', 'TaczMonitorAimInputMixin', 'TaczMonitorReloadInputMixin']:
            self.assertIn(name, mixins['client'])
            self.assertIn('HumanMonitorClient.active()', code('mixin/'+name))

if __name__ == '__main__': unittest.main()
