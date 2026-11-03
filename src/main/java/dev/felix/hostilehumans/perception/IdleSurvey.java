package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.observer.NavigationDebugService;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Sparse, budgeted view-direction selection. Never requests a navigation destination. */
final class IdleSurvey {
    private static final Map<Human,State> STATES=new WeakHashMap<>();
    private static final class State {
        long next,until;
        boolean reduced;
        Vec3 origin,point,best;
        float[] yaws;
        int index;
        boolean rear;
        double openness;
        String dimension;
    }
    private IdleSurvey() { }
    static Vec3 point(Human h) {
        if(h.isFleeing || h.isUsingItem() || h.hhu$hasPendingMeleeAttack()
                || NavigationDebugService.controls(h) || HumanInventorySession.isEditing(h))return null;
        long now=HumanPerception.now(h);var c=PerceptionConfig.get();
        State s=STATES.computeIfAbsent(h,k->new State());
        Vec3 eye=h.getEyePosition();
        String dimension=h.level().dimension().location().toString();
        if(!dimension.equals(s.dimension)){s.dimension=dimension;s.origin=null;s.yaws=null;s.point=null;s.next=now;}
        if(s.origin!=null && s.origin.distanceToSqr(eye)>1){s.origin=eye;s.yaws=null;s.point=null;s.next=now+20;}
        boolean reduced = BattlePerceptionDetail.reduced(h);
        if (s.reduced != reduced) {
            s.reduced = reduced;
            s.yaws = null;
            s.point = null;
            s.next = now;
        }
        if(s.point!=null && now<s.until)return s.point;
        if (reduced) {
            if (now < s.next) return null;
            float yaw = h.yBodyRot + (h.getRandom().nextFloat() - .5F) * 100;
            s.origin = eye;
            s.point = eye.add(Vec3.directionFromRotation(0, yaw).scale(4));
            s.until = now + 30;
            s.next = s.until + 80 + h.getRandom().nextInt(81);
            return s.point;
        }
        s.point=null;
        if(s.yaws==null) {
            if(now<s.next)return null;
            s.origin=eye;s.index=0;s.best=null;s.openness=0;
            float base=h.yBodyRot,jitter=h.getRandom().nextFloat()*30-15;
            boolean rear=h.getRandom().nextDouble()<c.number("attention.survey_rear_chance");
            s.rear=rear;
            s.yaws=new float[]{base+jitter,base-65+jitter,base+65+jitter,
                    base+(rear?165:-30)+jitter,base+(rear?-165:30)+jitter};
        }
        // One ray each idle tick; shortage preserves work without claiming an open view.
        if(!PerceptionBudget.claim(h,"rays",1,":idle-survey"))return null;
        Vec3 end=s.origin.add(Vec3.directionFromRotation(0,s.yaws[s.index++]).scale(c.number("attention.survey_distance")));
        var hit=h.level().clip(new ClipContext(s.origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,h));
        double length=hit.getType()==HitResult.Type.MISS?s.origin.distanceTo(end):s.origin.distanceTo(hit.getLocation());
        double quality=length+(s.rear && s.index>=4?2:0);
        if(length>=c.number("attention.survey_min_clearance") && quality>s.openness){
            s.openness=quality;s.best=s.origin.add(end.subtract(s.origin).normalize().scale(Math.max(1,length-.25)));
        }
        if(s.index<s.yaws.length)return null;
        s.yaws=null;s.point=s.best;
        double turn=s.point==null?0:Math.abs(net.minecraft.util.Mth.wrapDegrees(
                Math.toDegrees(Math.atan2(s.point.z-eye.z,s.point.x-eye.x))-90-h.yBodyRot));
        long turnTicks=(long)Math.ceil(turn/Math.max(.01,HumanAttention.surveyStep(h)));
        s.until=now+turnTicks+60+h.getRandom().nextInt(41);
        s.next=s.until+60+h.getRandom().nextInt(81);
        return s.point;
    }
}
