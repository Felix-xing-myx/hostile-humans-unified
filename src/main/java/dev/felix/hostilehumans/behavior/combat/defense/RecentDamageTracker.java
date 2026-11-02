package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.entity.Entity;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Tracks final health damage by attacker for threat selection only. */
public final class RecentDamageTracker {
    private record DamageSample(int tick, float amount, UUID attacker) {
    }

    private static final class DamageWindow {
        private final ArrayDeque<DamageSample> samples = new ArrayDeque<>();
    }

    private static final int WINDOW_TICKS = 80;
    private static final Map<Human, DamageWindow> WINDOWS = new WeakHashMap<>();

    private RecentDamageTracker() {
    }

    public static void record(Human human, float actualDamage, Entity attacker) {
        if (actualDamage <= 0.0F || !Float.isFinite(actualDamage) || human.level().isClientSide) {
            return;
        }
        DamageWindow window = WINDOWS.computeIfAbsent(human, ignored -> new DamageWindow());
        prune(human, window);
        window.samples.addLast(new DamageSample(
                human.tickCount,
                actualDamage,
                attacker == null ? null : attacker.getUUID()
        ));
    }

    public static float recentDamageFrom(Human human, Entity attacker, int ageTicks) {
        if (attacker == null) return 0.0F;
        DamageWindow window = WINDOWS.get(human);
        if (window == null) return 0.0F;
        prune(human, window);
        UUID attackerId = attacker.getUUID();
        float total = 0.0F;
        for (DamageSample sample : window.samples) {
            if (human.tickCount - sample.tick() <= ageTicks
                    && attackerId.equals(sample.attacker())) {
                total += sample.amount();
            }
        }
        return total;
    }

    private static void prune(Human human, DamageWindow window) {
        while (!window.samples.isEmpty()
                && human.tickCount - window.samples.peekFirst().tick() > WINDOW_TICKS) {
            window.samples.removeFirst();
        }
    }
}
