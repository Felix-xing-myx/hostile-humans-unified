package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.navigation.HumanTerrainSafety;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Deterministic, loaded-only hints. A hint grants neither a shot nor a path. */
final class RangedWindowCandidates {
    static final int MAX_CANDIDATES=96;
    private static final int MAX_INSPECTIONS=192;
    private static final class Seeds {
        final LinkedHashSet<BlockPos> points=new LinkedHashSet<>();int inspected,inspectionLimit=64;
        boolean available(int cap){return points.size()<cap&&inspected<Math.min(MAX_INSPECTIONS,inspectionLimit);}
    }
    private RangedWindowCandidates() {}
    static List<BlockPos> collect(Human h,LivingEntity target,int radius,int attempts){
        Seeds seeds=new Seeds();radius=Math.max(1,Math.min(16,radius));
        int cap=Math.min(MAX_CANDIDATES,Math.max(24,attempts*4));
        cap=switch(AdaptiveActivityBudget.mode(h)){case CROWDED->Math.max(16,cap/2);case BATTLE->Math.max(16,cap/3);default->cap;};
        Vec3 eye=h.getEyePosition(),aim=target.position().add(0,target.getBbHeight()*.85,0);
        // One initial obstruction probe; detailed ballistics stays in the budgeted evaluator.
        BlockHitResult hit=h.level().clip(new ClipContext(eye,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,h));
        if(hit.getType()==HitResult.Type.BLOCK&&hit.getDirection().getAxis()!=Direction.Axis.Y){
            Direction side=hit.getDirection();Vec3 normal=new Vec3(side.getStepX(),0,side.getStepZ());
            Vec3 tangent=new Vec3(-normal.z,0,normal.x);
            int span=Math.min(radius,12),apertureCap=Math.max(4,cap/3);
            for(int offset=0;offset<=span&&seeds.available(apertureCap);offset++){
                for(int sign:offset==0?new int[]{1}:new int[]{-1,1})for(int dy:new int[]{0,1,-1}){
                    BlockPos opening=hit.getBlockPos().offset((int)tangent.x*offset*sign,dy,(int)tangent.z*offset*sign);
                    if(!h.level().hasChunkAt(opening)||!h.level().getBlockState(opening).getCollisionShape(h.level(),opening).isEmpty())continue;
                    Vec3 window=Vec3.atCenterOf(opening);
                    for(double depth:new double[]{1,2,4}){
                        // Project the target-window line onto a standing eye position on our side.
                        Vec3 candidateEye=project(aim,window,normal,depth);
                        if(candidateEye==null)continue;
                        add(h,seeds,BlockPos.containing(candidateEye.x,h.getY(),candidateEye.z),radius,apertureCap);
                    }
                }
            }
        }
        // Reserve close cells and broader probes; failed searches do not repeat only the same close ring.
        seeds.inspectionLimit=112;
        BlockPos origin=h.blockPosition();int closeCap=Math.min(cap,seeds.points.size()+Math.max(4,cap/4));
        for(int ring=1;ring<=Math.min(2,radius)&&seeds.available(closeCap);ring++){
            for(int x=-ring;x<=ring&&seeds.available(closeCap);x++)for(int z=-ring;z<=ring&&seeds.available(closeCap);z++){
                if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
                for(int y:new int[]{0,1,-1})add(h,seeds,origin.offset(x,y,z),radius,closeCap);
            }
        }
        seeds.inspectionLimit=MAX_INSPECTIONS;
        int[][] directions={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
        int phase=Math.floorMod(h.tickCount/40,8);
        for(int step=0;step<8&&seeds.available(cap);step++){
            int[] direction=directions[(step+phase)%8];
            for(int ring=3;ring<=radius&&seeds.available(cap);ring++){
                for(int y:new int[]{0,1,-1})add(h,seeds,origin.offset(direction[0]*ring,y,direction[1]*ring),radius,cap);
            }
        }
        return List.copyOf(seeds.points);
    }
    static Vec3 project(Vec3 aim,Vec3 window,Vec3 normal,double depth){
        double along=window.subtract(aim).dot(normal);
        return !Double.isFinite(along)||along<=.1||depth<=0?null:aim.add(window.subtract(aim).scale(1+depth/along));
    }
    private static void add(Human h,Seeds seeds,BlockPos position,int radius,int cap){
        if(!seeds.available(cap))return;
        seeds.inspected++;
        if(!h.level().hasChunkAt(position))return;
        Vec3 feet=HumanTerrainSafety.firingFeet(h,position);
        if(feet==null)return;
        double distance=feet.distanceToSqr(h.position());
        if(distance<.25||distance>radius*radius)return;
        // firingFeet already proved a real supporting shape/fluid, possibly in this slab cell.
        if(!h.level().noCollision(h,h.getBoundingBox().move(feet.subtract(h.position())))
                ||!SoldierOrder.allowsCombatPosition(h,feet))return;
        seeds.points.add(HumanTerrainSafety.firingNode(feet));
    }
}
