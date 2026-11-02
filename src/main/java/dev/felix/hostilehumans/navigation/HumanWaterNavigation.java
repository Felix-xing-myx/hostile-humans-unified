package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.navigation.HumanNavigationOwnership;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Preserve native swimming search/following, with the same execution ownership as ground routes. */
public final class HumanWaterNavigation extends WaterBoundPathNavigation implements HumanNavigationOwnership.OwnedNavigation {
    private final HumanNavigationOwnership ownership = new HumanNavigationOwnership();
    public HumanWaterNavigation(Mob mob, Level level) { super(mob, level); }
    @Override public HumanNavigationOwnership movementOwnership() { return ownership; }
    @Override public boolean moveTo(Path route, double speed) {
        return mob instanceof Human human && ownership != null
                ? ownership.install(human, () -> this.path, () -> super.moveTo(route, speed), () -> super.stop())
                : super.moveTo(route, speed);
    }
    @Override public void stop() {
        if (ownership != null && mob instanceof Human human && !ownership.mayStop(human, path)) return;
        super.stop();
        if (ownership != null) ownership.stopped();
    }
    @Override public void recomputePath() {
        if (mob instanceof Human human && ownership != null) {
            Path before = path;
            ownership.runInstalled(human, before, () -> {
                super.recomputePath(); ownership.recomputed(human, before, path);
            });
        } else super.recomputePath();
    }
    @Override public void setSpeedModifier(double speed) {
        if (!Double.isFinite(speed) || speed < 0) return;
        if (mob instanceof Human human && dev.felix.hostilehumans.behavior.HumanGoalExecutionBridge.bound(human)
                && dev.felix.hostilehumans.navigation.HumanNavigationOwnership.writeToken(human) == null) return;
        super.setSpeedModifier(speed);
    }
    @Override public void tick() {
        if (mob instanceof Human human && ownership != null) {
            if (!ownership.runInstalled(human, path, () -> super.tick())) { super.stop(); ownership.stopped(); }
        } else super.tick();
    }
}
