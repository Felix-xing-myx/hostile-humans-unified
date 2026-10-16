package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.*;

/** Shortcuts a proven flat corridor inside the existing Path. Never searches or owns movement. */
public final class HumanPathFollower {
    static final int LOOKAHEAD=3, INTERVAL=4, MAX_BLOCKS=128;
    static final double MAX_DISTANCE=3;
    private static final Map<Human,State> STATES=new WeakHashMap<>();
    private static final class State {
        Path path; int nextProbe,cursor,original=-1,selected=-1,nextCheck,accepted,reverted;
        Map<BlockPos,BlockState> proof;
        String reason="原版逐点跟随";
    }
    private HumanPathFollower() {}
    private static boolean eligible(Human h){
        return !h.level().isClientSide&&h.isAlive()&&!h.isNoAi()&&!h.isSleeping()&&!h.isPassenger()
                &&h.onGround()&&!h.isInWater()&&!h.isInLava()&&!h.onClimbable()&&!h.isFleeing
                &&h.explosionThreat==null&&h.hurtTime==0&&!h.isUsingItem()
                &&!h.humanGunner$hasPendingMeleeAttack()&&!HumanGunner.isManualInventoryOpen(h)
                &&Double.isFinite(h.getX())&&Double.isFinite(h.getY())&&Double.isFinite(h.getZ());
    }
    private static boolean allowed(Human h,Vec3 p){
        return h.isActivelyCollectingLoot()?SoldierOrder.allowsLootPosition(h,p.x,p.z)
                :SoldierOrder.allowsCombatPosition(h,p);
    }
    private static void dropProof(State s){s.proof=null;s.original=s.selected=-1;}
    /** Cheap changed-block guard runs before native navigation, including airborne ticks. */
    public static void beforeTick(Human h,Path path){
        State s=STATES.get(h);
        if(s==null)return;
        if(path==null||path.isDone()||s.path!=path){
            dropProof(s);s.path=path;s.nextProbe=h.tickCount;s.cursor=0;s.reason="路径变化/完成，回原跟随";
            OptionalPathBudget.cancel(h,OptionalPathBudget.Kind.PATH_FOLLOW);return;
        }
        if(s.proof==null)return;
        if(path.getNextNodeIndex()!=s.selected){dropProof(s);return;}
        if(h.tickCount<s.nextCheck)return;
        s.nextCheck=h.tickCount+INTERVAL;
        boolean valid=true;
        for(var entry:s.proof.entrySet()){
            BlockPos pos=entry.getKey();
            if(!h.level().hasChunkAt(pos)){valid=false;break;}
            BlockState current=h.level().getBlockState(pos);
            if(!current.equals(entry.getValue())){
                // Cosmetic/redstone state changes with identical collision must not cause needless U-turns.
                if(sensitive(h,pos,current)||!current.getCollisionShape(h.level(),pos).toAabbs()
                        .equals(entry.getValue().getCollisionShape(h.level(),pos).toAabbs())){valid=false;break;}
                entry.setValue(current);
            }
        }
        if(valid)valid=allowed(h,path.getNextEntityPos(h));
        if(!valid){
            path.setNextNodeIndex(s.original);s.reverted++;s.reason="通道变化，恢复原节点";
            dropProof(s);s.nextProbe=h.tickCount+INTERVAL;
        }
    }
    /** Called after native node arrival and stuck detection, before its MoveControl destination. */
    public static void follow(Human h,Path path){
        if(path==null||path.isDone()||!eligible(h)){
            OptionalPathBudget.cancel(h,OptionalPathBudget.Kind.PATH_FOLLOW);return;
        }
        State s=STATES.computeIfAbsent(h,k->new State());
        if(s.path!=path){s.path=path;dropProof(s);s.nextProbe=h.tickCount;s.cursor=0;}
        if(s.proof!=null||h.tickCount<s.nextProbe)return;
        int from=path.getNextNodeIndex(),span=Math.min(LOOKAHEAD,path.getNodeCount()-1-from);
        if(span<1){OptionalPathBudget.cancel(h,OptionalPathBudget.Kind.PATH_FOLLOW);return;}
        Node first=path.getNode(from);
        if(first.type!=BlockPathTypes.WALKABLE||Math.abs(first.y-h.getY())>.6){
            OptionalPathBudget.cancel(h,OptionalPathBudget.Kind.PATH_FOLLOW);return;
        }
        int budget=OptionalPathBudget.claim(h,OptionalPathBudget.Kind.PATH_FOLLOW,Math.min(2,span));
        if(budget==0){s.nextProbe=h.tickCount+1;s.reason="等跟随额度，保留原节点";return;}
        s.nextProbe=h.tickCount+INTERVAL;
        for(int attempt=0;attempt<Math.min(span,Math.min(2,budget));attempt++){
            int ahead=span-Math.floorMod(s.cursor++,span),to=from+ahead;
            Vec3 destination=path.getEntityPosAtNode(h,to);
            double distance=destination.subtract(h.position()).horizontalDistance();
            if(!Double.isFinite(distance)||distance<.25||distance>MAX_DISTANCE)continue;
            boolean flat=true;
            for(int index=from;index<=to;index++){
                Node n=path.getNode(index);
                if(n.type!=BlockPathTypes.WALKABLE||n.y!=first.y){flat=false;break;}
            }
            if(!flat)continue;
            Map<BlockPos,BlockState> proof=prove(h,destination);
            if(proof==null){s.reason="通道未通过，原版跟随";continue;}
            path.setNextNodeIndex(to);s.original=from;s.selected=to;s.proof=proof;
            s.nextCheck=h.tickCount+INTERVAL;s.accepted++;s.reason="安全直达节点 "+from+"→"+to;
            NavigationDebugService.candidate(h,new Vec3(destination.x,h.getY(),destination.z),"follow","accepted",Double.NaN);
            break;
        }
        OptionalPathBudget.cancel(h,OptionalPathBudget.Kind.PATH_FOLLOW);
    }
    private static Map<BlockPos,BlockState> prove(Human h,Vec3 destination){
        Vec3 offset=new Vec3(destination.x-h.getX(),0,destination.z-h.getZ());
        AABB envelope=h.getBoundingBox().expandTowards(offset);
        BlockPos low=BlockPos.containing(envelope.minX,Math.floor(h.getY())-1,envelope.minZ);
        BlockPos high=BlockPos.containing(envelope.maxX,envelope.maxY,envelope.maxZ);
        long volume=(long)(high.getX()-low.getX()+1)*(high.getY()-low.getY()+1)*(high.getZ()-low.getZ()+1);
        if(volume>MAX_BLOCKS||volume<1||!h.level().hasChunksAt(low,high)
                ||!h.level().getWorldBorder().isWithinBounds(low)||!h.level().getWorldBorder().isWithinBounds(high))return null;
        Map<BlockPos,BlockState> proof=new LinkedHashMap<>();
        for(BlockPos pos:BlockPos.betweenClosed(low,high)){
            BlockState state=h.level().getBlockState(pos);
            if(sensitive(h,pos,state))return null;
            proof.put(pos.immutable(),state);
        }
        boolean clear=dev.felix.hostilehumans.core.LocalPathFollowPolicy.corridor(h.getX(),h.getZ(),destination.x,destination.z,
                (x,z,stepX,stepZ)->{
                    Vec3 point=new Vec3(x,h.getY(),z);
                    AABB body=h.getBoundingBox().move(x-h.getX(),0,z-h.getZ()).expandTowards(stepX,0,stepZ);
                    return allowed(h,point)&&HumanTerrainSafety.flatSupport(h,point,h.getY())
                            &&h.level().noCollision(h,body.deflate(1.e-5));
                });
        return clear?proof:null;
    }
    private static boolean sensitive(Human h,BlockPos pos,BlockState state){
        return !h.level().getFluidState(pos).isEmpty()||state.is(BlockTags.CLIMBABLE)
                ||state.getBlock() instanceof DoorBlock||state.getBlock() instanceof FenceGateBlock
                ||state.getBlock() instanceof TrapDoorBlock||RetreatPathSafety.isHazard(h,pos);
    }
    public static String describe(Human h){
        State s=STATES.get(h);
        return s==null?"原版跟随":s.reason+" · 平滑"+s.accepted+" / 回退"+s.reverted;
    }
}
