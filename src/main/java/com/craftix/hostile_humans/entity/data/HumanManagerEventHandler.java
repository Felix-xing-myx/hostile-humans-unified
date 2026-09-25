package com.craftix.hostile_humans.entity.data;

import com.craftix.hostile_humans.entity.HumanEntity;
import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Coalesced metadata updates; queued keys never retain entities or levels. */
@Mod.EventBusSubscriber(modid = "hostile_humans")
public final class HumanManagerEventHandler {
    private record Key(ResourceKey<Level> dimension, UUID uuid) {}
    private static final Map<MinecraftServer, dev.felix.hostilehumans.core.BudgetedUpdates<Key>> QUEUES = new WeakHashMap<>();
    private static final int PER_TICK_BUDGET = 32;

    public static void queue(Entity entity) {
        if (!(entity instanceof HumanEntity human) || !human.hasOwner()
                || !(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        QUEUES.computeIfAbsent(level.getServer(), k -> new dev.felix.hostilehumans.core.BudgetedUpdates<>())
                .offer(new Key(level.dimension(), entity.getUUID()));
    }
    private static void flush(Entity entity) {
        if (entity instanceof HumanEntity human && human.hasOwner() && !human.level().isClientSide) {
            HumanServerData data = HumanServerData.get();
            if (data != null) data.updateOrRegisterHumanMob(human);
        }
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) { queue(event.getEntity()); }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) { flush(event.getEntity()); }
    @SubscribeEvent public static void damage(LivingDamageEvent event) { queue(event.getEntity()); }
    @SubscribeEvent public static void heal(LivingHealEvent event) { queue(event.getEntity()); }
    @SubscribeEvent public static void death(LivingDeathEvent event) { flush(event.getEntity()); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        dev.felix.hostilehumans.core.BudgetedUpdates<Key> queue = QUEUES.get(server);
        if (queue == null) return;
        List<Key> batch = queue.drain(PER_TICK_BUDGET);
        for (Key key : batch) {
            ServerLevel level = server.getLevel(key.dimension());
            Entity entity = level == null ? null : level.getEntity(key.uuid());
            if (entity != null && !entity.isRemoved()) flush(entity);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QUEUES.remove(event.getServer()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { sync(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { sync(event); }
    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HumanServerData data = HumanServerData.get();
            if (data != null) data.syncHumanData(player.getUUID());
        }
    }
}

