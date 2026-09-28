package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Bounded, non-chunk-loading roster reconciliation. Entity removal events remain immediate. */
final class SoldierRosterAudit {
    private static final int PASSIVE_INTERVAL = 200;
    private static final int PASSIVE_BATCH = 16;
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static final class State {
        int cursor;
        final HashSet<UUID> suspectedMissing = new HashSet<>();
        final ArrayDeque<RefreshJob> refreshes = new ArrayDeque<>();
    }
    private record RefreshJob(UUID owner, ArrayDeque<UUID> remaining) {}
    private SoldierRosterAudit() {}

    static void onServerStopped(ServerStoppedEvent event) {
        STATES.remove(event.getServer());
    }

    static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        processRequestedRefresh(server, state);
        if (now % PASSIVE_INTERVAL != 0) return;
        List<UUID> ids = RecruitmentLedger.get(server).soldierIds();
        if (ids.isEmpty()) {
            state.suspectedMissing.clear();
            state.cursor = 0;
            return;
        }
        int size = ids.size();
        for (int i = 0; i < Math.min(PASSIVE_BATCH, size); i++) {
            audit(server, ids.get((state.cursor + i) % size), state);
        }
        state.cursor = (state.cursor + Math.min(PASSIVE_BATCH, size)) % size;
        state.suspectedMissing.retainAll(new HashSet<>(RecruitmentLedger.get(server).soldierIds()));
    }

    /** Immediately validates the displayed page without forcing any chunk to load. */
    static void refresh(ServerPlayer player, List<UUID> ids) {
        MinecraftServer server = player.server;
        State state = STATES.computeIfAbsent(server, ignored -> new State());
        for (UUID id : ids) {
            if (RecruitmentLedger.get(server).isOwnedRecord(id, player.getUUID())) {
                audit(server, id, state);
            }
        }
    }

    static void requestFullRefresh(ServerPlayer player) {
        State state = STATES.computeIfAbsent(player.server, ignored -> new State());
        state.refreshes.removeIf(job -> job.owner.equals(player.getUUID()));
        ArrayDeque<UUID> ids = new ArrayDeque<>();
        for (RecruitmentLedger.Soldier soldier : RecruitmentLedger.get(player.server).soldiers(player.getUUID())) {
            ids.addLast(soldier.id());
        }
        if (!ids.isEmpty()) state.refreshes.addLast(new RefreshJob(player.getUUID(), ids));
    }

    private static void processRequestedRefresh(MinecraftServer server, State state) {
        int budget = PASSIVE_BATCH;
        while (budget > 0 && !state.refreshes.isEmpty()) {
            RefreshJob job = state.refreshes.removeFirst();
            ServerPlayer player = server.getPlayerList().getPlayer(job.owner);
            if (player == null) continue;
            while (budget > 0 && !job.remaining.isEmpty()) {
                UUID id = job.remaining.removeFirst();
                if (RecruitmentLedger.get(server).isOwnedRecord(id, job.owner)) {
                    audit(server, id, state);
                }
                budget--;
            }
            if (job.remaining.isEmpty()) SoldierRosterNetwork.refreshIfOpen(player);
            else state.refreshes.addLast(job);
        }
    }

    static boolean suspectedMissingInLoadedChunk(MinecraftServer server, UUID id) {
        State state = STATES.get(server);
        return state != null && state.suspectedMissing.contains(id);
    }

    private static void audit(MinecraftServer server, UUID id, State state) {
        Human live = findLoaded(server, id);
        if (live != null) {
            state.suspectedMissing.remove(id);
            PeacefulHumanPolicy.reconcileLoaded(server, live);
            return;
        }
        if (HiredHumanRecall.hasPending(server, id)) {
            state.suspectedMissing.remove(id);
            return;
        }
        HumanServerData data = HumanServerData.get();
        HumanData stored = data == null ? null : data.getHumanMob(id);
        if (stored == null || stored.getBlockPos() == null || stored.getLevelKey() == null) {
            // An unloaded entity without a reliable location must not be treated as dead.
            state.suspectedMissing.remove(id);
            return;
        }
        ServerLevel level = server.getLevel(stored.getLevelKey());
        BlockPos pos = stored.getBlockPos();
        if (level == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            state.suspectedMissing.remove(id);
            return;
        }
        // A loaded block chunk alone does not prove its entity data is loaded.
        // Report suspicion, but never release capacity without an explicit removal event
        // or an owner-confirmed dismissal.
        state.suspectedMissing.add(id);
    }

    private static Human findLoaded(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Human human && !human.isRemoved()) return human;
        }
        return null;
    }
}
