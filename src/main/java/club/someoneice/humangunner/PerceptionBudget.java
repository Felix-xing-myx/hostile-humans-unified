package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.ScopedWorkBudget;
import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;

/** Perception work is accounted separately from path searches, with FIFO deferred grants. */
public final class PerceptionBudget {
    private static final Map<MinecraftServer,State> SERVERS=new WeakHashMap<>();
    private record Key(UUID id,String lane) {}
    private static final class State {
        final Map<String,ScopedWorkBudget<Key,String>> queues=new HashMap<>();
        final Map<String,Integer> used=new HashMap<>();
        long tick=-1;
    }
    private PerceptionBudget() {}
    public static boolean claim(Entity actor,String kind,int amount) {
        return claim(actor,kind,amount,"");
    }
    /** A separately accounted, bounded fallback batch for one target's logical body. */
    public static boolean claimBodyRays(Entity actor,UUID target,int amount) {
        String work=":body:"+target;
        boolean granted=claim(actor,"rays",amount,work);
        if(granted)cancelBodyRays(actor,target);
        return granted;
    }
    public static void cancelBodyRays(Entity actor,UUID target) {
        // Fallback is demand driven: do not pre-reserve another body batch next tick.
        State state=SERVERS.get(actor.level().getServer());
        if(state==null||!state.queues.containsKey("rays"))return;
        String lane="rays:"+actor.level().dimension().location()+":body:"+target;
        state.queues.get("rays").cancel(new Key(actor.getUUID(),lane));
    }
    public static boolean claim(Entity actor,String kind,int amount,String work) {
        MinecraftServer server=actor.level().getServer(); if(server==null || amount<1)return false;
        State s=SERVERS.computeIfAbsent(server,k->new State()); long tick=server.getTickCount();
        if(s.tick!=tick){s.tick=tick;s.used.clear();}
        PerceptionConfig c=PerceptionConfig.get(); String dim=actor.level().dimension().location().toString();
        int total=c.integer("budgets."+kind+"_per_server"), local=c.integer("budgets."+kind+"_per_level");
        String lane=kind+":"+dim; Key key=new Key(actor.getUUID(),lane+work);
        int batchLimit=kind.equals("rays")?32:16;
        ScopedWorkBudget<Key,String> q=s.queues.computeIfAbsent(kind,k->new ScopedWorkBudget<>(total,local,Math.min(batchLimit,Math.min(total,local)),40));
        if(amount>local || amount>total)return false;
        if(q.claimFull(tick,key,dim,amount)!=amount)return false;
        q.cancel(key); // Demand driven: consumed work must not reserve unused capacity next tick.
        s.used.merge(lane,amount,Integer::sum);s.used.merge(kind,amount,Integer::sum); return true;
    }
    public static String usage(Entity actor,String kind) {
        State s=SERVERS.get(actor.level().getServer());
        String lane=kind+":"+actor.level().dimension().location(); PerceptionConfig c=PerceptionConfig.get();
        boolean fresh=s!=null && actor.level().getServer()!=null && s.tick==actor.level().getServer().getTickCount();
        return (fresh?s.used.getOrDefault(lane,0):0)+"/"+c.integer("budgets."+kind+"_per_level")+
                " · 全服 "+(fresh?s.used.getOrDefault(kind,0):0)+"/"+c.integer("budgets."+kind+"_per_server");
    }
}
