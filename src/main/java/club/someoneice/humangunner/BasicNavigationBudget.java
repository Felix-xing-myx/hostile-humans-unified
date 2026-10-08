package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.FairWorkBudget;
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
        return SERVERS.computeIfAbsent(server, ignored -> new FairWorkBudget<>(32, 4, 40))
                .claimFull(server.getTickCount(), human.getUUID(), searches) == searches;
    }
    public static void cancel(Human human) {
        FairWorkBudget<UUID> budget = SERVERS.get(human.level().getServer());
        if (budget != null) budget.cancel(human.getUUID());
    }
}
