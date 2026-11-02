package dev.felix.hostilehumans.perception;

import java.util.ArrayList;
import java.util.List;

/** Bounded listener-local, dimension-local sound failures. Ignored notices never extend expiry. */
public final class SoundExclusionMemory {
    public record Region(String dimension,double x,double y,double z,double listenerX,double listenerY,
                         double listenerZ,long until,double radius,double vertical,double resetDistance,int duration,long rememberUntil) {}
    private final List<Region> regions=new ArrayList<>();
    public static boolean sound(String kind) {
        return switch(kind) {case "walk","run","swim","dig","block","door","damage","aid","explosion","gunshot","bowshot","crossbowshot",
                "throw","impact","landing","splash","container","mechanism","eat","drink","equipment","reload",
                "fuse","death","roar","teleport","instrument","shriek","thunder"->true;default->false;};
    }
    private static boolean finite(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    public void prune(String dimension,long now,double x,double y,double z){
        regions.removeIf(r->!r.dimension.equals(dimension)||now>=r.rememberUntil
                ||Math.pow(x-r.listenerX,2)+Math.pow(y-r.listenerY,2)+Math.pow(z-r.listenerZ,2)>r.resetDistance*r.resetDistance);
    }
    public boolean blocked(String dimension,long now,double lx,double ly,double lz,double x,double y,double z){
        prune(dimension,now,lx,ly,lz);
        if(!finite(x,y,z))return false;
        return regions.stream().anyMatch(r->now<r.until&&Math.abs(y-r.y)<=r.vertical
                &&Math.pow(x-r.x,2)+Math.pow(z-r.z,2)<=r.radius*r.radius);
    }
    public void fail(String dimension,long now,double lx,double ly,double lz,double x,double y,double z,
                     int ticks,double radius,double vertical,double resetDistance,int maximum){
        fail(dimension,now,lx,ly,lz,x,y,z,ticks,ticks,0,radius,vertical,resetDistance,maximum);
    }
    public void fail(String dimension,long now,double lx,double ly,double lz,double x,double y,double z,
                     int ticks,int maximumTicks,int historyTicks,double radius,double vertical,double resetDistance,int maximum){
        if(!finite(x,y,z)||!finite(lx,ly,lz)||ticks<=0||maximumTicks<=0||historyTicks<0
                ||!Double.isFinite(radius)||!Double.isFinite(vertical)||!Double.isFinite(resetDistance)
                ||radius<=0||vertical<0||resetDistance<=0||maximum<=0)return;
        if(blocked(dimension,now,lx,ly,lz,x,y,z))return;
        int duration=Math.min(ticks,maximumTicks);
        for(Region old:regions)if(overlaps(old,x,y,z,radius,vertical))
            duration=(int)Math.min(maximumTicks,Math.max((long)duration,2L*old.duration));
        // Replace expired overlapping history; keep active coverage of neighboring regions.
        regions.removeIf(old->now>=old.until&&overlaps(old,x,y,z,radius,vertical));
        while(regions.size()>=maximum)regions.remove(0);
        long until=now+duration;
        regions.add(new Region(dimension,x,y,z,lx,ly,lz,until,radius,vertical,resetDistance,duration,until+historyTicks));
    }
    private static boolean overlaps(Region old,double x,double y,double z,double radius,double vertical){
        return Math.abs(y-old.y)<=vertical+old.vertical
                &&Math.pow(x-old.x,2)+Math.pow(z-old.z,2)<=Math.pow(radius+old.radius,2);
    }
    public List<Region> regions(){return List.copyOf(regions);}
}
