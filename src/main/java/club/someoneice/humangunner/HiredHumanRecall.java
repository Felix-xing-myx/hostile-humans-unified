package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Locates persisted hired humans and moves them without portals or duplication. */
final class HiredHumanRecall {
    private static final double LONG_DISTANCE_TELEPORT_SQR = 64.0D * 64.0D;
    private static final int RECALL_TICKET_LEVEL = 2;
    private static final long RECALL_TIMEOUT_TICKS = 200L;
    private static final int MAX_PENDING_PER_TICK = 16;
    private static final int MAX_NEW_TICKETS_PER_TICK = 4;
    private static final int[][] PLACEMENT_DIRECTIONS = {
            {1, 0}, {1, 1}, {0, 1}, {-1, 1},
            {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
    };
    private static final TicketType<UUID> RECALL_TICKET = TicketType.create(
            "humangunner_recall", UUID::compareTo, (int) RECALL_TIMEOUT_TICKS
    );
    private static final Map<MinecraftServer, Map<UUID, PlayerLocation>> PLAYER_LOCATIONS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, RecallState> PENDING_RECALLS =
            new WeakHashMap<>();

    private static final class RecallState {
        final Map<UUID, PendingRecall> queued = new LinkedHashMap<>();
        final Map<UUID, PendingRecall> active = new LinkedHashMap<>();
        boolean isEmpty() { return queued.isEmpty() && active.isEmpty(); }
    }

    private record PlayerLocation(ResourceKey<Level> dimension, Vec3 position) {}
    private record PendingRecall(UUID owner, UUID human, ResourceKey<Level> dimension,
                                 ChunkPos chunk, boolean followersOnly, long expiresAt) {}

    private HiredHumanRecall() {}

    static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide) return;
        MinecraftServer server = player.server;
        Map<UUID, PlayerLocation> locations = PLAYER_LOCATIONS.computeIfAbsent(
                server, ignored -> new HashMap<>()
        );
        PlayerLocation current = new PlayerLocation(player.level().dimension(), player.position());
        PlayerLocation previous = locations.put(player.getUUID(), current);
        if (previous == null) return;
        boolean dimensionChanged = !previous.dimension().equals(current.dimension());
        boolean teleportedFar = !dimensionChanged
                && previous.position().distanceToSqr(current.position()) >= LONG_DISTANCE_TELEPORT_SQR;
        if (dimensionChanged || teleportedFar) {
            int recalled = recall(player, true);
            if (recalled > 0) {
                HumanGunner.LOGGER.debug(
                        "Recalled {} following hired humans after owner teleport owner={} crossDimension={}",
                        recalled, player.getUUID(), dimensionChanged
                );
            }
        }
    }

    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Map<UUID, PlayerLocation> locations = PLAYER_LOCATIONS.get(player.server);
        if (locations != null) locations.remove(player.getUUID());
    }

    static void onServerStopped(ServerStoppedEvent event) {
        PLAYER_LOCATIONS.remove(event.getServer());
        cancelAll(event.getServer());
    }

    static void cancelPending(MinecraftServer server, UUID human) {
        RecallState pending = PENDING_RECALLS.get(server);
        if (pending == null) return;
        pending.queued.remove(human);
        PendingRecall removed = pending.active.remove(human);
        if (removed != null) releaseTicket(server.getLevel(removed.dimension()), removed);
        if (pending.isEmpty()) PENDING_RECALLS.remove(server);
    }

    static void cancelAll(MinecraftServer server) {
        RecallState pending = PENDING_RECALLS.remove(server);
        if (pending == null) return;
        for (PendingRecall request : pending.active.values()) {
            releaseTicket(server.getLevel(request.dimension()), request);
        }
    }

    static boolean hasPending(MinecraftServer server, UUID human) {
        RecallState pending = PENDING_RECALLS.get(server);
        return pending != null && (pending.active.containsKey(human) || pending.queued.containsKey(human));
    }

    static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        RecallState pending = PENDING_RECALLS.get(server);
        if (pending == null || pending.isEmpty()) return;
        Set<ServerPlayer> refreshed = new HashSet<>();
        List<PendingRecall> batch = new ArrayList<>(pending.active.size());
        Iterator<PendingRecall> iterator = pending.active.values().iterator();
        while (iterator.hasNext() && batch.size() < MAX_PENDING_PER_TICK) {
            batch.add(iterator.next());
            iterator.remove();
        }
        int placementIndex = 0;
        for (PendingRecall request : batch) {
            ServerLevel source = server.getLevel(request.dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(request.owner());
            long now = server.overworld().getGameTime();
            if (source == null || owner == null || now > request.expiresAt()) {
                releaseTicket(source, request);
                continue;
            }
            Entity loaded = findLoaded(server, request.human());
            if (!(loaded instanceof Human human) || human.isRemoved()) {
                pending.active.put(request.human(), request);
                continue;
            }
            if (!human.isAlive() || !HumanRelations.isOwnedBy(human, owner)
                    || (request.followersOnly() && SoldierOrder.get(human) != SoldierOrder.FOLLOW)) {
                releaseTicket(source, request);
                continue;
            }
            if (moveToOwner(owner, human, placementIndex++)) {
                releaseTicket(source, request);
                refreshed.add(owner);
            } else {
                pending.active.put(request.human(), request);
            }
        }
        activateQueued(server, pending, refreshed);
        if (pending.isEmpty()) PENDING_RECALLS.remove(server);
        for (ServerPlayer player : refreshed) SoldierRosterNetwork.refreshIfOpen(player);
    }

    private static void activateQueued(MinecraftServer server, RecallState state, Set<ServerPlayer> refreshed) {
        Iterator<PendingRecall> queued = state.queued.values().iterator();
        int examined = 0;
        long now = server.overworld().getGameTime();
        while (queued.hasNext() && state.active.size() < MAX_PENDING_PER_TICK
                && examined++ < MAX_NEW_TICKETS_PER_TICK) {
            PendingRecall request = queued.next();
            queued.remove();
            ServerLevel source = server.getLevel(request.dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(request.owner());
            if (source == null || owner == null
                    || !RecruitmentLedger.get(server).isOwnedRecord(request.human(), request.owner())) continue;
            // Loaded bulk recalls share the activation budget as well. A mass
            // teleport must not serialize/transfer every follower in one tick.
            Entity loaded = findLoaded(server, request.human());
            if (loaded instanceof Human human && !human.isRemoved()) {
                if (!human.isAlive() || !HumanRelations.isOwnedBy(human, owner)
                        || (request.followersOnly() && SoldierOrder.get(human) != SoldierOrder.FOLLOW)) continue;
                if (moveToOwner(owner, human, examined - 1)) {
                    refreshed.add(owner);
                    continue;
                }
            }
            PendingRecall active = new PendingRecall(request.owner(), request.human(), request.dimension(),
                    request.chunk(), request.followersOnly(), now + RECALL_TIMEOUT_TICKS);
            source.getChunkSource().addRegionTicket(RECALL_TICKET, active.chunk(), RECALL_TICKET_LEVEL, active.human());
            state.active.put(active.human(), active);
        }
    }

    static int recallAll(ServerPlayer owner) {
        return recall(owner, false);
    }

    static boolean recallOne(ServerPlayer owner, UUID id) {
        if (!RecruitmentLedger.get(owner.server).isOwnedRecord(id, owner.getUUID())) return false;
        for (ServerLevel level : owner.server.getAllLevels()) {
            if (level.getEntity(id) instanceof Human live && live.isAlive()
                    && HumanRelations.isOwnedBy(live, owner)) {
                return moveToOwner(owner, live, 0);
            }
        }
        HumanServerData data = HumanServerData.get();
        HumanData stored = data == null ? null : data.getHumanMob(id);
        if (stored == null || !owner.getUUID().equals(stored.getOwnerUUID())) return false;
        return schedule(owner, stored, false);
    }

    private static int recall(ServerPlayer owner, boolean followersOnly) {
        HumanServerData serverData = HumanServerData.get();
        if (serverData == null) return 0;
        RecruitmentLedger ledger = RecruitmentLedger.get(owner.server);
        ledger.refreshLoadedIndex(owner.server, owner.getUUID());
        int recalled = 0;
        // Copy the stable view because dimension transfer updates HumanServerData.
        for (HumanData stored : Set.copyOf(serverData.getHumanMobs(owner.getUUID()))) {
            if (!ledger.isOwnedRecord(stored.getUUID(), owner.getUUID())) continue;
            Human human = resolve(owner.server, stored);
            if (human == null) {
                if (schedule(owner, stored, followersOnly)) recalled++;
                continue;
            }
            if (!human.isAlive() || !HumanRelations.isOwnedBy(human, owner)) continue;
            SoldierOrder order = SoldierOrder.get(human);
            if (followersOnly && order != SoldierOrder.FOLLOW) continue;
            // Preserve immediate single-unit recall, but batch every all-unit
            // request, including entities whose source chunk is already loaded.
            stored.refreshLiveMetadata(human);
            if (schedule(owner, stored, followersOnly)) recalled++;
        }
        return recalled;
    }

    private static boolean schedule(ServerPlayer owner, HumanData stored, boolean followersOnly) {
        ResourceKey<Level> dimension = stored.getLevelKey();
        BlockPos position = stored.getBlockPos();
        ServerLevel source = dimension == null ? null : owner.server.getLevel(dimension);
        if (source == null || position == null) return false;
        ChunkPos chunk = new ChunkPos(position);
        PendingRecall request = new PendingRecall(
                owner.getUUID(), stored.getUUID(), dimension, chunk, followersOnly,
                0L
        );
        RecallState pending = PENDING_RECALLS.computeIfAbsent(
                owner.server, ignored -> new RecallState()
        );
        PendingRecall previous = pending.active.remove(stored.getUUID());
        if (previous != null) {
            releaseTicket(owner.server.getLevel(previous.dimension()), previous);
        }
        pending.queued.put(stored.getUUID(), request);
        // Region tickets drive the normal chunk/entity loading pipeline.
        // Never call getChunk (or the main-thread getChunkFuture wrapper) here:
        // both can managedBlock the server while disk/generation work finishes.
        // Activation is limited to four requests per tick and sixteen active
        // entity-loading tickets server-wide. Queue time does not consume the
        // loading timeout; polling cannot be starved by unready earlier jobs.
        return true;
    }

    private static void releaseTicket(ServerLevel source, PendingRecall request) {
        if (source != null) {
            source.getChunkSource().removeRegionTicket(
                    RECALL_TICKET, request.chunk(), RECALL_TICKET_LEVEL, request.human()
            );
        }
    }

    private static boolean moveToOwner(ServerPlayer owner, Human human, int placementIndex) {
        BlockPos destination = destination(owner, placementIndex);
        if (destination == null) return false;
        SoldierOrder order = SoldierOrder.get(human);
        human.stopRiding();
        human.forgetSoldierTarget();
        UUID id = human.getUUID();
        boolean moved = human.teleportTo(
                owner.serverLevel(),
                destination.getX() + 0.5D,
                destination.getY(),
                destination.getZ() + 0.5D,
                Set.<RelativeMovement>of(),
                human.getYRot(),
                human.getXRot()
        );
        if (!moved) return false;
        Entity movedEntity = owner.serverLevel().getEntity(id);
        Human movedHuman = movedEntity instanceof Human result ? result : human;
        movedHuman.getNavigation().stop();
        SoldierOrder.set(movedHuman, order);
        HumanServerData serverData = HumanServerData.get();
        if (serverData != null) serverData.updateOrRegisterHumanMob(movedHuman);
        return true;
    }

    private static Human resolve(MinecraftServer server, HumanData stored) {
        HumanEntity cached = stored.getHHFollowerEntity();
        if (cached instanceof Human human && !human.isRemoved()) return human;
        for (ServerLevel level : server.getAllLevels()) {
            Entity loaded = level.getEntity(stored.getUUID());
            if (loaded instanceof Human human && !human.isRemoved()) return human;
        }
        return null;
    }

    private static Human findLoaded(MinecraftServer server, UUID id) {
        // Queue entries can outlive a normal dimension transfer. Resolve UUID
        // in loaded entity indexes, never load a chunk merely to locate it.
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Human human && !human.isRemoved()) return human;
        }
        return null;
    }

    private static BlockPos destination(ServerPlayer owner, int index) {
        int ring = 2 + index / 8;
        int phase = index % 8;
        for (int attempt = 0; attempt < PLACEMENT_DIRECTIONS.length; attempt++) {
            int[] direction = PLACEMENT_DIRECTIONS[(phase + attempt) % PLACEMENT_DIRECTIONS.length];
            BlockPos safe = SafePositions.nearFloor(
                    owner.serverLevel(),
                    owner.getBlockX() + direction[0] * ring,
                    owner.getBlockZ() + direction[1] * ring,
                    owner.getBlockY()
            );
            if (safe != null) return safe;
        }
        return null;
    }
}
