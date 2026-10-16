package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;
import net.minecraft.core.BlockPos;
import dev.felix.hostilehumans.core.CachedOcclusionProof;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Bounded negative LOS cache. Only completed real sweeps or conservative current-geometry proofs defer. */
final class VisualOcclusionMemory {
    record Result(boolean allowed,boolean reset,long retry) {}
    private static final class Entry {
        Vec3 eye,center,targetEye,rayEye,rayCenter;double width,height,rayWidth,rayHeight;
        final Set<Vec3> testedPoints=new LinkedHashSet<>();
        int failures;long retry,coveredMask,proofUntil;
        final Map<BlockPos,BlockState> blockers=new LinkedHashMap<>();
        Entry(Human h,LivingEntity e){anchor(h,e);}
        void anchor(Human h,LivingEntity e){eye=h.getEyePosition();center=e.getBoundingBox().getCenter();
            width=e.getBbWidth();height=e.getBbHeight();targetEye=e.getEyePosition();}
    }
    private static final Map<Human,LinkedHashMap<UUID,Entry>> CACHE=new WeakHashMap<>();
    private static final class Work { int tick=-1,used; }
    private static final Map<Human,Work> WORK=new WeakHashMap<>();
    private VisualOcclusionMemory() {}
    private static LinkedHashMap<UUID,Entry> entries(Human h){return CACHE.computeIfAbsent(h,k->new LinkedHashMap<>());}
    static Result prepare(Human h,LivingEntity e,long now,boolean urgent,List<Vec3> samples,long mask){
        var entries=entries(h);Entry entry=entries.get(e.getUUID());boolean reset=false;
        if(entry==null){entry=new Entry(h,e);entries.put(e.getUUID(),entry);reset=true;}
        boolean changed=false;
        for(var blocker:entry.blockers.entrySet()){
            if(!h.level().hasChunkAt(blocker.getKey())||!h.level().getBlockState(blocker.getKey()).equals(blocker.getValue())){changed=true;break;}
        }
        boolean moved=geometryChanged(entry,h,e);
        boolean uncovered=!CachedOcclusionProof.covers(entry.coveredMask,mask);
        if(changed){
            entry.anchor(h,e);entry.failures=0;entry.retry=0;entry.coveredMask=0;entry.proofUntil=0;entry.blockers.clear();entry.testedPoints.clear();reset=true;
        } else if(!urgent && (!entry.blockers.isEmpty())
                && (moved||uncovered||now>=entry.retry||entry.proofUntil>0&&now>=entry.proofUntil)
                && proof(h,e,entry,samples)){
            // This is a proof for every CURRENT eligible body sample, not an assumed invisible target.
            reset|=moved;entry.anchor(h,e);entry.coveredMask=mask;entry.proofUntil=now+4;
            if(now>=entry.retry)defer(entry,now,true);
            trim(h,entries);
            return new Result(false,reset,entry.retry);
        } else if(entry.coveredMask!=0&&(moved||uncovered||entry.proofUntil>0&&now>=entry.proofUntil)){
            // Any small movement/new FOV part breaks the completed cache unless re-proven blocked.
            entry.anchor(h,e);entry.failures=0;entry.retry=0;entry.coveredMask=0;entry.proofUntil=0;reset=true;
        } else if(entry.coveredMask==0){
            double movement=PerceptionConfig.get().number("vision.occlusion_move_reset");
            if(entry.eye.distanceToSqr(h.getEyePosition())>movement*movement
                    ||entry.center.distanceToSqr(e.getBoundingBox().getCenter())>movement*movement
                    ||entry.width!=e.getBbWidth()||entry.height!=e.getBbHeight()){
                entry.anchor(h,e);reset=true;
            }
        }
        trim(h,entries);
        return new Result(urgent||now>=entry.retry,reset,entry.retry);
    }
    private static boolean geometryChanged(Entry entry,Human h,LivingEntity e){
        return entry.eye.distanceToSqr(h.getEyePosition())>1.0E-8
                ||entry.targetEye.distanceToSqr(e.getEyePosition())>1.0E-8
                ||entry.center.distanceToSqr(e.getBoundingBox().getCenter())>1.0E-8
                ||entry.width!=e.getBbWidth()||entry.height!=e.getBbHeight();
    }
    private static void trim(Human h,LinkedHashMap<UUID,Entry> entries){
        int cap=PerceptionConfig.get().integer("budgets.candidates")+PerceptionConfig.get().integer("memory.maximum_targets");
        while(entries.size()>cap)entries.remove(entries.keySet().iterator().next());
    }
    private static boolean proof(Human h,LivingEntity e,Entry entry,List<Vec3> samples){
        var server=h.level().getServer();if(server==null)return false;
        Work work=WORK.computeIfAbsent(h,k->new Work());int tick=server.getTickCount();
        if(work.tick!=tick){work.tick=tick;work.used=0;}
        if(work.used>=PerceptionConfig.get().integer("budgets.occlusion_per_human"))return false;
        String lane=":proof:"+e.getUUID();
        if(!PerceptionBudget.claim(h,"occlusion",1,lane)){
            PerceptionBudget.cancel(h,"occlusion",lane);return false;
        }
        work.used++;
        List<AABB> cubes=new ArrayList<>();
        var blockers=new ArrayList<>(entry.blockers.entrySet());
        for(int i=blockers.size()-1;i>=0&&cubes.size()<CachedOcclusionProof.MAX_BLOCKERS;i--){
            var blocker=blockers.get(i);BlockPos pos=blocker.getKey();
            if(!h.level().hasChunkAt(pos))return false;
            if(Block.isShapeFullBlock(blocker.getValue().getCollisionShape(h.level(),pos,CollisionContext.of(h))))
                cubes.add(new AABB(pos));
        }
        return CachedOcclusionProof.covered(h.getEyePosition(),samples,cubes);
    }
    static void blockedRay(Human h,LivingEntity e,BlockHitResult hit,Vec3 point){
        Entry entry=entries(h).get(e.getUUID());if(entry==null||hit.getType()==HitResult.Type.MISS)return;
        Vec3 eye=h.getEyePosition(),center=e.getBoundingBox().getCenter();
        if(!eye.equals(entry.rayEye)||!center.equals(entry.rayCenter)||entry.rayWidth!=e.getBbWidth()||entry.rayHeight!=e.getBbHeight()){
            entry.testedPoints.clear();entry.rayEye=eye;entry.rayCenter=center;entry.rayWidth=e.getBbWidth();entry.rayHeight=e.getBbHeight();
        }
        entry.testedPoints.add(point);
        while(entry.testedPoints.size()>32)entry.testedPoints.remove(entry.testedPoints.iterator().next());
        BlockPos pos=hit.getBlockPos().immutable();
        if(h.level().hasChunkAt(pos)){
            entry.blockers.remove(pos);entry.blockers.put(pos,h.level().getBlockState(pos));
            while(entry.blockers.size()>32)entry.blockers.remove(entry.blockers.keySet().iterator().next());
        }
    }
    static void negative(Human h,LivingEntity e,long now,long mask,List<Vec3> samples){
        Entry entry=entries(h).get(e.getUUID());if(entry==null||entry.blockers.isEmpty())return;
        // Include the fast eye/last-visible point too: a narrow slit may expose it between the 27 grid points.
        boolean realComplete=h.getEyePosition().equals(entry.rayEye)
                &&e.getBoundingBox().getCenter().equals(entry.rayCenter)
                &&entry.rayWidth==e.getBbWidth()&&entry.rayHeight==e.getBbHeight()
                &&entry.testedPoints.containsAll(samples);
        if(!realComplete&&!proof(h,e,entry,samples))return;
        entry.anchor(h,e);entry.coveredMask=mask;entry.proofUntil=0;defer(entry,now,false);
    }
    private static void defer(Entry entry,long now,boolean proven){
        entry.failures=Math.min(4,entry.failures+1);
        int base=PerceptionConfig.get().integer("vision.occlusion_retry_ticks"),max=PerceptionConfig.get().integer("vision.occlusion_max_retry_ticks");
        entry.retry=now+Math.min(max,proven?(long)base<<(entry.failures-1):base);
    }
    static void visible(Human h,LivingEntity e){var entries=CACHE.get(h);if(entries!=null)entries.remove(e.getUUID());}
    static void clear(Human h){CACHE.remove(h);WORK.remove(h);}
}
