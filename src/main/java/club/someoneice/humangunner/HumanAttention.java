package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.AttentionFacingPolicy;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Final orientation boundary. Observation never owns a Goal MOVE/LOOK lease. */
public final class HumanAttention {
    private static final java.util.Map<Human,Request> REQUESTS=new java.util.WeakHashMap<>();
    private static final java.util.Map<Human,String> OWNERS=new java.util.WeakHashMap<>();
    private static final java.util.Map<Human,Integer> RETREAT_AIM=new java.util.WeakHashMap<>();
    public static void retreatAim(Human h){RETREAT_AIM.put(h,h.tickCount);}
    private record Movement(int tick,Float yaw){}
    private static final java.util.Map<Human,Movement> MOVEMENT=new java.util.WeakHashMap<>();
    public static void movement(Human h,double x,double z,boolean combatStance){
        double dx=x-h.getX(),dz=z-h.getZ();
        Float yaw=combatStance?null:dx*dx+dz*dz<1.e-8?h.getYRot():(float)Math.toDegrees(Math.atan2(dz,dx))-90;
        MOVEMENT.put(h,new Movement(h.tickCount,yaw));
    }
    private record Request(int tick,Vec3 point,int priority,String owner){}
    private HumanAttention() {}
    public static String owner(Human h){return OWNERS.getOrDefault(h,"自然朝向");}
    public static void combat(Human h,net.minecraft.world.entity.Entity target,float yaw,float pitch,int priority,String owner){
        if(target==null)return;
        Vec3 point=Float.isFinite(yaw)&&Float.isFinite(pitch)
                ?h.getEyePosition().add(Vec3.directionFromRotation(pitch,yaw).scale(8)):target.getEyePosition();
        Request old=REQUESTS.get(h);
        if(old==null||old.tick!=h.tickCount||priority>=old.priority)REQUESTS.put(h,new Request(h.tickCount,point,priority,owner));
    }

    /** Planned movement, rather than knockback velocity, owns ordinary travel yaw. */
    public static Float navigationYaw(Human human) {
        if(RETREAT_AIM.getOrDefault(human,Integer.MIN_VALUE)==human.tickCount)return null;
        Movement movement=MOVEMENT.get(human);
        if(movement!=null&&movement.tick==human.tickCount)return movement.yaw;
        var path=human.getNavigation().getPath();
        if(path==null||path.isDone())return null;
        Vec3 toward=path.getNextEntityPos(human).subtract(human.position());
        if(toward.horizontalDistanceSqr()<1.e-6)return human.getYRot();
        return (float)Math.toDegrees(Math.atan2(toward.z,toward.x))-90;
    }
    public static float movingHeadLimit(Human human) {
        return Math.min(human.getMaxHeadYRot(),(float)PerceptionConfig.get().number("attention.moving_head_angle"));
    }
    public static boolean canAim(Human human,LivingEntity target) {
        if(target==null)return false;
        Float route=navigationYaw(human);
        if(route==null)return true;
        Vec3 delta=target.getEyePosition().subtract(human.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        return Math.abs(Mth.wrapDegrees(yaw-route))<=movingHeadLimit(human);
    }
    public static void finish(Human human,Vec3 observation,boolean urgent) {
        if(human.level().isClientSide||human.isNoAi()||human.isSleeping())return;
        Float route=navigationYaw(human);
        LivingEntity target=human.getTarget();
        boolean combat=HumanPerception.canTarget(human,target);
        Vec3 point=combat&&!urgent?target.getEyePosition():observation;
        String owner=combat&&!urgent?"主目标观察":"辅助观察请求";
        Request request=REQUESTS.get(human);
        if(request!=null&&request.tick==human.tickCount){point=request.point;owner=request.owner;}
        else REQUESTS.remove(human);
        if(NavigationDebugService.controls(human)){point=null;combat=false;}
        if(human.isFleeing&&(request==null||request.tick!=human.tickCount))point=null;
        OWNERS.put(human,route==null?owner:"导航身体 / "+owner);
        float body=route==null?human.yBodyRot:route;
        float limit=route==null?human.getMaxHeadYRot():movingHeadLimit(human);
        if(route!=null){human.setYRot(body);human.setYBodyRot(body);}
        if(point==null){
            human.setYHeadRot(body+Mth.clamp(Mth.wrapDegrees(human.getYHeadRot()-body),-limit,limit));
            return;
        }
        Vec3 delta=point.subtract(human.getEyePosition());
        if(delta.lengthSqr()<1.e-8)return;
        float yaw=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
        var facing=AttentionFacingPolicy.step(body,human.getYHeadRot(),human.getXRot(),yaw,pitch,limit,route==null);
        if(route==null){human.setYRot(facing.body());human.setYBodyRot(facing.body());}
        human.setYHeadRot(facing.head());human.setXRot(facing.pitch());
    }
}
