package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.performance.ScopedWorkBudget;

import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.MinecraftServer;

/** Perception work is accounted separately from path searches, with FIFO deferred grants. */
public final class PerceptionBudget {
    private static final Map<MinecraftServer,State> SERVERS=new WeakHashMap<>();
    private record Key(UUID id,String lane) {}
    private record Limits(int total, int local) {
        int maxBatch(String kind) {
            return Math.min(kind.equals("rays") ? 32 : 16, Math.min(total, local));
        }

        static Limits read(PerceptionConfig config, String kind) {
            return new Limits(config.integer("budgets."+kind+"_per_server"),
                    config.integer("budgets."+kind+"_per_level"));
        }
    }
    private static final class State {
        final Map<String,ScopedWorkBudget<Key,String>> queues=new HashMap<>();
        final Map<String,Integer> used=new HashMap<>();
        PerceptionConfig config;
        long tick=-1;

        void beginTick(long now) {
            if (tick == now) return;
            PerceptionConfig next = PerceptionConfig.get();
            if (config != null && config != next) {
                // A changed pool starts fresh. Unchanged pools keep their deferred demand.
                queues.keySet().removeIf(kind -> !Limits.read(config, kind).equals(Limits.read(next, kind)));
            }
            config = next;
            tick = now;
            used.clear();
        }
    }
    private PerceptionBudget() {}
    static int maxRayBatch(Entity actor) {
        MinecraftServer server = actor.level().getServer();
        if (server == null) return 0;
        State state = currentState(server);
        return Limits.read(state.config, "rays").maxBatch("rays");
    }

    private static State currentState(MinecraftServer server) {
        State state = SERVERS.computeIfAbsent(server, key -> new State());
        state.beginTick(server.getTickCount());
        return state;
    }

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
        State s = currentState(server);
        String dim=actor.level().dimension().location().toString();
        Limits limits = Limits.read(s.config, kind);
        int total = limits.total(), local = limits.local();
        String lane=kind+":"+dim; Key key=new Key(actor.getUUID(),lane+work);
        int batchLimit = limits.maxBatch(kind);
        if (amount > batchLimit) return false;
        // Keep ambient queries, loot and sound probes from filling the visual queue.
        boolean auxiliary = kind.equals("rays") && !work.isEmpty() && !work.startsWith(":body:");
        String auxiliaryServer = "rays:aux", auxiliaryLevel = lane + ":aux";
        // Auxiliary rays still spend the same hard global and dimension caps.
        if (auxiliary && (s.used.getOrDefault(auxiliaryServer, 0) + amount > Math.max(1, total / 4)
                || s.used.getOrDefault(auxiliaryLevel, 0) + amount > Math.max(1, local / 4))) return false;
        ScopedWorkBudget<Key,String> q=s.queues.get(kind);
        if (q == null) {
            q = new ScopedWorkBudget<>(total,local,batchLimit,40);
            s.queues.put(kind,q);
        }
        if(q.claimFull(s.tick,key,dim,amount,dev.felix.hostilehumans.performance.BudgetDemandOrder.faction(actor))!=amount) {
            if (auxiliary) q.cancel(key);
            return false;
        }
        if (auxiliary) {
            s.used.merge(auxiliaryServer, amount, Integer::sum);
            s.used.merge(auxiliaryLevel, amount, Integer::sum);
        }
        q.cancel(key); // Demand driven: consumed work must not reserve unused capacity next tick.
        s.used.merge(lane,amount,Integer::sum);s.used.merge(kind,amount,Integer::sum); return true;
    }
    public static void cancel(Entity actor,String kind,String work){
        State state=SERVERS.get(actor.level().getServer());if(state==null)return;
        var queue=state.queues.get(kind);if(queue==null)return;
        queue.cancel(new Key(actor.getUUID(),kind+":"+actor.level().dimension().location()+work));
    }
    public static String usage(Entity actor,String kind) {
        return usage(actor.level().getServer(),actor.level().dimension().location().toString(),kind);
    }
    public static String usage(MinecraftServer server,String dimension,String kind) {
        State s=SERVERS.get(server);
        String lane=kind+":"+dimension;
        boolean fresh=s!=null && server!=null && s.tick>=server.getTickCount()-1L && s.tick<=server.getTickCount();
        PerceptionConfig c = fresh ? s.config : PerceptionConfig.get();
        return (fresh?s.used.getOrDefault(lane,0):0)+"/"+c.integer("budgets."+kind+"_per_level")+
                " · 全服 "+(fresh?s.used.getOrDefault(kind,0):0)+"/"+c.integer("budgets."+kind+"_per_server");
    }
}
