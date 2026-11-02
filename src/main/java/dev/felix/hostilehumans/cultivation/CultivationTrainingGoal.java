package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Uses the existing Goal execution bridge and native path ownership. Threat interruption restores
 * ordinary AI.
 */
final class CultivationTrainingGoal extends Goal {
    private final Human human;
    private int repath, failed;

    CultivationTrainingGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return CultivationService.holds(human);
    }

    @Override
    public boolean canContinueToUse() {
        return CultivationService.holds(human);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        repath = failed = 0;
        human.stopUsingItem();
    }

    @Override
    public void tick() {
        var record = CultivationService.record(human);
        if (record == null) return;
        var target = CultivationService.safeDestination(human, record);
        if (target == null) {
            CultivationService.interrupt(human, "No safe loaded training floor", false);
            return;
        }
        if (human.position().distanceToSqr(target) <= .8 * .8) {
            human.getNavigation().stop();
            CultivationService.arrived(human);
            return;
        }
        if (record.cycle.status() == CultivationCycle.Status.ACTIVE) {
            CultivationService.interrupt(human, "Left training seat", false);
            return;
        }
        if (repath-- <= 0) {
            repath = 20;
            var path =
                    human.getNavigation()
                            .createPath(net.minecraft.core.BlockPos.containing(target), 0);
            if (path == null
                    || !dev.felix.hostilehumans.navigation.HumanTerrainSafety.safePath(human, path)
                    || !human.getNavigation().moveTo(path, 1)) failed++;
            else failed = 0;
        }
        if (failed >= 5)
            CultivationService.interrupt(human, "No safe path to training seat", false);
    }

    @Override
    public void stop() {
        human.getNavigation().stop();
    }
}
