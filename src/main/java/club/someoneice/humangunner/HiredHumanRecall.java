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
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Iterator;
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
    private static final TicketType<UUID> RECALL_TICKET = TicketType.create(
            "humangunner_recall", UUID::compareTo, (int) RECALL_TIMEOUT_TICKS
    );
    private static final Map<MinecraftServer, Map<UUID, PlayerLocation>> PLAYER_LOCATIONS =
            new WeakHashMap<>();
    private static final Map<MinecraftServer, Map<UUID, PendingRecall>> PENDING_RECALLS =
            new WeakHashMap<>();

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
        PENDING_RECALLS.remove(event.getServer());
    }

    static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Map<UUID, PendingRecall> pending = PENDING_RECALLS.get(server);
        if (pending == null || pending.isEmpty()) return;
        int processed = 0;
        Iterator<PendingRecall> iterator = pending.values().iterator();
        while (iterator.hasNext() && processed++ < MAX_PENDING_PER_TICK) {
            PendingRecall request = iterator.next();
            ServerLevel source = server.getLevel(request.dimension());
            ServerPlayer owner = server.getPlayerList().getPlayer(request.owner());
            long now = server.overworld().getGameTime();
            if (source == null || owner == null || now > request.expiresAt()) {
                releaseTicket(source, request);
                iterator.remove();
                continue;
            }
            Entity loaded = source.getEntity(request.human());
            if (!(loaded instanceof Human human) || human.isRemoved()) continue;
            if (!human.isAlive() || !HumanRelations.isOwnedBy(human, owner)
                    || (request.followersOnly() && SoldierOrder.get(human) != SoldierOrder.FOLLOW)) {
                releaseTicket(source, request);
                iterator.remove();
                continue;
            }
            if (moveToOwner(owner, human, pending.size())) {
                releaseTicket(source, request);
                iterator.remove();
            }
        }
        if (pending.isEmpty()) PENDING_RECALLS.remove(server);
    }

    static int recallAll(ServerPlayer owner) {
        return recall(owner, false);
    }

    private static int recall(ServerPlayer owner, boolean followersOnly) {
        HumanServerData serverData = HumanServerData.get();
        if (serverData == null) return 0;
        RecruitmentLedger.get(owner.server).refreshLoadedIndex(owner.server, owner.getUUID());
        int recalled = 0;
        int placementIndex = 0;
        // Copy the stable view because dimension transfer updates HumanServerData.
        for (HumanData stored : Set.copyOf(serverData.getHumanMobs(owner.getUUID()))) {
            Human human = resolve(owner.server, stored);
            if (human == null) {
                if (schedule(owner, stored, followersOnly)) recalled++;
                continue;
            }
            if (!human.isAlive() || !HumanRelations.isOwnedBy(human, owner)) continue;
            SoldierOrder order = SoldierOrder.get(human);
            if (followersOnly && order != SoldierOrder.FOLLOW) continue;
            if (moveToOwner(owner, human, placementIndex++)) recalled++;
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
                owner.server.overworld().getGameTime() + RECALL_TIMEOUT_TICKS
        );
        Map<UUID, PendingRecall> pending = PENDING_RECALLS.computeIfAbsent(
                owner.server, ignored -> new LinkedHashMap<>()
        );
        PendingRecall previous = pending.put(stored.getUUID(), request);
        if (previous != null) {
            releaseTicket(owner.server.getLevel(previous.dimension()), previous);
        }
        source.getChunkSource().addRegionTicket(RECALL_TICKET, chunk, RECALL_TICKET_LEVEL, stored.getUUID());
        source.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, true);
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

    private static BlockPos destination(ServerPlayer owner, int index) {
        int ring = 2 + index / 8;
        int phase = index % 8;
        int[][] directions = {
                {1, 0}, {1, 1}, {0, 1}, {-1, 1},
                {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
        };
        for (int attempt = 0; attempt < directions.length; attempt++) {
            int[] direction = directions[(phase + attempt) % directions.length];
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
