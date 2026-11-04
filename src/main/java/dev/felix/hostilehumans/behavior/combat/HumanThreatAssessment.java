package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.defense.RecentDamageTracker;
import dev.felix.hostilehumans.faction.HumanFactions;
import dev.felix.hostilehumans.perception.HumanAwareness;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.HumanTargetSharing;
import dev.felix.hostilehumans.perception.PerceptionConfig;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;

import dev.felix.hostilehumans.perception.ThreatScorePolicy;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Read confirmed entities only; cached attributes contain no live entity references. */
public final class HumanThreatAssessment {
    private record Snapshot(long tick,double intrinsic,double priority,double score) {}
    private static final Map<Human,LinkedHashMap<UUID,Snapshot>> CACHE=new WeakHashMap<>();
    private HumanThreatAssessment() {}
    private static PerceptionConfig c(){return PerceptionConfig.get();}
    public static double priority(Human h,LivingEntity target) {
        return ThreatScorePolicy.reportPriority(target instanceof Human other&&HumanFactions.enemies(h,other),
                RecentDamageTracker.recentDamageFrom(h,target,80),h.getMaxHealth());
    }
    public static double intrinsic(Human h,LivingEntity target) {
        if(!HumanPerception.canTarget(h,target))return 0;
        long now=HumanPerception.now(h);
        LinkedHashMap<UUID,Snapshot> entries=CACHE.computeIfAbsent(h,k->new LinkedHashMap<>());
        Snapshot snapshot=entries.get(target.getUUID());
        int interval=AdaptiveActivityBudget.interval(h,c().integer("selection.attribute_interval_ticks"),false);
        if(snapshot==null||now<snapshot.tick||now-snapshot.tick>=interval){
            snapshot=new Snapshot(now,readIntrinsic(target),0,Double.NaN);
            entries.remove(target.getUUID());entries.put(target.getUUID(),snapshot);
            while(entries.size()>c().integer("memory.maximum_targets"))entries.remove(entries.keySet().iterator().next());
        }
        return snapshot.intrinsic;
    }
    private static double readIntrinsic(LivingEntity target) {
        var attack=target.getAttribute(Attributes.ATTACK_DAMAGE);
        return ThreatScorePolicy.intrinsic(target.getMaxHealth(),target.getArmorValue(),
                attack==null?0:attack.getValue(),CombatWeaponPolicy.isRangedThreat(target));
    }
    public static double score(Human h,LivingEntity target){
        if(!HumanPerception.canTarget(h,target))return Double.NEGATIVE_INFINITY;
        double intrinsic=intrinsic(h,target);
        var entries=CACHE.get(h);Snapshot snapshot=entries.get(target.getUUID());
        var report=HumanTargetSharing.threat(h,target);
        double priority=Math.max(priority(h,target),report.priority());
        double score=ThreatScorePolicy.score(Math.max(intrinsic,report.intrinsic()),h.distanceTo(target),
                RecentDamageTracker.recentDamageFrom(h,target,80),h.getMaxHealth(),
                target instanceof Mob mob&&mob.getTarget()==h,c().number("selection.near_radius"))
                +priority;
        entries.put(target.getUUID(),new Snapshot(snapshot.tick,snapshot.intrinsic,priority,score));
        return score;
    }
    /** Observer reads existing computation only, never refreshes attributes or selects targets. */
    public static String describe(Human h,LivingEntity target){
        if(target==null)return "无主目标";
        var entries=CACHE.get(h);Snapshot snapshot=entries==null?null:entries.get(target.getUUID());
        return snapshot==null||!Double.isFinite(snapshot.score)?"尚未评估":String.format(Locale.ROOT,"%.1f分 · 基础%.1f · 战术%.1f",snapshot.score,snapshot.intrinsic,snapshot.priority);
    }
    static boolean lowFar(Human h,LivingEntity target){
        return HumanPerception.canTarget(h,target)&&h.distanceToSqr(target)>Math.pow(c().number("selection.near_radius"),2)
                &&score(h,target)<c().number("selection.low_far_score");
    }
    static boolean shouldSwitch(Human h,LivingEntity current,LivingEntity candidate){
        return HumanPerception.canTarget(h,candidate)&&ThreatScorePolicy.stronger(score(h,current),score(h,candidate),
                c().number("selection.switch_ratio"),c().number("selection.switch_points"));
    }
    private static boolean weakCurrent(Human h, LivingEntity target) {
        return HumanPerception.canTarget(h,target)
                && !(target instanceof Human) && !(target instanceof net.minecraft.world.entity.player.Player)
                && intrinsic(h,target) < c().number("selection.weak_current_intrinsic")
                && RecentDamageTracker.recentDamageFrom(h,target,40) < h.getMaxHealth() * .1;
    }
    /** Incoming damage grants attention only; it carries no hidden position or attack permission. */
    public static double impactObservation(Human human, LivingEntity attacker, float actual) {
        LivingEntity current = human.getTarget();
        if (!canPrioritizeImpact(human, attacker.getUUID())) return 0;
        return ThreatScorePolicy.impactPriority(
                Math.max(actual, RecentDamageTracker.recentDamageFrom(human, attacker, 80)),
                RecentDamageTracker.recentDamageFrom(human, current, 80), human.getMaxHealth(),
                attacker instanceof Human other && HumanFactions.enemies(human, other));
    }

