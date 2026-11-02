package dev.felix.hostilehumans.soldier;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class PatrolRoutePolicyTest {
    private static BlockPos p(int x,int y,int z){return new BlockPos(x,y,z);}
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        check(!PatrolRoutePolicy.closed(List.of()),"empty draft is not a loop");
        check(!PatrolRoutePolicy.closed(List.of(p(0,0,0),p(4,0,0))),"open endpoints ping-pong");
        check(!PatrolRoutePolicy.closed(List.of(p(0,0,0),p(4,0,0),p(0,0,0))),"two-point round trip is not a polygon");
        check(PatrolRoutePolicy.closed(List.of(p(0,0,0),p(4,0,0),p(4,0,4),p(0,0,4),p(0,0,0))),"simple closed loop");
        check(!PatrolRoutePolicy.closed(List.of(p(-2,0,0),p(0,0,0),p(0,0,4),p(4,0,4),p(4,0,0),p(0,0,0))),"six-shaped route has open stem");
        check(!PatrolRoutePolicy.closed(List.of(p(-2,0,0),p(0,0,0),p(0,0,4),p(4,0,4),p(4,0,0),p(0,0,0),p(-2,0,0))),"closing a repeated junction does not erase its stem");
        check(!PatrolRoutePolicy.closed(List.of(p(0,0,0),p(4,0,4),p(0,0,4),p(4,0,0),p(0,0,0))),"crossed planar cycle is not a simple loop");
        check(PatrolRoutePolicy.closed(List.of(p(0,0,0),p(4,0,4),p(0,2,4),p(4,2,0),p(0,0,0))),"different-floor crossing is not a junction");
        check(!PatrolRoutePolicy.closed(List.of(p(0,0,0),p(1,0,0),p(2,0,0),p(0,0,0))),"adjacent backtracking cannot form a loop");
        check(!PatrolRoutePolicy.closed(List.of(p(0,0,0),p(4,0,0),p(4,0,4),p(2,0,0),p(0,0,4),p(0,0,0))),"touching an earlier leg creates a junction");
        Vec3 start=new Vec3(0,0,0),end=new Vec3(96,0,0);
        check(PatrolRoutePolicy.inLeg(start,end,new Vec3(80,0,0),8),"patrol can pass the old home circle along its own leg");
        check(PatrolRoutePolicy.inLeg(start,end,new Vec3(40,0,8),8),"corridor edge is inclusive");
        check(!PatrolRoutePolicy.inLeg(start,end,new Vec3(40,0,8.01),8),"corridor remains bounded");
        check(!PatrolRoutePolicy.inLeg(start,end,new Vec3(110,0,0),8),"cannot travel beyond the end cap");
        check(!PatrolRoutePolicy.inLeg(start,end,new Vec3(Double.NaN,0,0),8),"nonfinite points rejected");
        check(PatrolRoutePolicy.inLeg(start,start,new Vec3(8,0,0),8),"zero-length activation leg has a bounded circle");
        check(!PatrolRoutePolicy.mayEdit(false,false,true),"survival cannot command other or unowned soldiers");
        check(PatrolRoutePolicy.mayEdit(false,true,true),"survival may edit own stationed soldier");
        check(PatrolRoutePolicy.mayEdit(true,false,true),"creative may edit any stationed human");
        check(!PatrolRoutePolicy.mayEdit(true,true,false),"creative still needs the station mode");
        System.out.println("PatrolRoutePolicyTest passed; server/world patrol execution remains a separate acceptance step.");
    }
}
