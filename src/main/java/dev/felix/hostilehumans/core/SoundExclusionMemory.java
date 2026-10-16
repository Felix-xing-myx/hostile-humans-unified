package dev.felix.hostilehumans.core;

import java.util.ArrayList;
import java.util.List;

/** Bounded listener-local, dimension-local sound failures. Ignored notices never extend expiry. */
public final class SoundExclusionMemory {
    public record Region(String dimension,double x,double y,double z,double listenerX,double listenerY,
                         double listenerZ,long until,double radius,double vertical,double resetDistance) {}
    private final List<Region> regions=new ArrayList<>();
    public static boolean sound(String kind) {
        return switch(kind) {case "walk","run","swim","dig","block","door","damage","aid","explosion","gunshot"->true;default->false;};
    }
    private static boolean finite(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    public void prune(String dimension,long now,double x,double y,double z){
        regions.removeIf(r->!r.dimension.equals(dimension)||now>=r.until
                ||Math.pow(x-r.listenerX,2)+Math.pow(y-r.listenerY,2)+Math.pow(z-r.listenerZ,2)>r.resetDistance*r.resetDistance);
    }
    public boolean blocked(String dimension,long now,double lx,double ly,double lz,double x,double y,double z){
        prune(dimension,now,lx,ly,lz);
        if(!finite(x,y,z))return false;
        return regions.stream().anyMatch(r->Math.abs(y-r.y)<=r.vertical
                &&Math.pow(x-r.x,2)+Math.pow(z-r.z,2)<=r.radius*r.radius);
    }
    public void fail(String dimension,long now,double lx,double ly,double lz,double x,double y,double z,
                     int ticks,double radius,double vertical,double resetDistance,int maximum){
        if(!finite(x,y,z)||!finite(lx,ly,lz)||ticks<=0||radius<=0||vertical<0||resetDistance<=0||maximum<=0)return;
        if(blocked(dimension,now,lx,ly,lz,x,y,z))return;
        while(regions.size()>=maximum)regions.remove(0);
        regions.add(new Region(dimension,x,y,z,lx,ly,lz,now+ticks,radius,vertical,resetDistance));
    }
    public List<Region> regions(){return List.copyOf(regions);}
}
