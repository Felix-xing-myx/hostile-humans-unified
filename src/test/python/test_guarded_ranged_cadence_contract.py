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
        self.assertIn('progress = Math.max(progress, human.getTicksUsingItem())', charge)
        self.assertIn('if (preparingCombatGuard || wantsHighPressureGuard()) guardedCharge.capture(human)', source('ShieldDefenseController'))
        self.assertIn('lastTick = human.tickCount; // The active weapon already advanced', charge)
        self.assertLess(charge.index('if (lastTick == human.tickCount) return'), charge.index('progress = Math.min(20, progress + 3)'))
        self.assertIn('if (!HumanArrowCombat.canDrawBow(human)) return', charge)
    def test_crossbow_resume_keeps_real_loaded_delay(self):
        goal = (GOALS / 'CrossbowGoal.java').read_text(encoding='utf-8')
        self.assertNotIn('this.attackDelay = 1;', goal)
        self.assertIn('HumanArrowCombat.crossbowWaitFinished(human, held)', goal)
        self.assertIn('HumanArrowCombat.crossbowLoaded(human, itemstack)', goal)
        self.assertIn('HumanArrowCombat.crossbowLoaded(human, weapon)', source('GuardedRangedCharge'))
        api = source('HumanArrowCombat')
        self.assertIn('cadence(human).hasLoadedCrossbow(weapon)) return;', api)
    def test_bow_interval_survives_goal_interruption(self):
        bow = (GOALS / 'BowAttack.java').read_text(encoding='utf-8')
        self.assertIn('if (!(mob instanceof Human)) this.attackTime = -1', bow)
        self.assertIn('HumanArrowCombat.canDrawBow(human)', bow)
        self.assertIn('HumanArrowCombat.configureBowInterval(human, p_25798_)', bow)
        self.assertIn('cadenceAllowsShot(human)', source('HumanArrowCombat'))
    def test_only_successful_projectiles_commit_cadence(self):
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        bow = human.split('this.performCrossbowAttack((LivingEntity)this, 1.6f)', 1)[1].split('public void performRangedAttackTrident', 1)[0]
        self.assertLess(bow.index('if (!this.level().addFreshEntity'), bow.index('HumanArrowCombat.recordBowShot'))
        self.assertIn('if (volley.stream().anyMatch(Projectile::isAddedToWorld)) {\n                club.someoneice.humangunner.HumanArrowCombat.recordCrossbowShot(this)', human)
        heavy = source('SpartanRangedCompat')
        self.assertLess(heavy.index('if (!human.level().addFreshEntity'), heavy.index('HumanArrowCombat.recordCrossbowShot'))
    def test_counter_window_fits_wait_instead_of_skipping_it(self):
        counter = source('GuardedRangedCharge').split('int counterWindowTicks(', 1)[1].split('void tick(', 1)[0]
        self.assertIn('ArrowAttackCadence.crossbowWaitTicks', counter)
        self.assertIn('loadingTicks + loadedWait + 2', counter)
