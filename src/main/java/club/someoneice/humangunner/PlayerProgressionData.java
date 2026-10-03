package club.someoneice.humangunner;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import java.util.*;

/** Personal online clocks. One ten-second batch; spawn readers use immutable snapshots only. */
final class PlayerProgressionData extends SavedData {
    private static final Map<MinecraftServer, PlayerProgressionData> LOADED = new WeakHashMap<>();
    private final Map<UUID, Long> ticks = new HashMap<>();
    private final Map<UUID, Long> sessions = new HashMap<>();
    private volatile List<PlayerPoint> snapshot = List.of();
    private record PlayerPoint(UUID id, String dimension, double x, double y, double z, long ticks) { }

    static PlayerProgressionData load(CompoundTag tag) {
        PlayerProgressionData data = new PlayerProgressionData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try { data.ticks.put(UUID.fromString(key), Math.max(0, players.getLong(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    private static synchronized PlayerProgressionData get(MinecraftServer server) {
        PlayerProgressionData data = LOADED.get(server);
        if (data == null && server.isSameThread()) {
            data = server.overworld().getDataStorage().computeIfAbsent(PlayerProgressionData::load,
                    PlayerProgressionData::new, "hostile_humans_player_progression");
            LOADED.put(server, data);
        }
        return data;
    }

    void settle(UUID id, long now) {
        Long previous = sessions.put(id, now);
        if (previous != null && now > previous) {
            ticks.put(id, ProgressionClock.addClamped(ticks.getOrDefault(id, 0L), now - previous));
            setDirty();
        }
    }

    private void refresh(MinecraftServer server) {
        long now = server.overworld().getGameTime(); // Not dayTime: sleep and /time cannot skip it.
        List<PlayerPoint> points = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            settle(player.getUUID(), now);
            if (!player.isSpectator() && player.isAlive()) {
                points.add(new PlayerPoint(player.getUUID(), player.level().dimension().location().toString(),
                        player.getX(), player.getY(), player.getZ(), ticks.getOrDefault(player.getUUID(), 0L)));
            }
        }
        snapshot = List.copyOf(points);
    }

    /** No chunk load, stat query, NBT mutation or disk access from spawn workers. */
    static long timeNear(ServerLevelAccessor level, BlockPos pos) {
        PlayerProgressionData data = get(level.getLevel().getServer());
        if (data == null) return -1L;
        if (level.getLevel().getServer().isSameThread()) {
            // Live coordinates on the server thread avoid assigning a moving
            // newcomer a veteran's cached position. This is still a read-only query.
            double nearest = Double.POSITIVE_INFINITY;
            ServerPlayer selected = null;
            for (ServerPlayer player : level.getLevel().players()) {
                if (player.isSpectator() || !player.isAlive()) continue;
                double distance = player.distanceToSqr(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D);
                if (distance < nearest) { nearest = distance; selected = player; }
            }
            if (selected == null) return -1L;
            long now = level.getLevel().getServer().overworld().getGameTime();
            return ProgressionClock.addClamped(data.ticks.getOrDefault(selected.getUUID(), 0L),
                    Math.max(0L, now - data.sessions.getOrDefault(selected.getUUID(), now)));
        }
        String dimension = level.getLevel().dimension().location().toString();
        double best = Double.POSITIVE_INFINITY;
        long result = -1L;
        for (PlayerPoint point : data.snapshot) {
            if (!point.dimension.equals(dimension)) continue;
            double dx = point.x - (pos.getX() + .5), dy = point.y - pos.getY(), dz = point.z - (pos.getZ() + .5);
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) { best = distance; result = point.ticks; }
        }
        return result;
    }

    static long time(ServerPlayer player) {
        PlayerProgressionData data = get(player.getServer());
        data.settle(player.getUUID(), player.getServer().overworld().getGameTime());
        return data.ticks.getOrDefault(player.getUUID(), 0L);
    }

    static void change(ServerPlayer player, int days, boolean absolute) {
        long current = time(player);
        PlayerProgressionData data = get(player.getServer());
        data.ticks.put(player.getUUID(), absolute ? (Math.max(1L, days) - 1L) * 24000L
                : ProgressionClock.addClamped(current, (long) days * 24000L));
        data.setDirty();
        data.refresh(player.getServer());
    }

    static void onStarted(ServerStartedEvent event) { get(event.getServer()).refresh(event.getServer()); }
    static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 200 == 0) {
            get(event.getServer()).refresh(event.getServer());
        }
    }
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.getServer()).refresh(player.getServer());
    }
    static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.getServer()).refresh(player.getServer());
    }
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) get(player.getServer()).refresh(player.getServer());
    }
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerProgressionData data = get(player.getServer());
            data.settle(player.getUUID(), player.getServer().overworld().getGameTime());
            data.sessions.remove(player.getUUID());
            data.snapshot = data.snapshot.stream().filter(point -> !point.id.equals(player.getUUID())).toList();
        }
    }
    static void onStopping(ServerStoppingEvent event) { get(event.getServer()).refresh(event.getServer()); }
    static synchronized void onStopped(ServerStoppedEvent event) { LOADED.remove(event.getServer()); }

    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        ticks.forEach((id, value) -> players.putLong(id.toString(), value));
        tag.put("players", players);
        return tag;
    }
}
