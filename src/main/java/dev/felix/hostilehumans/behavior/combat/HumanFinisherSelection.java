package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.defense.RecentDamageTracker;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.PerceptionBudget;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Only ranks already admitted, recently visible enemies; never navigates or grants attack permission. */
public final class HumanFinisherSelection {
    private static final class State {
        UUID harvesting;
        int hurtTimestamp;
        long interruptedUntil;
        long lastHarvestUntil;
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
    public static void damaged(Human h) {
        State s=STATES.get(h);
        if(s==null || s.harvesting==null && HumanPerception.now(h)>=s.lastHarvestUntil)return;
        s.interrupt(HumanPerception.now(h));
        s.interruptedUntil=HumanPerception.now(h)+FinisherPolicy.SELF_DEFENSE_TICKS;
        s.harvesting=null;
    }
    /** Only interrupts this optional strategy, and only for already admitted real attackers. */
    static LivingEntity defensiveTarget(Human h) {
        State s=STATES.get(h);
        if(s==null || HumanPerception.now(h)>=s.interruptedUntil)return null;
        LivingEntity best=null;double damage=0;
        for(LivingEntity e:HumanPerception.knownDynamicTargets(h)) {
            if(!HumanPerception.selectionAllowed(h,e))continue;
            double recent=RecentDamageTracker.recentDamageFrom(h,e,80);
            if(recent>damage){damage=recent;best=e;}
        }
        return best;
    }
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
                || h.isUsingItem() || h.hhu$hasPendingMeleeAttack()
                || h.hhu$shieldDefense().defenseOwnsFacing()
                || h.getPersistentData().getInt(HumanRuntime.MOD_ID + ":incoming_gunfire_until") > h.tickCount
                || !SoldierCombatMode.allowsAutonomousTargeting(h)
                || SoldierOrder.isReturningToHoldPosition(h)) return null;
        var known = HumanPerception.knownDynamicTargets(h);
        // Self-defense/high pressure always wins over the optional opportunity score.
        for (LivingEntity e : known) if (HumanPerception.selectionAllowed(h, e) && defensive(h, e)) return null;
        State s = STATES.computeIfAbsent(h, k -> { State state = new State(); state.hurtTimestamp = h.getLastHurtByMobTimestamp(); return state; });
        long now = HumanPerception.now(h);
        if(now<s.interruptedUntil)return null;
        LivingEntity best = null;
        double bestScore = 0, runnerScore = 0;
        for (LivingEntity e : known) {
            if (!HumanPerception.selectionAllowed(h, e) || !HumanPerception.recentlySaw(h, e.getUUID())) continue;
            if(!FinisherPolicy.clearOpportunity(e.getMaxHealth(),e.getHealth(),h.distanceTo(e),
                    e instanceof Human || e instanceof net.minecraft.world.entity.player.Player))continue;
            // Optional wounded-mob harvesting cannot undo a decisive high-threat selection.
            if(current!=null&&e!=current&&HumanThreatAssessment.shouldSwitch(h,e,current))continue;
            var history = s.attempts.get(e.getUUID());
            if (history != null && history.blocked(now)) continue;
            double score = FinisherPolicy.score(e.getMaxHealth(), e.getHealth(), h.distanceTo(e));
            if (score > bestScore || score == bestScore && score > 0 && best != null && e.getUUID().compareTo(best.getUUID()) < 0) {
                runnerScore=bestScore;best = e; bestScore = score;
            }else runnerScore=Math.max(runnerScore,score);
        }
        if(!FinisherPolicy.decisive(bestScore,runnerScore))return null;
        // A margin keeps two similarly wounded targets from trading places each review.
        if (best != null && current != null && best != current) {
            double currentScore = FinisherPolicy.score(current.getMaxHealth(), current.getHealth(), h.distanceTo(current));
            var history = s.attempts.get(current.getUUID());
            if ((history == null || !history.blocked(now)) && bestScore < currentScore * 1.2) return null;
        }
        if(best!=null && (!PerceptionBudget.claim(h,"rays",1,":finisher") || !h.hasLineOfSight(best)))return null;
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
        if(s.harvesting!=null)s.lastHarvestUntil=HumanPerception.now(h)+FinisherPolicy.HANDOVER_MEMORY_TICKS;
    }
}
