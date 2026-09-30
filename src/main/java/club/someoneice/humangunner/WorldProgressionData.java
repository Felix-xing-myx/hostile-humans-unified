package club.someoneice.humangunner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** One saved clock per world. Spawn queries only read memory, never tick or do file I/O. */
final class WorldProgressionData extends SavedData {
    private static final String ID = "hostile_humans_progression";
    private static final Map<MinecraftServer, WorldProgressionData> LOADED = new WeakHashMap<>();
    private final ProgressionClock clock;

    private WorldProgressionData(long offset) {
        clock = new ProgressionClock(offset);
    }

    static WorldProgressionData load(CompoundTag root) {
        return new WorldProgressionData(root.getLong("calendar_offset_ticks"));
    }

    private static synchronized WorldProgressionData get(MinecraftServer server) {
        WorldProgressionData data = LOADED.get(server);
        if (data != null) return data;
        // Chunk population may query spawn rules on a worker. SavedData storage
        // is initialized only on the server thread; early worker reads use day 1.
        if (!server.isSameThread()) return null;
        ServerLevel world = server.overworld();
        data = world.getDataStorage().computeIfAbsent(WorldProgressionData::load,
                () -> new WorldProgressionData(0L), ID);
        data.setDirty();
        LOADED.put(server, data);
        return data;
    }

    static long time(MinecraftServer server) {
        WorldProgressionData data = get(server);
        return data == null ? 0L : data.currentTicks(server.overworld().getDayTime());
    }

    private synchronized long currentTicks(long overworldDayTime) {
        return clock.ticksAt(overworldDayTime);
    }

    static void setDay(MinecraftServer server, int day) {
        WorldProgressionData data = get(server);
        synchronized (data) {
            data.clock.setDay(day, server.overworld().getDayTime());
            data.setDirty();
        }
    }

    static void addDays(MinecraftServer server, int days) {
        WorldProgressionData data = get(server);
        synchronized (data) {
            data.clock.addDays(days, server.overworld().getDayTime());
            data.setDirty();
        }
    }

    static void syncFromOverworld(MinecraftServer server) {
        WorldProgressionData data = get(server);
        synchronized (data) {
            data.clock.syncFromOverworld();
            data.setDirty();
        }
    }

    static void onServerStarted(ServerStartedEvent event) { get(event.getServer()); }

    static synchronized void onServerStopped(ServerStoppedEvent event) { LOADED.remove(event.getServer()); }

    @Override
    public synchronized CompoundTag save(CompoundTag root) {
        // Only commands change this value. The saved Overworld calendar supplies
        // normal elapsed time and skipped nights without per-tick bookkeeping.
        root.putLong("calendar_offset_ticks", clock.savedOffset());
        return root;
    }
}
