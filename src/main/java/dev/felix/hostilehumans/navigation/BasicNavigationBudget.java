package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.performance.FairWorkBudget;
import dev.felix.hostilehumans.performance.BudgetDemandOrder;

import dev.felix.hostilehumans.observer.NavigationDebugService;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;

/** Ordinary route searches share FIFO capacity; a deferred grant is not failure. */
public final class BasicNavigationBudget {
    private static final Map<MinecraftServer, FairWorkBudget<UUID>> SERVERS = new WeakHashMap<>();
    private BasicNavigationBudget() {}
    public static boolean tryAcquire(Human human, int searches) {
        if (searches < 1 || searches > 4) throw new IllegalArgumentException("search batch must be 1..4");
        MinecraftServer server = human.level().getServer();
        if (server == null || human.level().isClientSide) return false;
        FairWorkBudget<UUID> budget=SERVERS.computeIfAbsent(server, ignored -> new FairWorkBudget<>(32, 4, 40));
        budget.setTickCapacity(AdaptiveActivityBudget.navigationCapacity(human,32,4));
        int granted=budget.claimFull(server.getTickCount(),human.getUUID(),searches,BudgetDemandOrder.faction(human));
        NavigationDebugService.budget(human, "basic", searches, granted);
        return granted == searches;
    }
    public static String usage(MinecraftServer server) {
        var budget = SERVERS.get(server);
        int cap = AdaptiveActivityBudget.navigationCapacity(server,32,4);
        return budget == null ? "0 / " + cap : budget.usage(server.getTickCount(),cap);
    }

    public static void cancel(Human human) {
        FairWorkBudget<UUID> budget = SERVERS.get(human.level().getServer());
        if (budget != null) budget.cancel(human.getUUID());
    }
}
