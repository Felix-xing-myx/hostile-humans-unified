package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.inventory.HumanInventoryMenu;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.observer.NavigationDebugFrame;
import dev.felix.hostilehumans.observer.NavigationDebugNetwork;
import dev.felix.hostilehumans.soldier.CommandView;
import dev.felix.hostilehumans.soldier.GarrisonSettings;
import dev.felix.hostilehumans.soldier.NaturalState;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Viewing permission is a short server lease, never an ownership or game-mode change. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class HumanMonitorService {
    private static final class Session {
        final UUID token = UUID.randomUUID(), target;
        final int entityId;
        final ResourceKey<Level> dimension;
        long deadline, lastAction = Long.MIN_VALUE;
        boolean debug;
        MonitorWeaponSnapshot weapon;
        Session(ServerPlayer player, Human human) {
            target = human.getUUID(); entityId = human.getId(); dimension = human.level().dimension();
            deadline = player.serverLevel().getGameTime() + 60;
        }
    }
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private HumanMonitorService() {}

    static Human target(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        if (session == null || player.isSpectator() || !player.isAlive()
                || player.level().dimension() != session.dimension
                || player.serverLevel().getGameTime() > session.deadline) return null;
        var entity = player.serverLevel().getEntity(session.target);
        return entity instanceof Human human && session.target.equals(human.getUUID())
                && MonitorAccessPolicy.allows(player, human) ? human : null;
    }
    /** Validated live viewing leases only; never accepts a client-supplied audio position. */
    static void forEachTarget(java.util.function.BiConsumer<ServerPlayer, Human> consumer) {
        for (ServerPlayer player : List.copyOf(SESSIONS.keySet())) {
            Human human = target(player);
            if (human != null) consumer.accept(player, human);
        }
    }
    @FunctionalInterface interface RecoilLease { void accept(ServerPlayer player,UUID token,long generation); }
    /** Read-only projection of validated, live control leases; no authority or AI state is changed. */
    static void forEachRecoilLease(Human subject,RecoilLease consumer) {
        for(var entry:List.copyOf(SESSIONS.entrySet())) {
            var player=entry.getKey();var session=entry.getValue();
            if(target(player)!=subject||!HumanMonitorControl.ownedBy(subject,player,session.token))continue;
            long generation=HumanMonitorControl.generation(subject,player,session.token);
            if(generation>0)consumer.accept(player,session.token,generation);
        }
    }
    private static boolean canControl(ServerPlayer player, Human human) {
        Session session = SESSIONS.get(player);
        return session != null && HumanMonitorControl.ownedBy(human, player, session.token);
    }
    public static boolean active(ServerPlayer player) { return target(player) != null; }
    public static Human debugTarget(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        return session != null && session.debug ? target(player) : null;
    }
    public static boolean canAccess(ServerPlayer player, Human human) { return target(player) == human; }
    public static void refresh(ServerPlayer player) {
        Human human = target(player); Session session = SESSIONS.get(player);
        if (human != null && session != null) publish(player, session, human);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof HumanMonitorItem)) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSpectator() || !(event.getTarget() instanceof Human human)
                || !MonitorAccessPolicy.allows(player, human)
                || !human.isAlive() || human.isRemoved() || human.level() != player.level()
                || player.distanceToSqr(human) > 64) return;
        MonitorTargetBinding.bind(event.getItemStack(), human);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("human_monitor.bound", human.getDisplayName()), true);
    }
    public static void open(ServerPlayer player, Human human) {
        if (!MonitorAccessPolicy.allows(player, human)) return;
        close(player);
        Session session = new Session(player, human);
        SESSIONS.put(player, session);
        if (!dev.felix.hostilehumans.monitor.remote.MonitorRemoteView.start(player, human)) { close(player); return; }
        publish(player, session, human);
    }
    public static void openOwned(ServerPlayer player, UUID target) {
        if (!dev.felix.hostilehumans.soldier.RecruitmentLedger.get(player.server).isOwnedRecord(target, player.getUUID())) return;
        var entity = player.serverLevel().getEntity(target);
        if (entity instanceof Human human) { open(player, human); return; }
        var saved = dev.felix.hostilehumans.entity.HumanServerData.get();
        var data = saved == null ? null : saved.getHumanMob(target);
        if (data == null || !player.getUUID().equals(data.getOwnerUUID()) || data.getLevelKey() != player.level().dimension()) {
            unavailable(player); return;
        }
        resolve(player, target, data.getBlockPos());
    }
    static void resolve(ServerPlayer player, UUID target, net.minecraft.core.BlockPos trustedPosition) {
        close(player);
        if (!dev.felix.hostilehumans.monitor.remote.MonitorRemoteView.bootstrap(player, player.serverLevel(), trustedPosition, target,
                human -> MonitorAccessPolicy.allows(player, human), human -> open(player, human), ignored -> unavailable(player))) unavailable(player);
    }
    static void unavailable(ServerPlayer player) { player.displayClientMessage(net.minecraft.network.chat.Component.translatable("human_monitor.target.unavailable"), true); }
    /** Requests carry only the lease and a bounded operation, never a client-provided target. */
    static void request(ServerPlayer player, UUID token, int action, boolean enabled, int mode) {
        Session session = SESSIONS.get(player);
        if (session == null || !session.token.equals(token)) return;
        if (action == 1) { close(player); return; }
        Human human = target(player);
        if (human == null) { close(player); return; }
        if (action == 0) { session.deadline = player.serverLevel().getGameTime() + 60; return; }
        long now = player.serverLevel().getGameTime();
        if (session.lastAction != Long.MIN_VALUE && now - session.lastAction < 4) return;
        session.lastAction = now;
        if ((action == 2 || action == 4) && HumanMonitorControl.controls(human) && !canControl(player, human)) return;
        switch (action) {
            case 2 -> {
                if (!HumanMonitorControl.pauseForInventory(player, token, human)) return;
                if (player.containerMenu == player.inventoryMenu) HumanInventoryMenu.openMonitored(player, human);
                if (!(player.containerMenu instanceof HumanInventoryMenu menu && menu.monitored()))
                    HumanMonitorControl.inventoryClosed(human, player);
            }
            case 3 -> session.debug = enabled;
            case 5 -> {
                if (enabled) HumanMonitorControl.begin(player, token, human);
                else HumanMonitorControl.release(player, token);
            }
            case 6 -> {
                if (!HumanMonitorControl.toggleOffhand(player, token, human)) failedEquipment(player);
            }
            case 4 -> {
                HumanMonitorControl.release(player, token);
                if (HumanInventorySession.isEditing(human) || mode < 0 || mode > 3) return;
                if (human.hasOwner()) {
                    if (mode == 0) CommandView.apply(human, CommandView.order(SoldierOrder.FOLLOW));
                    else if (mode == 3) CommandView.apply(human, CommandView.order(SoldierOrder.BODYGUARD));
                    else {
                        SoldierOrder.set(human, SoldierOrder.GARRISON);
                        CommandView.apply(human, CommandView.settings(new GarrisonSettings(mode == 1, mode == 2, GarrisonSettings.player(human).radius())));
                    }
                } else {
                    if (mode == 3) return; // A natural Human has no employer to protect.
                    if (mode == 0) NaturalState.suspendForController(human);
                    else {
                        if(!NaturalState.hasState(human))NaturalState.restoreForController(human);
                        NaturalState.set(human, new GarrisonSettings(mode == 1, mode == 2, GarrisonSettings.natural(human).radius()));
                    }
                    CommandView.persist(human);
                }
            }
            default -> { return; }
        }
        publish(player, session, human);
    }
    static void select(ServerPlayer player, UUID token, long generation, long revision, UUID weapon) {
        Session session = SESSIONS.get(player);
        Human human = target(player);
        if (session == null || !session.token.equals(token) || human == null) return;
        long now = player.serverLevel().getGameTime();
        if (session.lastAction != Long.MIN_VALUE && now - session.lastAction < 2) return;
        session.lastAction = now;
        if (!HumanMonitorControl.select(player, token, human, generation, revision, weapon)) failedEquipment(player);
        publish(player, session, human);
    }
    private static void failedEquipment(ServerPlayer player) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("human_monitor.equipment.unavailable"), true);
    }
    private static int mode(Human human) {
        if (!human.hasOwner()) return !NaturalState.hasState(human) ? 0 : GarrisonSettings.natural(human).stay() ? 1 : 2;
        return SoldierOrder.get(human) == SoldierOrder.BODYGUARD ? 3 : SoldierOrder.get(human) == SoldierOrder.GARRISON
                ? GarrisonSettings.player(human).stay() ? 1 : 2 : 0;
    }
    private static void publish(ServerPlayer player, Session session, Human human) {
        HumanMonitorNetwork.send(player, new HumanMonitorNetwork.State(session.token, human.getUUID(), human.getId(),
                human.getHealth(), human.getMaxHealth(), human.getDisplayName().getString(), human.hasOwner(), mode(human), session.debug, true, HumanMonitorControl.controls(human) && canControl(player, human), HumanMonitorControl.generation(human, player, session.token),
                dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies.pendingFoodHealing(human)));
        HumanMonitorControl.publishLoadout(player, session.token, human);
        publishWeapon(player, session, human);
    }
    /** Called at the post-action boundary as well as the observation heartbeat; changes only. */
    static void publishWeapon(Human human) {
        for (var entry : SESSIONS.entrySet()) {
            Session session = entry.getValue();
            if (session.target.equals(human.getUUID()) && session.dimension == human.level().dimension()
                    && target(entry.getKey()) == human) publishWeapon(entry.getKey(), session, human);
        }
    }
    private static void publishWeapon(ServerPlayer player, Session session, Human human) {
        var guns = dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get();
        var weapon = new MonitorWeaponSnapshot(session.token, human.getUUID(), human.getId(),
                HumanMonitorControl.generation(human, player, session.token), guns.hud(human.getMainHandItem()),
                dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo.magazineCount(human),
                !dev.felix.hostilehumans.config.UnifiedConfig.get().ammoConsumptionEnabled(), guns.isReloading(human, human.getMainHandItem()));
        if (!weapon.equals(session.weapon)) { session.weapon = weapon; HumanMonitorNetwork.sendWeapon(player, weapon); }
    }
    static void close(ServerPlayer player) {
        dev.felix.hostilehumans.monitor.remote.MonitorRemoteView.stop(player);
        Session session = SESSIONS.remove(player);
        if (session == null) return;
        HumanMonitorControl.release(player, session.token);
        NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY);
        if (player.containerMenu instanceof HumanInventoryMenu menu && menu.monitored()) player.closeContainer();
        HumanMonitorNetwork.send(player, new HumanMonitorNetwork.State(session.token, session.target, session.entityId,
                0, 1, "", false, 0, false, false, false));
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !SESSIONS.containsKey(player)) return;
        Human human = target(player);
        if (human == null || !dev.felix.hostilehumans.monitor.remote.MonitorRemoteView.tick(player, human)) close(player);
        else if (player.tickCount % 5 == 0) publish(player, SESSIONS.get(player), human);
    }
    /** All world/entity actions have completed; observing and takeover share this authoritative boundary. */
    @SubscribeEvent public static void telemetry(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var entry:List.copyOf(SESSIONS.entrySet())) {
            Human human=target(entry.getKey());
            if(human!=null)publishWeapon(entry.getKey(),entry.getValue(),human);
        }
    }
    @SubscribeEvent public static void leaving(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide) return;
        for (var entry : List.copyOf(SESSIONS.entrySet())) {
            if (entry.getValue().target.equals(human.getUUID()) && entry.getValue().dimension == human.level().dimension())
                close(entry.getKey());
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) close(player);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        for (ServerPlayer player : List.copyOf(SESSIONS.keySet())) if (player.server == event.getServer()) close(player);
    }
}
