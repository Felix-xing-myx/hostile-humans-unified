package dev.felix.hostilehumans.monitor.remote;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Session-owned tracking origin. Never changes a player's physical position, camera or game mode.
 * Native PLAYER tickets retain the server's view and simulation distances around the view origin.
 * Only the physical body's chunk and temporary bootstrap use small additional ticking tickets.
 * Callers own permission checks and renew only their currently authorized viewing session. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class MonitorRemoteView {
    private static final int SMALL_TICKET_DISTANCE = 2;
    private static final int RENEWAL_TIMEOUT = 100;
    private static final TicketType<ChunkPos> SUPPORT = TicketType.create(
            HumanRuntime.MOD_ID + ":monitor_support", Comparator.comparingLong(ChunkPos::toLong));
    private record Support(ServerLevel level, ChunkPos chunk) { }
    private static final Map<Support, Integer> SUPPORT_USERS = new HashMap<>();
    private static final Map<ServerPlayer, View> VIEWS = new HashMap<>();
    private static final Map<ServerPlayer, Bootstrap> BOOTSTRAPS = new HashMap<>();
    private static final Map<ServerPlayer, Integer> CLIENT_VIEW_DISTANCE = new HashMap<>();

    private static final class View {
        final ServerLevel level;
        final UUID target;
        Support body;
        Vec3 position;
        long renewed;
        View(ServerPlayer player, Human human) {
            level = player.serverLevel(); target = human.getUUID(); position = human.position();
            body = new Support(level, player.chunkPosition()); renewed = level.getGameTime();
        }
    }
    private record Bootstrap(Support support, UUID target, Predicate<Human> authorized,
                             Consumer<Human> ready, Consumer<String> failed, long deadline) { }
    private MonitorRemoteView() { }

    /** Authorization must already be established by the monitor service. Same-dimension client world only. */
    public static boolean start(ServerPlayer player, Human human) {
        if (!available(player) || human == null || human.level() != player.level()
                || !human.isAlive() || human.isRemoved()) return false;
        View current = VIEWS.get(player);
        if (current != null && current.target.equals(human.getUUID()) && current.level == human.level()) {
            return tick(player, human);
        }
        stop(player);
        View view = new View(player, human);
        acquire(view.body);
        VIEWS.put(player, view);
        try { view.level.getChunkSource().move(player); }
        catch (RuntimeException | Error failure) {
            VIEWS.remove(player);
            try { view.level.getChunkSource().move(player); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            finally { release(view.body); }
            throw failure;
        }
        return true;
    }

    /** Renew from the authorized service every server tick. Refreshes native entity audiences without body movement. */
    public static boolean tick(ServerPlayer player, Human human) {
        View view = VIEWS.get(player);
        if (view == null) return false;
        if (!available(player) || human == null || !human.isAlive() || human.isRemoved()
                || human.level() != view.level || player.level() != view.level
                || !view.target.equals(human.getUUID())) { stop(player); return false; }
        Support body = new Support(view.level, player.chunkPosition());
        boolean bodyChanged = !body.equals(view.body);
        if (bodyChanged) { acquire(body); release(view.body); view.body = body; }
        Vec3 previous = view.position;
        view.position = human.position();
        view.renewed = view.level.getGameTime();
        if (bodyChanged || !previous.equals(view.position)) view.level.getChunkSource().move(player);
        return true;
    }

    /** Restore native tracking while the physical-body support is still held, then release our last support. */
    public static void stop(ServerPlayer player) {
        cancelBootstrap(player);
        View view = VIEWS.remove(player);
        if (view == null) return;
        try {
            if (!player.isRemoved() && player.level() == view.level) view.level.getChunkSource().move(player);
        } finally { release(view.body); }
    }

    public static boolean active(ServerPlayer player) { return VIEWS.containsKey(player); }
    public static Vec3 position(ServerPlayer player) {
        View view = VIEWS.get(player);
        return view != null && view.level == player.level() ? view.position : player.position();
    }
    public static SectionPos section(ServerPlayer player) {
        Vec3 position = position(player);
        return SectionPos.of(BlockPos.containing(position));
    }
    public static void recordClientViewDistance(ServerPlayer player, int distance) {
        CLIENT_VIEW_DISTANCE.put(player, Math.max(2, Math.min(32, distance)));
    }
    /** Informational effective render radius; native server loading and simulation policy remains unchanged. */
    public static int renderRadius(ServerPlayer player) {
        return Math.min(player.server.getPlayerList().getViewDistance(), CLIENT_VIEW_DISTANCE.getOrDefault(player, 10));
    }

    /** Asynchronously resolve a UUID near a server-trusted last position. Never restores or clones saved entities.
     * The predicate is rechecked against the live entity; the callback must establish its own monitor session.
     * Exactly one pending bootstrap belongs to each player. Timeout or cancellation releases only our ticket. */
    public static boolean bootstrap(ServerPlayer player, ServerLevel level, BlockPos trustedPosition, UUID target,
                                    Predicate<Human> authorized, Consumer<Human> ready, Consumer<String> failed) {
        if (!available(player) || level != player.level() || target == null || trustedPosition == null
                || authorized == null || ready == null || failed == null) return false;
        cancelBootstrap(player);
        Support support = new Support(level, new ChunkPos(trustedPosition));
        acquire(support);
        BOOTSTRAPS.put(player, new Bootstrap(support, target, authorized, ready, failed,
                level.getGameTime() + RENEWAL_TIMEOUT));
        return true;
    }
    public static void cancelBootstrap(ServerPlayer player) {
        Bootstrap pending = BOOTSTRAPS.remove(player);
        if (pending != null) release(pending.support());
    }
    private static boolean available(ServerPlayer player) {
        return player != null && !player.isRemoved() && player.isAlive() && !player.hasDisconnected();
    }
    private static void acquire(Support support) {
        int users = SUPPORT_USERS.getOrDefault(support, 0);
        if (users == 0) support.level().getChunkSource().addRegionTicket(
                SUPPORT, support.chunk(), SMALL_TICKET_DISTANCE, support.chunk());
        SUPPORT_USERS.put(support, users + 1);
    }
    private static void release(Support support) {
        Integer users = SUPPORT_USERS.get(support);
        if (users == null) return;
        if (users > 1) SUPPORT_USERS.put(support, users - 1);
        else {
            SUPPORT_USERS.remove(support);
            support.level().getChunkSource().removeRegionTicket(
                    SUPPORT, support.chunk(), SMALL_TICKET_DISTANCE, support.chunk());
        }
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        View view = VIEWS.get(player);
        if (view != null && (!available(player) || player.level() != view.level
                || view.level.getGameTime() - view.renewed > RENEWAL_TIMEOUT)) stop(player);
        Bootstrap pending = BOOTSTRAPS.get(player);
        if (pending == null) return;
        Human human = pending.support().level().getEntity(pending.target()) instanceof Human found ? found : null;
        String failure = !available(player) || player.level() != pending.support().level() ? "observer unavailable"
                : pending.support().level().getGameTime() > pending.deadline() ? "target loading timed out" : null;
        try {
            if (failure == null && human != null) {
                if (!human.isAlive() || human.isRemoved() || !pending.authorized().test(human)) failure = "target unavailable or unauthorized";
                else {
                    // Establish the native view's ticket before releasing bootstrap support.
                    BOOTSTRAPS.remove(player, pending);
                    try { pending.ready().accept(human); }
                    finally { release(pending.support()); }
                    return;
                }
            }
            if (failure != null) {
                BOOTSTRAPS.remove(player, pending);
                try { pending.failed().accept(failure); }
                finally { release(pending.support()); }
            }
        } catch (RuntimeException | Error exception) {
            if (BOOTSTRAPS.remove(player, pending)) release(pending.support());
            throw exception;
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try { stop(player); } finally { CLIENT_VIEW_DISTANCE.remove(player); }
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer player : java.util.List.copyOf(VIEWS.keySet()))
            if (player.server == event.getServer()) stop(player);
        for (ServerPlayer player : java.util.List.copyOf(BOOTSTRAPS.keySet()))
            if (player.server == event.getServer()) cancelBootstrap(player);
        CLIENT_VIEW_DISTANCE.keySet().removeIf(player -> player.server == event.getServer());
    }
}
