package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Only ranks already admitted, recently visible enemies; never navigates or grants attack permission. */
final class HumanFinisherSelection {
    private static final class State {
        UUID harvesting;
        int hurtTimestamp;
        final LinkedHashMap<UUID, FinisherPolicy.Attempts> attempts = new LinkedHashMap<>();
        void interrupt(long now) {
            if (harvesting == null) return;
            attempts.computeIfAbsent(harvesting, k -> new FinisherPolicy.Attempts()).interrupt(now);
            while (attempts.size() > 32) attempts.remove(attempts.keySet().iterator().next());
        }
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private HumanFinisherSelection() {}
    static void clear(Human h) { STATES.remove(h); }
    static void observe(Human h, LivingEntity current) {
        State s = STATES.get(h);
        if (s == null) return;
        long now = HumanPerception.now(h);
        boolean hurt = h.getLastHurtByMobTimestamp() != s.hurtTimestamp;
        s.hurtTimestamp = h.getLastHurtByMobTimestamp();
        if (hurt) s.interrupt(now);
        if (s.harvesting != null && (current == null || !s.harvesting.equals(current.getUUID()))) {
            // A lost target/defensive handover is an interruption, never authority to reacquire it.
            s.interrupt(now); s.harvesting = null;
        }
    }
    private static boolean defensive(Human h, LivingEntity e) {
        return RecentDamageTracker.recentDamageFrom(h, e, 40) > 0
                || e instanceof Mob mob && mob.getTarget() == h && h.distanceToSqr(e) <= 36;
    }
    static LivingEntity candidate(Human h, LivingEntity current, boolean urgent) {
        if (urgent || h.isFleeing || h.explosionThreat != null || h.hurtTime > 0
                || h.getPersistentData().getInt(HumanGunner.MOD_ID + ":incoming_gunfire_until") > h.tickCount
                || !SoldierCombatMode.allowsAutonomousTargeting(h)
                || SoldierOrder.isReturningToHoldPosition(h)) return null;
        var known = HumanPerception.knownDynamicTargets(h);
        // Self-defense/high pressure always wins over the optional opportunity score.
        for (LivingEntity e : known) if (HumanPerception.selectionAllowed(h, e) && defensive(h, e)) return null;
        State s = STATES.computeIfAbsent(h, k -> { State state = new State(); state.hurtTimestamp = h.getLastHurtByMobTimestamp(); return state; });
        long now = HumanPerception.now(h);
        LivingEntity best = null, fallback = null;
        double bestScore = 0, fallbackScore = Double.NEGATIVE_INFINITY;
        for (LivingEntity e : known) {
            if (!HumanPerception.selectionAllowed(h, e) || !HumanPerception.recentlySaw(h, e.getUUID())) continue;
            var history = s.attempts.get(e.getUUID());
            if (history != null && history.blocked(now)) continue;
            double threat = HumanThreatAssessment.score(h, e);
            if (threat > fallbackScore) { fallbackScore = threat; fallback = e; }
            double score = FinisherPolicy.score(e.getMaxHealth(), e.getHealth(), h.distanceTo(e));
            if (score > bestScore || score == bestScore && score > 0 && best != null && e.getUUID().compareTo(best.getUUID()) < 0) {
                best = e; bestScore = score;
            }
        }
        if (best == null && current != null && s.attempts.containsKey(current.getUUID())
                && s.attempts.get(current.getUUID()).blocked(now)) return fallback;
        // A margin keeps two similarly wounded targets from trading places each review.
        if (best != null && current != null && best != current) {
            double currentScore = FinisherPolicy.score(current.getMaxHealth(), current.getHealth(), h.distanceTo(current));
            var history = s.attempts.get(current.getUUID());
            if ((history == null || !history.blocked(now)) && bestScore < currentScore * 1.2) return current;
        }
        return best;
    }
    static void selected(Human h, LivingEntity chosen, boolean harvesting, boolean preserve) {
        State s = STATES.get(h);
        if (s == null) return;
        if (s.harvesting != null && (chosen == null || !s.harvesting.equals(chosen.getUUID()))) s.interrupt(HumanPerception.now(h));
        if (preserve && s.harvesting != null && chosen != null && s.harvesting.equals(chosen.getUUID())) return;
        if (!harvesting) s.interrupt(HumanPerception.now(h));
        s.harvesting = harvesting && chosen != null
                && FinisherPolicy.score(chosen.getMaxHealth(), chosen.getHealth(), h.distanceTo(chosen)) > 0 ? chosen.getUUID() : null;
    }
}
