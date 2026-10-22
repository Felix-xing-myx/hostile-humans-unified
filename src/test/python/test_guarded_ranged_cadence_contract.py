"""Wiring contracts for damage-independent bow/crossbow pacing (not a game simulation)."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'
GOALS = ROOT / 'src/main/java/com/craftix/hostile_humans/entity/ai/goal'
def source(name): return (PKG / (name + '.java')).read_text(encoding='utf-8')
class GuardedRangedCadenceContract(unittest.TestCase):
    def test_capture_retains_progress_and_does_not_double_tick(self):
        charge = source('GuardedRangedCharge')
        self.assertIn('progress = Math.max(progress, Math.min(1, (double) human.getTicksUsingItem() / duration(weapon)))', charge)
        self.assertIn('if (preparingCombatGuard || wantsHighPressureGuard()) guardedCharge.capture(human)', source('ShieldDefenseController'))
        self.assertIn('charging = HumanEquipmentExchange.bind(human, weapon)', charge)
        self.assertLess(charge.index('if (lastTick == human.tickCount) return'), charge.index('progress = Math.min(1, progress + foregroundRate(human, weapon) / (1.5 * duration(weapon)))'))
        self.assertIn('weapon.getItem() instanceof BowItem && !HumanArrowCombat.canDrawBow(human)', charge)
    def test_crossbow_resume_keeps_real_loaded_delay(self):
        goal = (GOALS / 'CrossbowGoal.java').read_text(encoding='utf-8')
        self.assertNotIn('this.attackDelay = 1;', goal)
        self.assertIn('HumanArrowCombat.crossbowWaitFinished(human, held)', goal)
        self.assertIn('HumanArrowCombat.crossbowLoaded(human, itemstack)', goal)
        self.assertIn('HumanArrowCombat.crossbowCompletedBehindGuard(human, weapon)', source('GuardedRangedCharge'))
        api = source('HumanArrowCombat')
        self.assertIn('cadence(human).hasLoadedCrossbow(weapon)) return;', api)
        # A completed 1.5x background draw is ready immediately; foreground retains its real loaded wait.
        self.assertIn('cadence(human).crossbowReadyNow(weapon, human.tickCount)', api)
        cadence = source('ArrowAttackCadence')
        self.assertIn('if (loadedCrossbow == weapon) return;', cadence)
        self.assertIn('crossbowFireAt = now + crossbowWaitTicks(now, delay, heavy)', cadence)
    def test_bow_interval_survives_goal_interruption(self):
        bow = (GOALS / 'BowAttack.java').read_text(encoding='utf-8')
        self.assertIn('if (!(mob instanceof Human)) this.attackTime = -1', bow)
        self.assertIn('HumanArrowCombat.canDrawBow(human)', bow)
        self.assertIn('HumanArrowCombat.configureBowInterval(human, p_25798_)', bow)
        self.assertIn('cadenceAllowsShot(human)', source('HumanArrowCombat'))
    def test_only_successful_projectiles_commit_cadence(self):
        api = source('HumanArrowCombat')
        volley = api.split('private static int executeVolley(', 1)[1].split('private record ArrowLayout', 1)[0]
        self.assertIn('if (human.level().addFreshEntity(arrow)) inserted++', volley)
        self.assertIn('arrows.stream().filter(net.minecraft.world.entity.Entity::isAddedToWorld).count()', volley)
        self.assertLess(volley.index('if (inserted > 0)'), volley.index('recordCrossbowShot(human)'))
        self.assertLess(volley.index('if (inserted > 0)'), volley.index('recordBowShot(human)'))
        self.assertIn('if (inserted == 0) payment.refund(human)', volley)
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        self.assertIn('HumanArrowCombat.fireVolley', human)
        self.assertIn('HumanArrowCombat.fireNativeCrossbow', human)
    def test_counter_window_uses_remaining_normalized_draw(self):
        counter = source('GuardedRangedCharge').split('int counterWindowTicks(', 1)[1].split('void tick(', 1)[0]
        self.assertIn('(1 - progress) * duration(weapon) / foregroundRate(human, weapon)', counter)
        self.assertIn('Math.max(32, remaining + 2)', counter)
        charge = source('GuardedRangedCharge')
        self.assertIn('if (charging != null) { reset(); return; }', charge)
        self.assertIn('HumanEquipmentExchange.locate(human, charging)', charge)
        self.assertIn('HumanInventorySession.isEditing(human)', charge)
