package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.ActionCoordinator;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;

import dev.felix.hostilehumans.perception.AttentionFacingPolicy;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import java.lang.ref.WeakReference;

/** Final orientation boundary. Observation never owns a Goal MOVE/LOOK lease. */
public final class HumanAttention {
    private static final java.util.Map<Human,Request> REQUESTS=new java.util.WeakHashMap<>();
    private static final java.util.Map<Human,String> OWNERS=new java.util.WeakHashMap<>();
    private static final java.util.Map<Human,Integer> RETREAT_AIM=new java.util.WeakHashMap<>();
    public static void retreatAim(Human h){RETREAT_AIM.put(h,h.tickCount);}
    private record Pose(int tick,float body,float head,float pitch){}
    private static final java.util.Map<Human,Pose> POSES=new java.util.WeakHashMap<>();
    private record Movement(int tick,Float yaw){}
    private static final java.util.Map<Human,Movement> MOVEMENT=new java.util.WeakHashMap<>();
    public static void movement(Human h,double x,double z,boolean combatStance){
        double dx=x-h.getX(),dz=z-h.getZ();
        Float yaw=combatStance?null:dx*dx+dz*dz<1.e-8?h.getYRot():(float)Math.toDegrees(Math.atan2(dz,dx))-90;
        MOVEMENT.put(h,new Movement(h.tickCount,yaw));
    }
    private record GunAim(WeakReference<LivingEntity> target, WeakReference<ItemStack> weapon,
                          ActionCoordinator actions, long session, float yaw, float pitch) {}
    private record Request(int tick,Vec3 point,int priority,String owner,boolean pressure,GunAim gunAim) {
        Request(int tick,Vec3 point,int priority,String owner) { this(tick,point,priority,owner,false,null); }
        Request(int tick,Vec3 point,int priority,String owner,boolean pressure) { this(tick,point,priority,owner,pressure,null); }
    }
    private HumanAttention() {}
    public static String owner(Human h){return OWNERS.getOrDefault(h,"自然朝向");}
    /** A known loot location may orient an idle body, never replace combat or real-hit attention. */
    public static void observeLoot(Human h,Vec3 point){
        idleObservation(h,point,-10,"已观察物品");
    }
    public static void idleGlance(Human h,Vec3 point){
        if(h.isFleeing||h.isUsingItem()||h.hhu$hasPendingMeleeAttack())return;
        idleObservation(h,point,-20,"闲置近旁观察");
    }
    private static void idleObservation(Human h,Vec3 point,int priority,String owner){
        if(point==null||h.level().isClientSide||!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z))return;
        Request old=REQUESTS.get(h);
        if(old==null||old.tick!=h.tickCount||old.priority<=priority)REQUESTS.put(h,new Request(h.tickCount,point,priority,owner));
    }
    public static void combat(Human h,net.minecraft.world.entity.Entity target,float yaw,float pitch,int priority,String owner){
        if(target==null)return;
        Vec3 point=Float.isFinite(yaw)&&Float.isFinite(pitch)
                ?h.getEyePosition().add(Vec3.directionFromRotation(pitch,yaw).scale(8)):target.getEyePosition();
        Request old=REQUESTS.get(h);
        if(old==null||old.tick!=h.tickCount||priority>=old.priority)REQUESTS.put(h,new Request(h.tickCount,point,priority,owner));
    }

    /** Called only after the gun goal's target, range and firing-lane checks. Does not acquire movement. */
    public static boolean beginGunAim(Human human, LivingEntity target, float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || target != human.getTarget()
                || !HumanPerception.allowCombatLook(human, target)
                || !GunSupport.isGunItem(human.getMainHandItem())) return false;
        Request old = REQUESTS.get(human);
        if (old != null && old.tick == human.tickCount && old.priority > 20) return false;
        ActionCoordinator actions = HumanBehaviorControl.peekActions(human);
        GunAim aim = new GunAim(new WeakReference<>(target), new WeakReference<>(human.getMainHandItem()),
                actions, actions == null ? 0 : actions.session(), yaw, pitch);
        Vec3 point = human.getEyePosition().add(Vec3.directionFromRotation(pitch, yaw).scale(8));
        REQUESTS.put(human, new Request(human.tickCount, point, 20, "枪械瞄准朝向", false, aim));
        if (hasGunAim(human)) { applyGunAim(human); return true; }
        clearGunAim(human);
        return false;
    }

    /** One tick, one exact weapon/target/session. Higher-priority requests replace the whole request. */
    public static boolean hasGunAim(Human human) {
        Request request = REQUESTS.get(human);
        if (request == null || request.tick != human.tickCount || request.gunAim == null) return false;
        GunAim aim = request.gunAim;
        LivingEntity target = aim.target.get();
        ActionCoordinator actions = HumanBehaviorControl.peekActions(human);
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !human.isSleeping() && !HumanInventorySession.isEditing(human)
                && !NavigationDebugService.controls(human)
                && target != null && target == human.getTarget() && target.isAlive() && !target.isRemoved()
                && target.level() == human.level() && aim.weapon.get() == human.getMainHandItem()
                && !human.getMainHandItem().isEmpty()
                && actions == aim.actions && (actions == null || actions.session() == aim.session)
                && HumanPerception.allowCombatLook(human, target)
                && !HumanPerception.soundGlanceActive(human)
                && !human.hhu$hasPendingMeleeAttack()
                && !human.hhu$shieldDefense().defenseOwnsFacing()
                && !(human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem()));
    }

    public static boolean applyGunAim(Human human) {
        if (!hasGunAim(human)) return false;
        GunAim aim = REQUESTS.get(human).gunAim;
        HumanTurnControl.ballistic(human,aim.target.get(),aim.yaw,aim.pitch);
        return true;
    }

    public static boolean gunAligned(Human human) {
        Request request=REQUESTS.get(human);
        return hasGunAim(human)&&HumanTurnControl.aligned(human,request.gunAim.yaw,request.gunAim.pitch);
    }

    public static void clearGunAim(Human human) {
        Request request = REQUESTS.get(human);
        if (request != null && request.gunAim != null) REQUESTS.remove(human);
    }

    /** Defensive yaw is derived from an impact, without a target entity or hidden coordinates. */
    public static void pressureFacing(Human h, float yaw) {
        if (h.level().isClientSide || !Float.isFinite(yaw)) return;
        Vec3 point = h.getEyePosition().add(Vec3.directionFromRotation(0, yaw).scale(8));
        Request old = REQUESTS.get(h);
        if (old == null || old.tick != h.tickCount || old.priority <= 40)
            REQUESTS.put(h, new Request(h.tickCount, point, 40, "高压平滑盾面", true));
    }

    /** Planned movement, rather than knockback velocity, owns ordinary travel yaw. */
    public static Float navigationYaw(Human human) {
        if (hasGunAim(human)) return null;
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
    public static void finish(Human human,Vec3 observation,boolean urgent) {finish(human,observation,urgent,false);}
    static float surveyStep(Human human) {
        return AttentionFacingPolicy.surveyStep((float)PerceptionConfig.get().number("attention.idle_head_step"),
                (float)PerceptionConfig.get().number("vision.far_full_angle"),
                AdaptiveActivityBudget.interval(human,PerceptionConfig.get().integer("budgets.idle_interval_ticks"),false));
    }
    public static void finish(Human human,Vec3 observation,boolean urgent,boolean soundGlance) {
        finish(human, observation, urgent, soundGlance, false);
    }
    public static void finish(Human human, Vec3 observation, boolean urgent, boolean soundGlance, boolean priorityGlance) {
        if(human.level().isClientSide||human.isNoAi()||human.isSleeping()){HumanTurnControl.clear(human);return;}
        boolean glance = soundGlance || priorityGlance;
        if (!priorityGlance && applyGunAim(human)) {
            OWNERS.put(human, "枪械瞄准朝向");
            remember(human);
            return;
        }
        clearGunAim(human); // An invalidated gun request must not survive as generic combat attention.
        Float route=navigationYaw(human);
        LivingEntity target=human.getTarget();
        boolean combat=HumanPerception.canTarget(human,target);
        Vec3 point=combat&&!urgent&&!glance?target.getEyePosition():observation;
        String owner=priorityGlance?"高威胁短时观察":soundGlance?"重要声音有限回看":combat&&!urgent?"主目标观察":"辅助观察请求";
        Request request=REQUESTS.get(human);
        boolean accepted=request!=null&&request.tick==human.tickCount
                &&(request.priority>=0&&(!glance||request.pressure||human.hhu$hasPendingMeleeAttack()
                ||human.hhu$shieldDefense().defenseOwnsFacing())||!combat&&!urgent&&observation==null);
        if(accepted){point=request.point;owner=request.owner;}
        else REQUESTS.remove(human);
        boolean action=accepted&&request.priority>=0;
        boolean survey=accepted&&request.priority<0&&route==null;
        if(point==null && route==null && !combat && !urgent && !action){
            point=IdleSurvey.point(human);survey=point!=null;
            if(survey)owner="自主开阔环顾";
        }
        // Shield MoveControl already converts route motion into facing-relative inputs.
        // Do not rotate its physical shield back onto the route at the final attention boundary.
        if(action&&(request.pressure||human.hhu$shieldDefense().defenseOwnsFacing()))route=null;
        if(NavigationDebugService.controls(human)){point=null;combat=false;}
        if(human.isFleeing&&(request==null||request.tick!=human.tickCount))point=null;
        OWNERS.put(human,route==null?owner:"导航身体 / "+owner);
        if(point==null&&route==null){HumanTurnControl.release(human);remember(human);return;}
        Pose previous=POSES.get(human);
        boolean stable=previous!=null&&previous.tick==human.tickCount-1;
        float body=route==null?(stable?previous.body:human.yBodyRot):route;
        float head=route==null?(stable?previous.head:human.getYHeadRot()):route;
        float pitch=stable?previous.pitch:human.getXRot();
        if(point!=null){
            Vec3 delta=point.subtract(human.getEyePosition());
            if(delta.lengthSqr()>1.e-8){
                head=(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
                pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
                if(route==null)body=head;
            }
        }
        var profile=survey
                ?dev.felix.hostilehumans.perception.TurnResponse.IDLE
                :urgent||action&&request.priority>=30?dev.felix.hostilehumans.perception.TurnResponse.URGENT
                :dev.felix.hostilehumans.perception.TurnResponse.COMBAT;
        double speed=profile==dev.felix.hostilehumans.perception.TurnResponse.IDLE?surveyStep(human):profile.speed();
        float limit=route==null?human.getMaxHeadYRot():movingHeadLimit(human);
        HumanTurnControl.apply(human,body,head,pitch,limit,profile,speed);
        remember(human);
    }
    private static void remember(Human h){POSES.put(h,new Pose(h.tickCount,h.yBodyRot,h.getYHeadRot(),h.getXRot()));}
}
