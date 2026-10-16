package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ThreatScorePolicy;
import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Read confirmed entities only; cached attributes contain no live entity references. */
final class HumanThreatAssessment {
    private record Snapshot(long tick,double intrinsic,double score) {}
    private static final Map<Human,LinkedHashMap<UUID,Snapshot>> CACHE=new WeakHashMap<>();
    private HumanThreatAssessment() {}
    private static PerceptionConfig c(){return PerceptionConfig.get();}
    static double score(Human h,LivingEntity target){
        if(!HumanPerception.canTarget(h,target))return Double.NEGATIVE_INFINITY;
        long now=HumanPerception.now(h);
        LinkedHashMap<UUID,Snapshot> entries=CACHE.computeIfAbsent(h,k->new LinkedHashMap<>());
        Snapshot snapshot=entries.get(target.getUUID());
        int interval=AdaptiveActivityBudget.interval(h,c().integer("selection.attribute_interval_ticks"),false);
        if(snapshot==null||now<snapshot.tick||now-snapshot.tick>=interval){
            var attack=target.getAttribute(Attributes.ATTACK_DAMAGE);
            snapshot=new Snapshot(now,ThreatScorePolicy.intrinsic(target.getHealth(),target.getArmorValue(),
                    attack==null?0:attack.getValue(),CombatWeaponPolicy.isRangedThreat(target)),Double.NaN);
            entries.remove(target.getUUID());entries.put(target.getUUID(),snapshot);
            while(entries.size()>c().integer("memory.maximum_targets"))entries.remove(entries.keySet().iterator().next());
        }
        double score=ThreatScorePolicy.score(snapshot.intrinsic,h.distanceTo(target),
                RecentDamageTracker.recentDamageFrom(h,target,80),h.getMaxHealth(),
                target instanceof Mob mob&&mob.getTarget()==h,c().number("selection.near_radius"));
        entries.put(target.getUUID(),new Snapshot(snapshot.tick,snapshot.intrinsic,score));
        return score;
    }
    /** Observer reads existing computation only, never refreshes attributes or selects targets. */
    static String describe(Human h,LivingEntity target){
        if(target==null)return "无主目标";
        var entries=CACHE.get(h);Snapshot snapshot=entries==null?null:entries.get(target.getUUID());
        return snapshot==null||!Double.isFinite(snapshot.score)?"尚未评估":String.format(Locale.ROOT,"%.1f分 · 基础%.1f",snapshot.score,snapshot.intrinsic);
    }
    static boolean lowFar(Human h,LivingEntity target){
        return HumanPerception.canTarget(h,target)&&h.distanceToSqr(target)>Math.pow(c().number("selection.near_radius"),2)
                &&score(h,target)<c().number("selection.low_far_score");
    }
    static boolean shouldSwitch(Human h,LivingEntity current,LivingEntity candidate){
        return HumanPerception.canTarget(h,candidate)&&ThreatScorePolicy.stronger(score(h,current),score(h,candidate),
                c().number("selection.switch_ratio"),c().number("selection.switch_points"));
    }
    static boolean nearOverride(Human h,LivingEntity current,LivingEntity candidate){
        return lowFar(h,current)&&HumanPerception.canTarget(h,candidate)
                &&h.distanceToSqr(candidate)<=Math.pow(c().number("selection.near_radius"),2)
                &&shouldSwitch(h,current,candidate);
    }
}
