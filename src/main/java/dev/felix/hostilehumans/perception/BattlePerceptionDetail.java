package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.performance.ActivityLoadPolicy.Mode;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Chooses perception detail from the existing neighborhood query and server load tier. */
final class BattlePerceptionDetail {
    static final double LOCAL_RADIUS = 24;
    static final double LOCAL_RADIUS_SQUARED = LOCAL_RADIUS * LOCAL_RADIUS;
    private static final double STEP_RADIUS_SQUARED = 6 * 6;
    static final int FRONTLINE_VISUAL_PERIOD = 8;
    static final int ENTRY_NEIGHBORS = 7, EXIT_NEIGHBORS = 5, HOSTILE_NEIGHBORS = 2;
    private static final int LOCAL_SAMPLE_TTL = 40;
    private static final double SAMPLE_MOVEMENT_LIMIT_SQUARED = LOCAL_RADIUS_SQUARED / 4;
    private static final Map<Human, Sample> FRONTLINES = new WeakHashMap<>();

    private record Sample(ResourceKey<Level> dimension, Vec3 origin, long expires) {}

    private BattlePerceptionDetail() {}

    private static boolean enabled(Human h) {
        return UnifiedConfig.get().activityBudget().enabled() && !HumanMonitorControl.controls(h);
    }

    static boolean measure(Human h) {
        if (!enabled(h)) return false;
        Mode load = AdaptiveActivityBudget.mode(h);
        return load == Mode.BATTLE || load == Mode.CROWDED
                || h.getTarget() instanceof Human || h.getLastHurtByMob() instanceof Human || reduced(h);
    }

    static boolean throttledSharing(Human h) {
        return reduced(h) || enabled(h) && AdaptiveActivityBudget.mode(h) == Mode.BATTLE;
    }

    static void sample(Human h, long now, int humans, int enemies) {
        int minimum = reduced(h) ? EXIT_NEIGHBORS : ENTRY_NEIGHBORS;
        if (humans >= minimum && enemies >= HOSTILE_NEIGHBORS)
            FRONTLINES.put(h, new Sample(h.level().dimension(), h.position(), now + LOCAL_SAMPLE_TTL));
        else FRONTLINES.remove(h);
    }

    static boolean reduced(Human h) {
        var sample = FRONTLINES.get(h);
        return sample != null && enabled(h) && !h.isNoAi() && !h.isSleeping()
                && sample.dimension().equals(h.level().dimension())
                && h.position().distanceToSqr(sample.origin()) <= SAMPLE_MOVEMENT_LIMIT_SQUARED
                && HumanPerception.now(h) < sample.expires();
    }

    static boolean stableEngagement(Human h) {
        var target = h.getTarget();
        return reduced(h) && !h.isFleeing && target != null && target.isAlive() && target.level() == h.level()
                && h.distanceToSqr(target) <= LOCAL_RADIUS_SQUARED
                && HumanPerception.recentConfirmedSight(h, target);
    }

    static long nextNeighborhood(long now, Human h, int base, boolean stable) {
        if (!stable) return now + base;
        // A stable fighter refreshes its surrounding candidates once per 24 ticks.
        // Stagger by UUID so a synchronized wave does not query in one tick.
        int period = Math.max(base, 24);
        return nextPhase(now, h, period);
    }

    static long nextPhase(long now, Human h, int period) {
        int phase = Math.floorMod(h.getUUID().hashCode(), period);
        return now + 1 + Math.floorMod(phase - now - 1, period);
    }

    static boolean acceptsMovement(Human h, Entity source) {
        return !reduced(h) || closeHuman(h, source);
    }

    static boolean acceptsClue(Human h, String kind, Entity source) {
        if (!reduced(h)) return true;
        return switch (kind) {
            case "block", "dig", "door", "damage" -> false;
            case "walk", "run", "swim" -> closeHuman(h, source);
            default -> true;
        };
    }

    static boolean closeHuman(Human h, Entity source) {
        return source instanceof Human && h.distanceToSqr(source) <= STEP_RADIUS_SQUARED;
    }
}
