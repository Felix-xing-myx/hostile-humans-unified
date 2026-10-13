"""Fixed-speed solver integration; numerical JVM cases live in CombatPressurePolicyTest."""
from pathlib import Path
import unittest
import os
import subprocess
import json

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/club/someoneice/humangunner"


class ThrowableLaunchSpeedContract(unittest.TestCase):
    def test_native_entity_receives_solved_velocity_without_rescaling(self):
        source = (JAVA / "ScgunsThrowables.java").read_text(encoding="utf-8")
        self.assertIn("ThrowableBallistics.solve(delta.x, delta.y, delta.z, gravity, speed, horizon)", source)
        self.assertIn("projectile.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0)", source)
        self.assertIn("!safeArea(human, plan.impact(), safety)", source)
        self.assertNotIn("velocity.lengthSqr() <= speed * speed", source)

    def test_solver_uses_fractional_native_movement_and_bounded_work(self):
        source = (JAVA / "ThrowableBallistics.java").read_text(encoding="utf-8")
        self.assertIn("iteration < 36", source)
        self.assertIn("(ticks - whole) * drag", source)
        self.assertIn("double low = tick - 1, high = tick", source)
        self.assertNotIn("net.minecraft", source)
        self.assertNotIn("top.ribs", source)

    def test_numerical_cases_check_speed_and_actual_discrete_endpoint(self):
        source = (ROOT / "src/test/java/club/someoneice/humangunner/CombatPressurePolicyTest.java").read_text(encoding="utf-8")
        self.assertIn("checkThrowableSpeed();", source)
        self.assertIn("Math.abs(actual - speed) < 1.0E-8", source)
        self.assertIn("vy = vy * ThrowableBallistics.DRAG - gravity", source)
        self.assertIn("x += vx * fraction; y += vy * fraction", source)

    def test_constructor_gravity_and_effect_profiles(self):
        source = (JAVA / "ScgunsThrowableAdapter.java").read_text(encoding="utf-8")
        self.assertIn('GasGrenadeItem) return new FlightProfile((double) .05F, true, 21)', source)
        self.assertIn('ChokeBombItem) return new FlightProfile((double) .05F, true, 41)', source)
        self.assertIn('GrenadeItem) return new FlightProfile((double) .05F, true, fuse)', source)
        self.assertIn('BeaconGrenadeItem) return new FlightProfile((double) .025F, true, fuse)', source)
        self.assertIn('ThrowableShotballItem) return new FlightProfile((double) .04F, false, fuse)', source)
        self.assertIn('return new FlightProfile((double) .05F, false, fuse)', source)
        self.assertNotIn('return new FlightProfile((double) .03F', source)

    def test_cluster_budget_and_safety_are_preserved(self):
        source = (JAVA / "ScgunsThrowables.java").read_text(encoding="utf-8")
        self.assertIn('candidates.size() >= 16', source)
        self.assertLess(source.index('if (!hasThrowable) return'), source.index('ClusterAim cluster ='))
        self.assertIn('lead(targetMotion, horizon)', source)
        self.assertIn('offset.lengthSqr() > 144', source)
        self.assertIn('SoldierOrder.allowsTarget(human, entity)', source)
        self.assertIn('!HumanRelations.allied(human, entity)', source)
        self.assertIn('Math.min(settings.throwCooldownTicks(), settings.clusterCooldownTicks())', source)
        self.assertIn('!safeArea(human, plan.impact(), safety)', source)
        self.assertNotIn('human.setTarget(', source)
        self.assertIn('HumanTargeting.isAutonomousPlayerEnemy(human, entity)', source)
        self.assertIn('SoldierCombatMode.isExplicitlyAuthorizedAgainst(human, entity)', source)
        self.assertIn('human.getBbWidth() * .5 + .2', source)
        self.assertIn('ClipContext(eye, origin', source)

    def test_distance_speed_and_cluster_controls_are_exposed(self):
        config = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))['scguns']['throwables']
        expected = dict(maximum_range=32, initial_speed=2, cluster_radius=4,
                        cluster_minimum_targets=3, cluster_cooldown_ticks=80, cooldown_ticks=160, quality=170)
        parser = (JAVA / 'ScgunsSettings.java').read_text(encoding='utf-8')
        loot = (JAVA / 'HumanLootManager.java').read_text(encoding='utf-8')
        self.assertIn('if (TacticalThrowables.supported(stack)) return UnifiedConfig.get().scguns().throwableQuality();', loot)
        for name, value in expected.items():
            self.assertEqual(config[name], value)
            self.assertIn('"' + name + '"', parser)

    @unittest.skipUnless(os.environ.get('SCGUNS_API_JAR'), 'requires installed native dependency')
    def test_actual_native_constructor_profiles(self):
        jar = os.environ['SCGUNS_API_JAR']
        def native(name):
            return subprocess.check_output([r'D:\Java\17\bin\javap.exe', '-classpath', jar, '-p', '-c',
                    'top.ribs.scguns.entity.throwable.' + name], text=True, encoding='utf-8', errors='replace')
        base = native('ThrowableGrenadeEntity')
        self.assertIn('float 0.05f', base)
        self.assertIn('Method setGravityVelocity:(F)V', base)
        for name in ('ThrowableGasGrenadeEntity', 'ThrowableChokeBombEntity', 'ThrowableNailBombEntity',
                     'ThrowableSwarmBombEntity', 'ThrowableMolotovCocktailEntity', 'ThrowableHellfireBombEntity'):
            child = native(name)
            self.assertIn('extends top.ribs.scguns.entity.throwable.ThrowableGrenadeEntity', child)
            self.assertIn('Method top/ribs/scguns/entity/throwable/ThrowableGrenadeEntity."<init>"', child)
            self.assertNotIn('Method setGravityVelocity:(F)V', child)
        for name, gravity in (('ThrowableBeaconGrenadeEntity', '0.025'), ('ThrowableShotballEntity', '0.04')):
            child = native(name)
            self.assertIn('float ' + gravity + 'f', child)
            self.assertIn('Method setGravityVelocity:(F)V', child)


if __name__ == "__main__":
    unittest.main()
