package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Bounded negative LOS cache. Geometry/deferred work never calls negative(). */
final class VisualOcclusionMemory {
    record Result(boolean allowed,boolean reset,long retry) {}
    private static final class Entry {
        Vec3 eye,center;double width,height;float yaw,pitch;
        int failures;long retry;
        final Map<BlockPos,BlockState> blockers=new LinkedHashMap<>();
        Entry(Human h,LivingEntity e){anchor(h,e);}
        void anchor(Human h,LivingEntity e){eye=h.getEyePosition();center=e.getBoundingBox().getCenter();
            width=e.getBbWidth();height=e.getBbHeight();yaw=h.getYHeadRot();pitch=h.getXRot();}
    }
    private static final Map<Human,LinkedHashMap<UUID,Entry>> CACHE=new WeakHashMap<>();
    private VisualOcclusionMemory() {}
    private static LinkedHashMap<UUID,Entry> entries(Human h){return CACHE.computeIfAbsent(h,k->new LinkedHashMap<>());}
    static Result prepare(Human h,LivingEntity e,long now,boolean urgent){
        var entries=entries(h);Entry entry=entries.get(e.getUUID());boolean reset=false;
        if(entry==null){entry=new Entry(h,e);entries.put(e.getUUID(),entry);reset=true;}
        else {
            double movement=PerceptionConfig.get().number("vision.occlusion_move_reset");
            reset=entry.eye.distanceToSqr(h.getEyePosition())>movement*movement
                    ||entry.center.distanceToSqr(e.getBoundingBox().getCenter())>movement*movement
                    ||entry.width!=e.getBbWidth()||entry.height!=e.getBbHeight();
            boolean turned=Math.abs(Mth.wrapDegrees(entry.yaw-h.getYHeadRot()))>8
                    ||Math.abs(Mth.wrapDegrees(entry.pitch-h.getXRot()))>8;
            if(!reset)for(var blocker:entry.blockers.entrySet()){
                if(!h.level().hasChunkAt(blocker.getKey())||!h.level().getBlockState(blocker.getKey()).equals(blocker.getValue())){reset=true;break;}
            }
            if(reset){entry.anchor(h,e);entry.failures=0;entry.retry=0;entry.blockers.clear();}
            else if(turned){
                // Turning invalidates a completed FOV-specific backoff, not unchanged ray geometry.
                // NegativeObservationSweep's sample mask handles partial FOV membership changes.
                entry.yaw=h.getYHeadRot();entry.pitch=h.getXRot();entry.failures=0;entry.retry=0;
            }
        }
        int cap=PerceptionConfig.get().integer("budgets.candidates")+PerceptionConfig.get().integer("memory.maximum_targets");
        while(entries.size()>cap)entries.remove(entries.keySet().iterator().next());
        return new Result(urgent||now>=entry.retry,reset,entry.retry);
    }
    static void blockedRay(Human h,LivingEntity e,BlockHitResult hit){
        Entry entry=entries(h).get(e.getUUID());if(entry==null||hit.getType()==HitResult.Type.MISS)return;
        BlockPos pos=hit.getBlockPos().immutable();
        if(entry.blockers.size()<32&&h.level().hasChunkAt(pos))entry.blockers.put(pos,h.level().getBlockState(pos));
    }
    static void negative(Human h,LivingEntity e,long now){
        Entry entry=entries(h).get(e.getUUID());if(entry==null||entry.blockers.isEmpty())return;
        entry.failures=Math.min(4,entry.failures+1);
        int base=PerceptionConfig.get().integer("vision.occlusion_retry_ticks"),max=PerceptionConfig.get().integer("vision.occlusion_max_retry_ticks");
        entry.retry=now+Math.min(max,(long)base<<(entry.failures-1));
    }
    static void visible(Human h,LivingEntity e){var entries=CACHE.get(h);if(entries!=null)entries.remove(e.getUUID());}
    static void clear(Human h){CACHE.remove(h);}
}
