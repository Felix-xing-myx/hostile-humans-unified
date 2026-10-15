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
        MinecraftServer server=actor.level().getServer(); if(server==null || amount<1)return false;
        State s=SERVERS.computeIfAbsent(server,k->new State()); long tick=server.getTickCount();
        if(s.tick!=tick){s.tick=tick;s.used.clear();}
        PerceptionConfig c=PerceptionConfig.get(); String dim=actor.level().dimension().location().toString();
        int total=c.integer("budgets."+kind+"_per_server"), local=c.integer("budgets."+kind+"_per_level");
        String lane=kind+":"+dim; Key key=new Key(actor.getUUID(),lane);
        ScopedWorkBudget<Key,String> q=s.queues.computeIfAbsent(kind,k->new ScopedWorkBudget<>(total,local,Math.min(16,Math.min(total,local)),40));
        if(amount>local || amount>total)return false;
        if(q.claimFull(tick,key,dim,amount)!=amount)return false;
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
