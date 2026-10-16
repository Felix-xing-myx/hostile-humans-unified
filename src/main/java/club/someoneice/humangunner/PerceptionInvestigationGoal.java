package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Position-only investigation: never resolves the source UUID into a living target. */
public class PerceptionInvestigationGoal extends Goal {
    private final Human h;private final double speed;
    private HumanPerception.Clue clue;private Path ownPath;private Vec3 origin,destination,plannedCenter;
    private long started,nextAttempt,arrivedAt=-1,lastTick,worked;private boolean beganWork,planning;private int points;
    public PerceptionInvestigationGoal(Mob mob,double speed){h=mob instanceof Human human?human:null;this.speed=speed;setFlags(EnumSet.of(Flag.MOVE));}
    private PerceptionConfig c(){return PerceptionConfig.get();}
    @Override public boolean canUse(){return h!=null&&!h.isNoAi()&&!h.isSleeping()&&!h.isFleeing&&h.getTarget()==null
            &&!NavigationDebugService.controls(h)
            &&!SoldierOrder.isHoldingPosition(h)&&HumanPerception.bestClue(h)!=null;}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){clue=HumanPerception.bestClue(h);origin=h.position();started=HumanPerception.now(h);nextAttempt=started;points=clue==null?0:clue.searchedPoints;arrivedAt=-1;ownPath=null;worked=clue==null?0:clue.searchedTicks;beganWork=false;planning=false;lastTick=started;}
    @Override public boolean canContinueToUse(){
        if(!canUse()||clue==null||clue.completed>=0)return false;long now=HumanPerception.now(h);
        if(now>=clue.until||worked>=c().integer("investigation.duration_ticks")||clue.attempts>=c().integer("investigation.maximum_retries"))return false;
        var best=HumanPerception.bestClue(h);
        return best==clue||now-started<c().integer("investigation.hold_ticks")||best!=null&&best.priority<clue.priority;
    }
    @Override public void tick(){
        if(clue==null)return;long now=HumanPerception.now(h);
        if(beganWork)worked+=Math.max(0,now-lastTick);lastTick=now;
        HumanPerception.decision(h,"调查 "+clue.kind,"位置线索，不读取来源实体");
        HumanPerception.lookAtClue(h,clue);
        if(plannedCenter!=null&&plannedCenter.distanceToSqr(clue.position)>Math.pow(c().number("investigation.repath_distance"),2)
                &&now>=nextAttempt){
            if(ownPath!=null&&h.getNavigation().getPath()==ownPath)h.getNavigation().stop();
            ownPath=null;arrivedAt=-1;planning=false;
            LongRangeNavigation.cancel(h,LongRangeNavigation.Purpose.INVESTIGATION);
        }
        Path current=h.getNavigation().getPath();
        boolean owned=ownPath!=null&&current!=null&&(current==ownPath||MovementContinuity.ownsRoute(h,ownPath));
        if(owned)ownPath=current;
        boolean lostRoute=ownPath!=null&&!ownPath.isDone()&&!owned;
        boolean refreshing=lostRoute||owned&&!ownPath.isDone()
                &&h.getNavigation() instanceof com.craftix.hostile_humans.entity.ai.HumanNavigation ground && ground.needsBoundedRefresh();
        if(owned&&!ownPath.isDone()&&!refreshing)return;
        if(refreshing)planning=true;
        if(!refreshing&&arrivedAt<0&&ownPath!=null)arrivedAt=now;
        if(!refreshing&&arrivedAt>=0&&now-arrivedAt<20)return;
        if(!refreshing&&points>=c().integer("investigation.maximum_points")){HumanPerception.finishClue(h,clue,"搜索结束，未目视发现敌人");return;}
        if(now<nextAttempt)return;
        if(!planning) {
            double radius=c().number(clue.kind.equals("beacon")?"beacon.search_radius":"investigation.radius");Vec3 aim=clue.position;
            if(points>0){double angle=(h.getUUID().hashCode()%360)*Math.PI/180+points*Math.PI;aim=aim.add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);}
            destination=safeFloor(aim);plannedCenter=clue.position;arrivedAt=-1;ownPath=null;
            if(destination==null){points++;clue.attempts++;nextAttempt=now+c().integer("investigation.retry_ticks");HumanPerception.decision(h,"调查受限","无安全落点或行动命令不允许");return;}
            planning=true;
        }
        var query=LongRangeNavigation.search(h,BlockPos.containing(destination),LongRangeNavigation.Purpose.INVESTIGATION,1);
        if(query.pending()){HumanPerception.decision(h,"等待调查路径","分档或配额等待，不算不可达");beganWork=false;nextAttempt=now+1;return;}
        Path candidate=query.path();
        if(query.status()==LongRangeNavigation.Status.UNSUPPORTED) {
            if(!BasicNavigationBudget.tryAcquire(h,1)){beganWork=false;nextAttempt=now+1;return;}
            BasicNavigationBudget.cancel(h);candidate=h.getNavigation().createPath(BlockPos.containing(destination),1);
        }
        planning=false;if(!refreshing)points++;beganWork=true;nextAttempt=now+c().integer("investigation.repath_ticks");
        if(candidate==null||!candidate.canReach()||!HumanTerrainSafety.safePath(h,candidate)||!RetreatPathSafety.tryMove(h,candidate,speed)){
            clue.attempts++;nextAttempt=now+c().integer("investigation.retry_ticks");HumanPerception.decision(h,"调查不可达","保留安全/通道限制，稍后有限重试");return;
        }ownPath=h.getNavigation().getPath();LongRangeNavigation.accepted(h,LongRangeNavigation.Purpose.INVESTIGATION);
    }

    private Vec3 safeFloor(Vec3 point){
        if(!(h.level() instanceof ServerLevel level))return null;
        int vertical=clue.kind.equals("beacon")?c().integer("beacon.vertical_search"):4;BlockPos seed=BlockPos.containing(point);
        for(int distance=0;distance<=vertical;distance++)for(int sign:distance==0?new int[]{1}:new int[]{1,-1}){
            BlockPos feet=seed.offset(0,distance*sign,0);
            if(!level.hasChunkAt(feet)||!level.getWorldBorder().isWithinBounds(feet)||!SafePositions.isSafe(level,feet))continue;
            Vec3 candidate=Vec3.atBottomCenterOf(feet);if(!SoldierOrder.allowsCombatPosition(h,candidate))continue;
            SoldierOrder order=SoldierOrder.get(h);
            if(h.hasOwner()&&(order==SoldierOrder.FOLLOW||order==SoldierOrder.PATROL)&&candidate.distanceToSqr(origin)>Math.pow(c().number("investigation.command_deviation"),2))continue;
            if(!RetreatPathSafety.isHazard(h,feet)&&!RetreatPathSafety.isHazard(h,feet.below())&&level.noCollision(h,h.getBoundingBox().move(candidate.subtract(h.position()))))return candidate;
        }return null;
    }
    @Override public void stop(){
        if(h==null)return;LongRangeNavigation.cancel(h,LongRangeNavigation.Purpose.INVESTIGATION);if(ownPath!=null&&h.getNavigation().getPath()==ownPath&&h.getTarget()==null)h.getNavigation().stop();
        if(clue!=null){clue.searchedTicks=worked;clue.searchedPoints=Math.max(0,points-(ownPath!=null&&!ownPath.isDone()?1:0));}
        if(clue!=null&&(HumanPerception.now(h)>=clue.until||worked>=c().integer("investigation.duration_ticks")
                ||clue.attempts>=c().integer("investigation.maximum_retries")||points>=c().integer("investigation.maximum_points")&&ownPath!=null&&ownPath.isDone()))
            HumanPerception.finishClue(h,clue,"调查达到终止条件");clue=null;ownPath=null;destination=null;plannedCenter=null;planning=false;
    }
    public boolean isRunning(){return clue!=null;}
}
