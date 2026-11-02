package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.inventory.SoldierPickupPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Visual evidence only. Coarse item queries never grant position knowledge by themselves. */
public final class HumanLootVision {
    public enum Sight { VISIBLE, HIDDEN, DEFERRED }
    private static final int RAYS_PER_HUMAN_TICK = 4;
    private static final int MAX_RECORDS = 64;
    private static final Map<Human,State> STATES = new WeakHashMap<>();
    private static final class State {
        int tick = Integer.MIN_VALUE, rays;
        final LinkedHashMap<UUID,Probe> probes = new LinkedHashMap<>();
        final LinkedHashMap<UUID,Seen> seen = new LinkedHashMap<>();
    }
    private static final class Probe {
        Vec3 item, eye; float yaw, pitch;
        int cursor, completeTick = Integer.MIN_VALUE;
        Sight result;
        Probe(Human h,ItemEntity item){snapshot(h,item);}
        void snapshot(Human h,ItemEntity e){item=e.position();eye=h.getEyePosition();yaw=h.getYHeadRot();pitch=h.getXRot();}
        boolean compatible(Human h,ItemEntity e){return item.distanceToSqr(e.position())<.01
                &&eye.distanceToSqr(h.getEyePosition())<4
                &&Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-h.getYHeadRot()))<90&&Math.abs(pitch-h.getXRot())<45;}
        boolean exact(Human h,ItemEntity e){return item.distanceToSqr(e.position())<1.e-8
                &&eye.distanceToSqr(h.getEyePosition())<1.e-8
                &&Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw-h.getYHeadRot()))<.01&&Math.abs(pitch-h.getXRot())<.01;}
    }
    private record Seen(Vec3 position,int tick) {}
    private HumanLootVision() {}
    /** Local ground awareness ignores head yaw only; walls and the shared ray budget still apply. */
    public static boolean nearbyGround(Vec3 feet,Vec3 point) {
        Vec3 delta=point.subtract(feet);
        return Double.isFinite(delta.x)&&Double.isFinite(delta.y)&&Double.isFinite(delta.z)
                &&Math.abs(delta.y)<=1.5&&delta.horizontalDistanceSqr()<=64;
    }
    public static Vec3 lastSeen(Human h,ItemEntity item){
        State s=STATES.get(h);Seen seen=s==null?null:s.seen.get(item.getUUID());
        return seen!=null&&h.tickCount-seen.tick<=600?seen.position:null;
    }
    public static Sight inspect(Human h,ItemEntity item){
        if(h.level().isClientSide||!item.isAlive()||item.level()!=h.level())return Sight.HIDDEN;
        State s=STATES.computeIfAbsent(h,k->new State());
        if(s.tick!=h.tickCount){s.tick=h.tickCount;s.rays=0;s.seen.values().removeIf(v->h.tickCount-v.tick>600);}
        Probe p=s.probes.get(item.getUUID());
        if(p!=null&&p.completeTick==h.tickCount&&p.exact(h,item))return p.result;
        if(p==null||p.result!=null||!p.compatible(h,item)){p=new Probe(h,item);s.probes.put(item.getUUID(),p);}
        while(s.probes.size()>MAX_RECORDS)s.probes.remove(s.probes.keySet().iterator().next());
        var b=item.getBoundingBox();Vec3 center=b.getCenter();
        Vec3[] points={center,new Vec3(center.x,b.maxY-.01,center.z),
                new Vec3(b.minX+.01,center.y,b.minZ+.01),new Vec3(b.maxX-.01,center.y,b.maxZ-.01)};
        while(p.cursor<points.length){
            Vec3 point=points[p.cursor];
            boolean nearby=SoldierPickupPolicy.canCollectNow(h)&&nearbyGround(h.position(),point);
            if(!nearby&&!HumanPerception.withinLootVision(h,point)){p.cursor++;continue;}
            if(s.rays>=RAYS_PER_HUMAN_TICK||!PerceptionBudget.claim(h,"rays",1,":loot:"+item.getUUID()+":"+p.cursor))return Sight.DEFERRED;
            s.rays++;p.cursor++;
            Vec3 toEye=h.getEyePosition().subtract(point);
            Vec3 end=toEye.lengthSqr()>1.e-8?point.add(toEye.normalize().scale(1.e-4)):point;
            boolean clear=h.level().clip(new ClipContext(h.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,h)).getType()==HitResult.Type.MISS;
            if(clear){
                p.snapshot(h,item);p.completeTick=h.tickCount;p.result=Sight.VISIBLE;
                s.seen.remove(item.getUUID());s.seen.put(item.getUUID(),new Seen(item.position(),h.tickCount));
                while(s.seen.size()>MAX_RECORDS)s.seen.remove(s.seen.keySet().iterator().next());
                return p.result;
            }
        }
        p.snapshot(h,item);p.completeTick=h.tickCount;p.result=Sight.HIDDEN;return p.result;
    }
}
