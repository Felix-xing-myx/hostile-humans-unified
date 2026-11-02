package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.behavior.combat.HumanTargetCoordinator;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;

/** Shared execution feedback for weapon movement and target coordination. Never scans, paths or selects enemies. */
public final class RangedEngagement {
    public enum Failure { WINDOW_EXHAUSTED, MOVE_REJECTED, ROUTE_STALLED, TERRAIN_EXHAUSTED }
    private static final int MAX_TARGETS = 16, RETAIN_TICKS = 600;
    private static final Map<Human, LinkedHashMap<UUID, Entry>> STATES = new WeakHashMap<>();
    private static final class Entry {
        final ResourceKey<Level> dimension;
        final RangedEngagementProgress progress = new RangedEngagementProgress();
        int visited;
        Failure lastFailure;
        Entry(Human h) { dimension = h.level().dimension(); visited = h.tickCount; }
    }
    private RangedEngagement() {}
    private static boolean eligible(Human h, LivingEntity t) {
        return !h.level().isClientSide && h.isAlive() && !h.isRemoved() && t != null && t.isAlive()
                && t.level() == h.level() && !h.isFleeing && !h.isPassenger() && !h.isSeekingShore()
                && !h.isShoreTransitionPending() && !HumanTargetCoordinator.suspended(h)
                && !SoldierOrder.isHoldingPosition(h) && HumanPerception.canTarget(h, t);
    }
    private static Entry entry(Human h, LivingEntity t, boolean create) {
        var entries = create ? STATES.computeIfAbsent(h, ignored -> new LinkedHashMap<>()) : STATES.get(h);
        if (entries == null || t == null) return null;
        entries.entrySet().removeIf(e -> !e.getValue().dimension.equals(h.level().dimension())
                || h.tickCount < e.getValue().visited || h.tickCount - e.getValue().visited > RETAIN_TICKS);
        Entry value = entries.get(t.getUUID());
        if (value == null && create) {
            while (entries.size() >= MAX_TARGETS) entries.remove(entries.keySet().iterator().next());
            value = new Entry(h); entries.put(t.getUUID(), value);
        }
        return value;
    }
    public static void observe(Human h, LivingEntity target, boolean canFire) {
        if (!eligible(h, target)) return;
        Entry value = entry(h, target, true); value.visited = h.tickCount;
        value.progress.observe(h.tickCount, h.getX(), h.getY(), h.getZ(), canFire);
    }
    public static void failure(Human h, LivingEntity target, Failure reason) {
        if (!eligible(h, target)) return;
        Entry value = entry(h, target, false);
        if (value != null) { value.progress.failure(h.tickCount); value.lastFailure = reason; }
    }
    public static void window(Human h, LivingEntity target, RangedFiringPosition.AttackWindow result, boolean moved) {
        if (result.failed()) failure(h, target, Failure.WINDOW_EXHAUSTED);
        else if (result.status() == RangedFiringPosition.AttackStatus.FOUND && !moved) failure(h, target, Failure.MOVE_REJECTED);
    }
    public static boolean mayApproach(Human h, LivingEntity target) {
        Entry value = entry(h, target, false);
        return eligible(h, target) && value != null && value.progress.relax();
    }
    public static boolean temporarilyDeferred(Human h, LivingEntity target) {
        var held = h.getMainHandItem();
        boolean ranged = GunSupport.isGunItem(held) || RangedWeaponCustody.isBowOrCrossbow(held)
                || held.getItem() instanceof TridentItem;
        Entry value = entry(h, target, false);
        return ranged && eligible(h, target) && value != null && value.progress.deferred(h.tickCount);
    }
    public static void clear(Human h) { STATES.remove(h); }
}
