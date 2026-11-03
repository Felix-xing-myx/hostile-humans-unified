package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.performance.ActivityLoadPolicy.Mode;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Chooses perception detail from the existing neighborhood query and server load tier. */
final class BattlePerceptionDetail {
    static final double LOCAL_RADIUS = 24;
    static final double QUICK_RECOGNITION_RANGE = 12;
    static final double LOCAL_RADIUS_SQUARED = LOCAL_RADIUS * LOCAL_RADIUS;
    private static final double STEP_RADIUS_SQUARED = 6 * 6;
    private static final int LOCAL_SAMPLE_TTL = 40;
    private static final Map<Human, Sample> FRONTLINES = new WeakHashMap<>();

    private record Sample(ResourceKey<Level> dimension, long expires) {}

    private BattlePerceptionDetail() {}

    static boolean measure(Human h) {
        return AdaptiveActivityBudget.mode(h) == Mode.BATTLE && !HumanMonitorControl.controls(h);
    }

    static void sample(Human h, long now, int humans, int enemies) {
        if (humans >= 7 && enemies >= 2)
            FRONTLINES.put(h, new Sample(h.level().dimension(), now + LOCAL_SAMPLE_TTL));
        else FRONTLINES.remove(h);
    }

    static boolean reduced(Human h) {
        var sample = FRONTLINES.get(h);
        return sample != null && measure(h) && sample.dimension().equals(h.level().dimension())
                && HumanPerception.now(h) < sample.expires();
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
