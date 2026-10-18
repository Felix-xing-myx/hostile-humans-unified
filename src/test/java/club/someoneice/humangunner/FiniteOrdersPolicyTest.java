package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.RouteProgressPolicy;
import java.util.UUID;

/** Pure policy checks; no world, chunk loading or optional mod APIs required. */
public final class FiniteOrdersPolicyTest {
    private static int checks;
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        GuardCombatState guard=new GuardCombatState();UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        guard.tick(100,true,false,true,400);guard.attacked(first,200,true);guard.attacked(second,499,true);
        check(guard.outsideSince()==100,"new hits cannot restart the first excursion clock");
        guard.tick(499,true,false,true,400);check(!guard.returning(),"399 executing ticks remain legal");
        guard.tick(500,true,false,true,400);check(guard.returning(),"400 ticks force return despite repeated hits");
        guard.attacked(first,510,true);check(guard.returning(),"self-defense cannot cancel return");
        guard.returnedToPost(520,80);check(!guard.canExcursion(599)&&guard.canExcursion(600),"80-tick cooldown boundary");
        guard.tick(550,false,false,true,400);check(!guard.returning(),"inside daily circle remains legal during cooldown");
        guard.tick(551,true,false,true,400);check(guard.returning(),"no early new excursion");
        guard.reset();guard.tick(600,true,false,true,400);guard.tick(601,false,false,true,400);
        check(guard.outsideSince()==600,"crossing inside during the same fight does not renew clock");
        guard.tick(1000,false,false,true,400);check(guard.returning(),"repeated circle crossings cannot extend 400 ticks");
        guard.reset();guard.tick(10,true,false,true,400);guard.tick(11,true,false,false,400);
        check(guard.returning(),"loss of legal target returns immediately");
        guard.reset();guard.tick(20,false,true,true,400);check(guard.returning(),"touching hard outer boundary returns");
        guard.bindReturnRadius(52);guard.bindReturnRadius(60);
        check(guard.returnRadius()==52,"return detour envelope cannot expand after local outward steps");
        guard.reset();guard.tick(500,true,false,true,400);guard.tick(400,true,false,true,400);
        check(guard.returning(),"time rollback cannot extend excursion");
        check(RouteProgressPolicy.executedTicks(70,10,11,true)==71,"active adjacent ticks count");
        check(RouteProgressPolicy.executedTicks(70,10,11,false)==70,"budget wait pauses point timeout");
        check(RouteProgressPolicy.executedTicks(70,10,1000,true)==70,"combat/unloaded gap is not executing time");
        var loop=RouteProgressPolicy.advance(2,1,3,"loop",false,0,100,100);
        check(loop.index()==0&&!loop.completed(),"loop wraps");
        var reverse=RouteProgressPolicy.advance(2,1,3,"ping_pong",false,0,100,100);
        check(reverse.index()==1&&reverse.direction()==-1,"ping pong reverses at final point");
        var forward=RouteProgressPolicy.advance(0,-1,3,"ping_pong",false,0,100,100);
        check(forward.index()==1&&forward.direction()==1,"ping pong reverses at first point");
        var once=RouteProgressPolicy.advance(2,1,3,"once",false,0,100,100);
        check(once.completed()&&once.index()==2,"once final arrival retains completion");
        var fail=RouteProgressPolicy.advance(2,1,3,"loop",true,2,100,100);
        check(fail.retryAt()==200&&fail.failures()==0&&!fail.completed(),"whole route failure waits 100 ticks");
        var partial=RouteProgressPolicy.advance(0,1,3,"once",true,0,100,100);
        check(partial.index()==1&&partial.retryAt()==0&&!partial.completed(),"single failed point skips without completing route");
        check(BodyguardPolicy.attackInRange(9,10000,true,false,true,9409,16384,64),"legal stationary sniper keeps weapon reach outside owner activity circle");
        check(!BodyguardPolicy.attackInRange(64.01,10000,true,false,true,9409,16384,64),"sniper body cannot leave configured activity circle");
        check(!BodyguardPolicy.attackInRange(9,10000,true,false,false,9409,16384,64),"stationary reach grants no attack permission");
        System.out.println("FiniteOrdersPolicyTest: "+checks+" checks passed");
    }
}
