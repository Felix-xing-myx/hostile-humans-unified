package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.FairWorkBudget;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Shared server-main-thread budget for multi-candidate searches, not basic navigation. */
final class OptionalPathBudget {
    enum Kind { FIRING_LANE, RETREAT, LAVA }
    private record Key(UUID human, Kind kind) {}
    private record Pools(FairWorkBudget<Key> combat, FairWorkBudget<Key> emergency) {}
    private static final Map<MinecraftServer, Pools> SERVERS = new WeakHashMap<>();

    static int claim(Human human, Kind kind, int maximum) {
        MinecraftServer server = human.level().getServer();
        if (server == null || human.level().isClientSide) return 0;
        Pools pools = SERVERS.computeIfAbsent(server, ignored -> new Pools(
                new FairWorkBudget<>(64, 4, 2), new FairWorkBudget<>(32, 4, 2)));
        return pool(pools, kind).claim(server.getTickCount(), new Key(human.getUUID(), kind), maximum);
    }

    static void cancel(Human human, Kind kind) {
        Pools pools = SERVERS.get(human.level().getServer());
        if (pools != null) pool(pools, kind).cancel(new Key(human.getUUID(), kind));
    }

    private static FairWorkBudget<Key> pool(Pools pools, Kind kind) {
        return kind == Kind.RETREAT || kind == Kind.LAVA ? pools.emergency() : pools.combat();
    }
}
