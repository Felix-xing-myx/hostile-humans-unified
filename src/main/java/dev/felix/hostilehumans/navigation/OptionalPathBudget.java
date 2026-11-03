package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.performance.FairWorkBudget;
import dev.felix.hostilehumans.performance.BudgetDemandOrder;

import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Shared server-main-thread budget for multi-candidate searches, not basic navigation. */
public final class OptionalPathBudget {
    public enum Kind { FIRING_LANE, SHORE_FIRING, WATER_PURSUIT, RETREAT, LAVA, EXPLOSION, CONTINUITY, COMBAT_TERRAIN, PATH_FOLLOW }
    private record Key(UUID human, Kind kind) {}
    private record Pools(FairWorkBudget<Key> combat, FairWorkBudget<Key> emergency,
                         FairWorkBudget<Key> continuity,FairWorkBudget<Key> follow) {}
    private static final Map<MinecraftServer, Pools> SERVERS = new WeakHashMap<>();

    public static int claim(Human human, Kind kind, int maximum) {
        MinecraftServer server = human.level().getServer();
        if (server == null || human.level().isClientSide) return 0;
        Pools pools = SERVERS.computeIfAbsent(server, ignored -> new Pools(
                new FairWorkBudget<>(64, 4, 2), new FairWorkBudget<>(32, 4, 2),
                new FairWorkBudget<>(8, 1, 3),new FairWorkBudget<>(16,2,8)));
        pools.combat().setTickCapacity(AdaptiveActivityBudget.navigationCapacity(human,64,4));
        pools.follow().setTickCapacity(AdaptiveActivityBudget.navigationCapacity(human,16,2));
        int granted = pool(pools, kind).claim(server.getTickCount(), new Key(human.getUUID(), kind), maximum, BudgetDemandOrder.faction(human));
        NavigationDebugService.budget(human, kind.name(), maximum, granted);
        return granted;
    }

    public static int claimFull(Human human, Kind kind, int maximum) {
        MinecraftServer server = human.level().getServer();
        if (server == null || human.level().isClientSide) return 0;
        Pools pools = SERVERS.computeIfAbsent(server, ignored -> new Pools(
                new FairWorkBudget<>(64, 4, 2), new FairWorkBudget<>(32, 4, 2),
                new FairWorkBudget<>(8, 1, 3),new FairWorkBudget<>(16,2,8)));
        pools.combat().setTickCapacity(AdaptiveActivityBudget.navigationCapacity(human,64,4));
        pools.follow().setTickCapacity(AdaptiveActivityBudget.navigationCapacity(human,16,2));
        int granted = pool(pools, kind).claimFull(server.getTickCount(), new Key(human.getUUID(), kind), maximum, BudgetDemandOrder.faction(human));
        NavigationDebugService.budget(human, kind.name(), maximum, granted);
        return granted;
    }

    public static void cancel(Human human, Kind kind) {
        Pools pools = SERVERS.get(human.level().getServer());
        if (pools != null) pool(pools, kind).cancel(new Key(human.getUUID(), kind));
    }

    private static FairWorkBudget<Key> pool(Pools pools, Kind kind) {
        if(kind==Kind.PATH_FOLLOW)return pools.follow();
        if (kind == Kind.CONTINUITY) return pools.continuity();
        return kind == Kind.RETREAT || kind == Kind.LAVA || kind == Kind.EXPLOSION ? pools.emergency() : pools.combat();
    }
}
