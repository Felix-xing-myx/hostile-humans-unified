package dev.felix.hostilehumans.inventory;

/** A detour may increase target distance. Track both route progress and real movement. */
public final class PickupRouteProgress {
    private Object route;
    private int node, lastTick, physicalTick, routeTick;
    private double nodeDistance, x, y, z;
    public void reset(int tick,double x,double y,double z){
        route=null;node=-1;nodeDistance=Double.POSITIVE_INFINITY;
        lastTick=physicalTick=routeTick=tick;this.x=x;this.y=y;this.z=z;
    }
    public boolean stalled(int tick,Object route,int node,double distance,double x,double y,double z,boolean waiting){
        if(tick<lastTick){reset(tick,x,y,z);return false;}
        if(waiting){int elapsed=tick-lastTick;physicalTick+=elapsed;routeTick+=elapsed;lastTick=tick;return false;}
        lastTick=tick;
        double dx=x-this.x,dy=y-this.y,dz=z-this.z;
        if(dx*dx+dy*dy+dz*dz>=.04){physicalTick=tick;this.x=x;this.y=y;this.z=z;}
        boolean same=this.route==route&&this.node==node;
        if(this.route==route&&node>this.node||same&&distance<nodeDistance-.1){routeTick=tick;nodeDistance=distance;}
        else if(!same)nodeDistance=distance;
        this.route=route;this.node=node;
        return tick-physicalTick>=60||tick-routeTick>=60;
    }
    public int quietTicks(int tick){return Math.max(0,tick-Math.min(physicalTick,routeTick));}
}
