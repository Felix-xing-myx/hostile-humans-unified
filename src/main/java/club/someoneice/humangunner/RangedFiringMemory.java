package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** One recently usable shot position, not a new Goal or knowledge of unseen enemies. */
public final class RangedFiringMemory {
    static final int MEMORY_TICKS=200, RETRY_TICKS=40, ROUTE_TICKS=100, SELECTED_ROUTE_TICKS=200;
    static final double MAX_DISTANCE_SQR=256, CLOSE_DISTANCE_SQR=16;
    private static final Map<Human, State> STATES=new WeakHashMap<>();
    private static final class State {
        UUID target; ResourceKey<Level> dimension; Item weapon;
        Vec3 feet, progress; int until, nextTry, failures, started, progressTick, nextCheck;
        boolean currentShot; int shotTick; double maximumRangeSqr;
        Path route; int routeLimit=ROUTE_TICKS;
    }
    private RangedFiringMemory() {}
    private static boolean eligible(Human h,LivingEntity t) {
        return !h.level().isClientSide && h.isAlive() && t!=null && HumanPerception.canTarget(h,t)
                && !HumanTargetCoordinator.suspended(h) && !h.isFleeing && !h.isInWater() && !h.isInLava()
                && !h.isPassenger() && !h.onClimbable() && !h.isSeekingShore() && !h.isShoreTransitionPending()
                && h.explosionThreat==null && !SoldierOrder.isHoldingPosition(h)
                && !h.humanGunner$shieldDefense().shouldSuppressRecovery()
                && h.distanceToSqr(t)>CLOSE_DISTANCE_SQR;
    }
    private static boolean matches(Human h,LivingEntity t,State s) {
        return s!=null && t!=null && s.target.equals(t.getUUID()) && s.dimension.equals(h.level().dimension())
                && s.weapon==h.getMainHandItem().getItem() && h.tickCount<s.until;
    }
    private static void release(Human h,State s) {
        if(s!=null && s.route!=null && MovementContinuity.ownsRoute(h,s.route))h.getNavigation().stop();
        if(s!=null)s.route=null;
    }
    /** Caller supplies an already computed nominal lane AND effective weapon range, never a debug ray. */
    public static void observe(Human h,LivingEntity t,boolean usable,double maximumRangeSqr) {
        State s=STATES.get(h);
        if(!eligible(h,t) || s!=null&&!matches(h,t,s)) {
            release(h,s);STATES.remove(h);s=null;
            if(!eligible(h,t))return;
        }
        if(usable && h.onGround() && h.getDeltaMovement().y>=-.05) {
            if(s==null){s=new State();s.target=t.getUUID();s.dimension=h.level().dimension();
                s.weapon=h.getMainHandItem().getItem();STATES.put(h,s);}
            // A momentary lane below a selected platform must not replace the destination.
            boolean travelling=s.route!=null&&MovementContinuity.ownsRoute(h,s.route)
                    &&!arrivedAtWindow(h.position(),s.feet,h.onGround());
            if(!travelling){
                release(h,s);
                s.feet=h.position();s.until=h.tickCount+MEMORY_TICKS;s.failures=0;s.nextTry=h.tickCount;
            }
        }
        if(s!=null){s.currentShot=usable;s.shotTick=h.tickCount;s.maximumRangeSqr=maximumRangeSqr;}
    }
    static boolean arrivedAtWindow(Vec3 feet,Vec3 destination,boolean grounded) {
        return grounded&&Math.abs(feet.y-destination.y)<=.6
                &&feet.subtract(destination).horizontalDistanceSqr()<=.75*.75;
    }
    static boolean sameDestination(Path installed,Path proposed) {
        if(installed==null||proposed==null||installed.getEndNode()==null||proposed.getEndNode()==null)return false;
        var a=installed.getEndNode();var b=proposed.getEndNode();
        return a.x==b.x&&a.y==b.y&&a.z==b.z;
    }
    /** Bind the route actually retained by PathNavigation, not merely the proposed Path object. */
    public static void selected(Human h,LivingEntity t,Path proposed,double maximumRangeSqr) {
        if(!eligible(h,t)||proposed==null||proposed.getEndNode()==null)return;
        Path installed=h.getNavigation().getPath();
        if(installed==null||!installed.canReach()||!sameDestination(installed,proposed)
                ||!HumanTerrainSafety.safePath(h,installed)||!SoldierOrder.allowsCombatPath(h,installed))return;
        State s=STATES.get(h);
        if(!matches(h,t,s)){s=new State();s.target=t.getUUID();s.dimension=h.level().dimension();
            s.weapon=h.getMainHandItem().getItem();STATES.put(h,s);}
        var end=installed.getEndNode();
        Vec3 destination=HumanTerrainSafety.firingFeet(h,new BlockPos(end.x,end.y,end.z));
        if(destination==null||!h.level().noCollision(h,h.getBoundingBox().move(destination.subtract(h.position()))))return;
        s.feet=destination;
        s.until=h.tickCount+MEMORY_TICKS;s.maximumRangeSqr=maximumRangeSqr;s.failures=0;
        bind(h,s,installed,SELECTED_ROUTE_TICKS);
        HumanPerception.decision(h,"前往已确认射位","已绑定导航实际路径，等待可靠落脚而非途中一帧射界");
    }
    private static void bind(Human h,State s,Path installed,int limit) {
        s.route=installed;s.routeLimit=limit;s.started=s.progressTick=h.tickCount;
        s.progress=h.position();s.nextCheck=h.tickCount+10;
        MovementContinuity.cancelStep(h);h.clearRangedStrafeMotion();
    }
    public static String debugState(Human h){
        State s=STATES.get(h);
        return !matches(h,h.getTarget(),s)?"没有当前射位记录":(s.route==null?"射位记忆":"射位路线保持")+" · "+s.feet
                +" · 剩余"+Math.max(0,s.until-h.tickCount)+"tick · 失败"+s.failures;
    }
    static BlockPos preferredCandidate(Human h,LivingEntity t,int radius){
        State s=STATES.get(h);
        return eligible(h,t)&&matches(h,t,s)&&s.failures<2&&s.feet.distanceToSqr(h.position())<=radius*radius
                ?HumanTerrainSafety.firingNode(s.feet):null;
    }
    /** Optional tactical veto only for active local shooting steps, not retreat/pursuit or fall damage. */
    static boolean keepsFiringFloor(Human h,Vec3 step) {
        State s=STATES.get(h);LivingEntity t=h.getTarget();
        return !eligible(h,t)||!matches(h,t,s)||!s.currentShot||s.shotTick!=h.tickCount
                ||!h.onGround()||HumanTerrainSafety.maintainsFiringFloor(h,step);
    }
    /** Reuse the existing firing-lane budget and normal navigation; yield on invalid/unreachable memory. */
    public static boolean recover(Human h,LivingEntity t,double speed) {
        State s=STATES.get(h);
        if(!eligible(h,t)||!matches(h,t,s)){release(h,s);STATES.remove(h);return false;}
        if(s.route==null&&s.currentShot && s.shotTick==h.tickCount)return false;
        if(h.position().distanceToSqr(s.feet)>MAX_DISTANCE_SQR || !SoldierOrder.allowsCombatPosition(h,s.feet)){
            release(h,s);STATES.remove(h);return false;
        }
        if(CombatTerrainNavigation.ownsRoute(h)) {release(h,s);return false;}
        if(s.route!=null) {
            Path current=h.getNavigation().getPath();
            if(!MovementContinuity.ownsRoute(h,s.route)) {
                HumanPerception.decision(h,"射位路线被接管","保留外部实际路径，不把换路误认为已到射位");
                s.route=null;s.nextTry=h.tickCount+RETRY_TICKS;return false;
            }
            if(h.position().distanceToSqr(s.progress)>.09){s.progress=h.position();s.progressTick=h.tickCount;}
            if(current==null||current.isDone()||h.getNavigation().isStuck()
                    ||h.tickCount-s.started>=s.routeLimit||h.tickCount-s.progressTick>=40){
                failed(h,s);return false;
            }
            if(h.tickCount>=s.nextCheck){
                if(OptionalPathBudget.claim(h,OptionalPathBudget.Kind.FIRING_LANE,1)==0){
                    // Budget waiting is not a route failure. Final travel safety still runs every tick.
                    s.nextCheck=h.tickCount+1;return true;
                }
                s.nextCheck=h.tickCount+10;
                if(!validPath(h,t,current)){failed(h,s);return false;}
            }
            return true;
        }
        if(!h.onGround()||h.tickCount<s.nextTry||s.failures>=2||h.position().distanceToSqr(s.feet)<.25
                ||RangedFiringPosition.hasPendingVisibleSearch(h))return false;
        if(OptionalPathBudget.claim(h,OptionalPathBudget.Kind.FIRING_LANE,1)==0){
            return false;
        }
        s.nextTry=h.tickCount+RETRY_TICKS;
        if(!h.level().hasChunkAt(BlockPos.containing(s.feet))||t.distanceToSqr(s.feet)>s.maximumRangeSqr||!RangedShotClearance.fromPosition(h,s.feet,t)){
            s.failures++;return false;
        }
        Path path=h.getNavigation().createPath(HumanTerrainSafety.firingNode(s.feet),0);
        if(!validPath(h,t,path)||!RetreatPathSafety.tryMove(h,path,speed)){s.failures++;return false;}
        if(!sameDestination(h.getNavigation().getPath(),path)){s.failures++;return false;}
        bind(h,s,h.getNavigation().getPath(),ROUTE_TICKS);
        NavigationDebugService.candidate(h,s.feet,"firing","return_recent",Double.NaN);
        return true;
    }
    private static void failed(Human h,State s){release(h,s);s.failures++;s.nextTry=h.tickCount+RETRY_TICKS;}
    private static boolean validPath(Human h,LivingEntity t,Path p){
        if(p==null||!p.canReach()||p.isDone()||p.getEndNode()==null||!HumanTerrainSafety.safePath(h,p)
                ||!SoldierOrder.allowsCombatPath(h,p))return false;
        var n=p.getEndNode();Vec3 feet=HumanTerrainSafety.firingFeet(h,new BlockPos(n.x,n.y,n.z));
        if(feet==null)return false;
        return h.level().hasChunkAt(BlockPos.containing(feet))
                && h.level().noCollision(h,h.getBoundingBox().move(feet.subtract(h.position())))
                && t.distanceToSqr(feet)<=STATES.get(h).maximumRangeSqr
                && RangedShotClearance.fromPosition(h,feet,t);
    }
}
