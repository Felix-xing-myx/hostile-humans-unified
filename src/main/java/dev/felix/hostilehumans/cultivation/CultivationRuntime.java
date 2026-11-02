package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime clock and lifecycle hooks. The server consumes local pause timestamps; remote clients
 * have no such protocol.
 */
public final class CultivationRuntime {
    private record PauseMark(long nano, boolean paused) {}

    private static final class ClockSession {
        ActiveRuntimeClock clock;
        final ArrayDeque<PauseMark> marks = new ArrayDeque<>();
    }

    private static final Map<MinecraftServer, ClockSession> CLOCKS = new ConcurrentHashMap<>();
    private static final Set<Human> GOALS = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;

    private CultivationRuntime() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CultivationNetwork.setRequestHandler(CultivationFacade::handle);
        CultivationNetwork.register();
        MinecraftForge.EVENT_BUS.register(CultivationRuntime.class);
        MinecraftForge.EVENT_BUS.addListener(CultivationCommands::register);
    }

    public static void observeLocalPause(MinecraftServer server, boolean paused, long nanoTime) {
        if (server == null || !server.isRunning()) return;
        var s = CLOCKS.computeIfAbsent(server, k -> new ClockSession());
        synchronized (s) {
            if (s.marks.isEmpty() || s.marks.getLast().paused() != paused)
                s.marks.addLast(new PauseMark(nanoTime, paused));
        }
    }

    public static long time(MinecraftServer server) {
        var s = CLOCKS.get(server);
        return s != null && s.clock != null
                ? s.clock.current()
                : CultivationData.get(server).runtimeMillis;
    }

    private static void advance(MinecraftServer server) {
        var data = CultivationData.get(server);
        var s = CLOCKS.computeIfAbsent(server, k -> new ClockSession());
        synchronized (s) {
            if (s.clock == null) {
                s.clock = new ActiveRuntimeClock(data.runtimeMillis);
                s.clock.advance(System.nanoTime());
            }
            while (!s.marks.isEmpty()) {
                var mark = s.marks.removeFirst();
                s.clock.paused(mark.nano(), mark.paused());
            }
            data.runtimeMillis = s.clock.advance(System.nanoTime());
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        advance(server);
        CultivationService.tick(server);
        CultivationFacade.tick(server);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void living(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Human human
                && !human.level().isClientSide
                && GOALS.add(human)) {
            human.goalSelector.addGoal(-10, new CultivationTrainingGoal(human));
            CultivationService.joined(human);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingDamageEvent event) {
        if (event.getAmount() > 0
                && event.getEntity() instanceof Human h
                && !h.level().isClientSide)
            CultivationService.interrupt(h, "Attacked; normal AI released", false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void leaving(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Human h) || h.level().isClientSide) return;
        var reason = h.getRemovalReason();
        if (reason == net.minecraft.world.entity.Entity.RemovalReason.KILLED
                || reason == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED
                || reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION)
            CultivationService.interrupt(h, "Soldier removed or changed dimension", true);
    }

    @SubscribeEvent
    public static void logout(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)
            CultivationFacade.close(p);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        advance(e.getServer());
        CultivationFacade.close(e.getServer());
        var s = CLOCKS.remove(e.getServer());
        if (s != null && s.clock != null) s.clock.stop();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        CLOCKS.remove(e.getServer());
        GOALS.removeIf(h -> h.getServer() == e.getServer());
    }
}