    public static boolean canPrioritizeImpact(Human human, UUID source) {
        LivingEntity current = human.getTarget();
        return !HumanPerception.canTarget(human, current)
                || !current.getUUID().equals(source) && weakCurrent(human, current);
    }

    public static boolean priorityInterrupt(Human h, LivingEntity current, LivingEntity challenger) {
        return HumanPerception.canTarget(h, challenger) && priorityInterrupt(h, current, challenger, false);
    }

    /** Call only with a verified visible enemy. This comparison never grants attack permission. */
    public static boolean visiblePriorityInterrupt(Human h, LivingEntity current, LivingEntity challenger) {
        return priorityInterrupt(h, current, challenger, true);
    }

    private static boolean priorityInterrupt(Human h, LivingEntity current, LivingEntity challenger,
            boolean visible) {
        if (current == challenger || !weakCurrent(h, current)) return false;
        double tactical = priority(h, challenger);
        if (tactical >= 60 || impactObservation(h, challenger, 0) > 0) return true;
        double weak = intrinsic(h, current);
        double threat = visible ? readIntrinsic(challenger) : intrinsic(h, challenger);
        if (threat < Math.max(weak * 1.75, weak + 12)) return false;
        double challengerScore = visible ? ThreatScorePolicy.score(threat, h.distanceTo(challenger),
                RecentDamageTracker.recentDamageFrom(h, challenger, 80), h.getMaxHealth(),
                challenger instanceof Mob mob && mob.getTarget() == h,
                c().number("selection.near_radius")) + tactical : score(h, challenger);
        return challengerScore >= score(h, current) * .55;
    }

    public static boolean maySoundGlance(Human h, net.minecraft.world.phys.Vec3 point, int priority) {
        if (priority < 3 || h.isFleeing || h.hhu$hasPendingMeleeAttack()
                || h.hhu$shieldDefense().defenseOwnsFacing() || h.isUsingItem()) return false;
        double distance = h.position().distanceToSqr(point);
        return (distance <= Math.pow(HumanAwareness.movementRadius(),2) && lowFar(h,h.getTarget()))
                || (distance <= Math.pow(c().number("selection.near_radius"),2) && weakCurrent(h,h.getTarget()));
    }
    static boolean nearOverride(Human h,LivingEntity current,LivingEntity candidate){
        return (lowFar(h,current)||weakCurrent(h,current))&&HumanPerception.canTarget(h,candidate)
                &&h.distanceToSqr(candidate)<=Math.pow(c().number("selection.near_radius"),2)
                &&shouldSwitch(h,current,candidate);
    }
}
