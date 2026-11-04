package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Per-hop acoustic reach. Null means deferred work, never proof of an open link. */
final class HumanReportPropagation {
    private record Probe(long tick,Vec3 from,Vec3 to,boolean clear,String dimension) { }
    private static final Map<Human,LinkedHashMap<UUID,Probe>> CACHE=new WeakHashMap<>();
    private HumanReportPropagation() { }
    static Boolean link(Human sender,Human receiver) {
        var c=PerceptionConfig.get();
        double distance = sender.distanceTo(receiver), range = HumanTargetSharing.range(sender);
        double wallFactor = c.number("sharing.wall_range_factor");
        if (!withinRange(distance, range, true, wallFactor)) return false;
        boolean reduced = BattlePerceptionDetail.measure(sender);
        if (withinRange(distance, range, false, wallFactor)) {
            if (reduced && sender.level() instanceof ServerLevel level)
                BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.REPORT_DIRECT, 1);
            return true;
        }
        long now=HumanPerception.now(sender);
        Vec3 from=sender.getEyePosition(),to=receiver.getEyePosition();
        var cache=CACHE.computeIfAbsent(sender,k->new LinkedHashMap<>());
        Probe p=cache.get(receiver.getUUID());
        String dimension=sender.level().dimension().location().toString();
        int ttl = c.integer("sharing.occlusion_cache_ticks");
        if (reduced) ttl = Math.max(ttl, 16);
        double movedSquared = reduced ? 4 : .25;
        if(p==null || !dimension.equals(p.dimension) || now<p.tick || now-p.tick>=ttl
                ||p.from.distanceToSqr(from)>movedSquared ||p.to.distanceToSqr(to)>movedSquared) {
            if(!PerceptionBudget.claim(sender,"rays",1,":report:"+receiver.getUUID()))return null;
            if (reduced && sender.level() instanceof ServerLevel level)
                BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.REPORT_CLIP, 1);
            var hit=sender.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,sender));
            p=new Probe(now,from,to,hit.getType()==HitResult.Type.MISS,dimension);
            cache.remove(receiver.getUUID());cache.put(receiver.getUUID(),p);
            while(cache.size()>64)cache.remove(cache.keySet().iterator().next());
        } else if (reduced && sender.level() instanceof ServerLevel level) {
            BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.REPORT_CACHE, 1);
        }
        return p.clear;
    }
    static boolean withinRange(double distance,double range,boolean clear,double factor) {
        if(!Double.isFinite(distance+range+factor) || distance<0 || range<=0)return false;
        double reach=range*(clear?1:Math.max(0,Math.min(1,factor)));
        return reach>0 && distance<=reach;
    }
}
