package dev.felix.hostilehumans.performance;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;

import dev.felix.hostilehumans.performance.ActivityLoadPolicy;
import dev.felix.hostilehumans.performance.ActivityLoadPolicy.Mode;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Heartbeat census, not a world scan. Values retain no entity, level or server references. */
public final class AdaptiveActivityBudget {
    private static final Map<MinecraftServer,State> SERVERS=new WeakHashMap<>();
    private static final class State {
        final Map<UUID,Integer> present=new HashMap<>();
        int sampled=Integer.MIN_VALUE,count,lowerSince=-1;
        Mode mode=Mode.SPARSE,lower;
    }
    private AdaptiveActivityBudget() {}
    private static ActivityBudgetConfig c(){return UnifiedConfig.get().activityBudget();}
    public static void touch(Human h) {
        MinecraftServer server=h.level().getServer();if(server==null||h.level().isClientSide)return;
        State s=SERVERS.computeIfAbsent(server,k->new State());int tick=server.getTickCount();var c=c();
        if(tick<s.sampled){s.present.clear();s.sampled=Integer.MIN_VALUE;s.mode=Mode.SPARSE;s.lower=null;s.lowerSince=-1;}
        if(h.isAlive()&&!h.isRemoved())s.present.put(h.getUUID(),tick);else s.present.remove(h.getUUID());
        if(s.sampled!=Integer.MIN_VALUE&&tick-s.sampled<c.sampleTicks())return;
        s.sampled=tick;s.present.values().removeIf(last->tick-last>c.staleTicks());s.count=s.present.size();
        Mode desired=ActivityLoadPolicy.desired(s.count,c.sparseHumans(),c.crowdedHumans(),c.battleHumans());
        if(desired.ordinal()>s.mode.ordinal()){s.mode=desired;s.lower=null;s.lowerSince=-1;}
        else if(desired==s.mode){s.lower=null;s.lowerSince=-1;}
        else {
            if(s.lower!=desired){s.lower=desired;s.lowerSince=tick;}
            if(tick-s.lowerSince>=c.downgradeTicks()){s.mode=desired;s.lower=null;s.lowerSince=-1;}
        }
    }
    public static Mode mode(Human h) { return mode(h.level().getServer()); }
    public static Mode mode(MinecraftServer server) {
        if(!c().enabled())return Mode.NORMAL;
        State s=SERVERS.get(server);return s==null?Mode.SPARSE:s.mode;
    }
    public static int interval(Human h,int base,boolean urgent) {
        var c=c();double factor=switch(mode(h)){case SPARSE->c.sparseInterval();case CROWDED->c.crowdedInterval();case BATTLE->c.battleInterval();default->1;};
        return ActivityLoadPolicy.interval(base,factor,urgent);
    }
    public static int probes(Human h,int base) {
        return switch(mode(h)){case SPARSE->Math.min(16,base*c().sparseProbes());case BATTLE->Math.min(2,base);case CROWDED->Math.min(3,base);default->base;};
    }
    public static int bodyRays(Human h,int base) {
        return switch(mode(h)){case CROWDED->Math.min(base,c().crowdedBodyRays());case BATTLE->Math.min(base,c().battleBodyRays());default->base;};
    }
    public static int candidates(Human h,int base) {
        return switch(mode(h)){case CROWDED->Math.min(base,16);case BATTLE->Math.min(base,12);default->base;};
    }
    public static int sources(Human h) {
        return switch(mode(h)){case SPARSE->Math.min(64,c().normalSources()*2);case CROWDED->c().crowdedSources();case BATTLE->c().battleSources();default->c().normalSources();};
    }
    public static int navigationCapacity(Human h,int maximum,int minimumJob) {
        return navigationCapacity(h.level().getServer(),maximum,minimumJob);
    }
    public static int navigationCapacity(MinecraftServer server,int maximum,int minimumJob) {
        double factor=switch(mode(server)){case CROWDED->c().crowdedNavigation();case BATTLE->c().battleNavigation();default->1;};
        return ActivityLoadPolicy.capacity(maximum,minimumJob,factor);
    }
    public static String describe(Human h) {
        return describe(h.level().getServer());
    }
    public static String describe(MinecraftServer server) {
        State s=SERVERS.get(server);return mode(server).name()+" · 心跳人类"+(s==null?0:s.count);
    }
}
