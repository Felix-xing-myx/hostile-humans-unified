package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

/** Tracks final post-mitigation health damage for burst-damage retreats. */
final class RecentDamageTracker {
    private record DamageSample(int tick, float amount) {
    }

    private static final class DamageWindow {
        private final ArrayDeque<DamageSample> samples = new ArrayDeque<>();
        private int nextEligibleTick;
    }

    private static final int WINDOW_TICKS = 80;
    private static final float RETREAT_RATIO = 0.40F;
    private static final Map<Human, DamageWindow> WINDOWS = new WeakHashMap<>();

    private RecentDamageTracker() {
    }

    static void record(Human human, float actualDamage) {
        if (actualDamage <= 0.0F || human.level().isClientSide) {
            return;
        }
        DamageWindow window = WINDOWS.computeIfAbsent(human, ignored -> new DamageWindow());
        prune(human, window);
        window.samples.addLast(new DamageSample(
                human.tickCount,
                Math.min(actualDamage, Math.max(0.0F, human.getHealth()))
        ));
    }

    static boolean consumeRetreatTrigger(Human human) {
        DamageWindow window = WINDOWS.get(human);
        if (window == null) {
            return false;
        }
        prune(human, window);
        if (human.tickCount < window.nextEligibleTick) {
            return false;
        }
        float total = 0.0F;
        for (DamageSample sample : window.samples) {
            total += sample.amount();
        }
        if (total <= human.getMaxHealth() * RETREAT_RATIO) {
            return false;
        }
        window.samples.clear();
        window.nextEligibleTick = human.tickCount + WINDOW_TICKS;
        human.getPersistentData().putFloat("humangunner:burst_damage_retreat_amount", total);
        human.getPersistentData().putInt("humangunner:burst_damage_retreat_tick", human.tickCount);
        return true;
    }

    private static void prune(Human human, DamageWindow window) {
        while (!window.samples.isEmpty()
                && human.tickCount - window.samples.peekFirst().tick() > WINDOW_TICKS) {
            window.samples.removeFirst();
        }
    }
}
