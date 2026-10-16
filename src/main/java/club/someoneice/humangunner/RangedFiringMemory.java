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
    static final int MEMORY_TICKS=200, RETRY_TICKS=40, ROUTE_TICKS=100;
    static final double MAX_DISTANCE_SQR=256, CLOSE_DISTANCE_SQR=16;
    private static final Map<Human, State> STATES=new WeakHashMap<>();
    private static final class State {
        UUID target; ResourceKey<Level> dimension; Item weapon;
        Vec3 feet, progress; int until, nextTry, failures, started, progressTick, nextCheck;
        boolean currentShot; int shotTick; double maximumRangeSqr;
        Path route;
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
            release(h,s);
            s.feet=h.position();s.until=h.tickCount+MEMORY_TICKS;s.failures=0;s.nextTry=h.tickCount;
        }
        if(s!=null){s.currentShot=usable;s.shotTick=h.tickCount;s.maximumRangeSqr=maximumRangeSqr;}
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
        if(s.currentShot && s.shotTick==h.tickCount){release(h,s);return false;}
        if(h.position().distanceToSqr(s.feet)>MAX_DISTANCE_SQR || !SoldierOrder.allowsCombatPosition(h,s.feet)){
            release(h,s);STATES.remove(h);return false;
        }
        if(CombatTerrainNavigation.ownsRoute(h)) {release(h,s);return false;}
        if(s.route!=null) {
            Path current=h.getNavigation().getPath();
            if(!MovementContinuity.ownsRoute(h,s.route)) {s.route=null;s.nextTry=h.tickCount+RETRY_TICKS;return false;}
            if(h.position().distanceToSqr(s.progress)>.09){s.progress=h.position();s.progressTick=h.tickCount;}
            if(current==null||current.isDone()||h.getNavigation().isStuck()
                    ||h.tickCount-s.started>=ROUTE_TICKS||h.tickCount-s.progressTick>=40){
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
        Path path=h.getNavigation().createPath(BlockPos.containing(s.feet),0);
        if(!validPath(h,t,path)||!RetreatPathSafety.tryMove(h,path,speed)){s.failures++;return false;}
        s.route=path;s.started=s.progressTick=h.tickCount;s.progress=h.position();s.nextCheck=h.tickCount+10;
        MovementContinuity.cancelStep(h);h.clearRangedStrafeMotion();
        NavigationDebugService.candidate(h,s.feet,"firing","return_recent",Double.NaN);
        return true;
    }
    private static void failed(Human h,State s){release(h,s);s.failures++;s.nextTry=h.tickCount+RETRY_TICKS;}
    private static boolean validPath(Human h,LivingEntity t,Path p){
        if(p==null||!p.canReach()||p.isDone()||p.getEndNode()==null||!HumanTerrainSafety.safePath(h,p)
                ||!SoldierOrder.allowsCombatPath(h,p))return false;
        var n=p.getEndNode();Vec3 feet=new Vec3(n.x+.5,n.y,n.z+.5);
        return h.level().hasChunkAt(BlockPos.containing(feet))
                && h.level().noCollision(h,h.getBoundingBox().move(feet.subtract(h.position())))
                && t.distanceToSqr(feet)<=STATES.get(h).maximumRangeSqr
                && RangedShotClearance.fromPosition(h,feet,t);
    }
}
