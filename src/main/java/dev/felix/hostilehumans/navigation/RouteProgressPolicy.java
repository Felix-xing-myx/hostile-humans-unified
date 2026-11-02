package dev.felix.hostilehumans.navigation;

/** Deterministic route progress, independent of navigation, entity loading and the NBT adapter. */
public final class RouteProgressPolicy {
    public record Next(int index,int direction,int failures,boolean completed,long retryAt) {}
    private RouteProgressPolicy() {}
    public static int executedTicks(int prior,long previousTick,long now,boolean activelyExecuting) {
        return prior+(activelyExecuting&&previousTick==now-1?1:0);
    }
    public static Next advance(int index,int direction,int count,String mode,boolean failed,int priorFailures,long now,int backoff) {
        if(count<1)throw new IllegalArgumentException("empty route");
        index=Math.floorMod(index,count);direction=direction==-1?-1:1;
        int failures=failed?priorFailures+1:0;
        if(failures>=count)return new Next(index,direction,0,false,now+backoff);
        if(mode.equals("once")&&index==count-1)
            return failed?new Next(0,direction,failures,false,now+backoff):new Next(index,direction,0,true,0);
        if(mode.equals("ping_pong")&&(index+direction<0||index+direction>=count))direction=-direction;
        return new Next(Math.floorMod(index+direction,count),direction,failures,false,0);
    }
}
