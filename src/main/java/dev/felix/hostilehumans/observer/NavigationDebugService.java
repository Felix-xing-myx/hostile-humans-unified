package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.navigation.HumanNavigation;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.observer.NavigationObserverSelection;

import dev.felix.hostilehumans.behavior.BehaviorDebugSnapshot;
import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.ContentRegistry;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.monitor.HumanMonitorControl;
import dev.felix.hostilehumans.monitor.HumanMonitorService;
import dev.felix.hostilehumans.navigation.BasicNavigationBudget;
import dev.felix.hostilehumans.navigation.HumanFallPolicy;
import dev.felix.hostilehumans.navigation.LongRangeNavigation;
import dev.felix.hostilehumans.navigation.MovementContinuity;
import dev.felix.hostilehumans.navigation.MovementSpeedController;
import dev.felix.hostilehumans.navigation.PursuitNavigation;
import dev.felix.hostilehumans.navigation.WaterCombatNavigation;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.PerceptionConfig;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in server instrumentation. Never loads chunks, grants extra searches or persists a test order. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class NavigationDebugService {
    private static final class Session {
        final ResourceKey<Level> dimension;
        final NavigationObserverSelection<UUID> selection = new NavigationObserverSelection<>(NavigationDebugFrame.MAX_ENTITIES);
        Session(ResourceKey<Level> dimension) { this.dimension = dimension; }
    }
    private static final class Run {
        final UUID owner;
        final BlockPos destination;
        final long deadline;
        int nextSearch;
        String state = "queued";
        Run(ServerPlayer player, BlockPos destination) {
            owner = player.getUUID(); this.destination = destination.immutable();
            deadline = player.serverLevel().getGameTime() + 2400;
        }
    }
    private record TimedSample(long until, NavigationDebugSnapshot.Sample sample) {}
    private static final class Trace {
        BlockPos requested;
        String state = "observing", budget = "none", obstruction = "SAFE";
        double searchMillis;
        final Deque<TimedSample> samples = new ArrayDeque<>();
    }
    private static final Map<ServerPlayer, Session> SELECTIONS = new WeakHashMap<>();
    private static final Map<Human, Run> RUNS = new WeakHashMap<>();
    private static final Map<Human, Trace> TRACES = new WeakHashMap<>();
    private static final Map<Human, Long> WATCHED = new WeakHashMap<>();
    private static final Set<Human> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerPlayer> CAMERA_OBSERVERS = Collections.newSetFromMap(new WeakHashMap<>());
    private NavigationDebugService() {}
    private static boolean allowed(ServerPlayer player) { return player.isCreative() || player.hasPermissions(2); }
    /** Attached camera identity; selection permission is checked before publishing snapshots. */
    private static Human spectatorHuman(ServerPlayer player) {
        return player.isSpectator() && player.getCamera() instanceof Human human
                && human.level() == player.level() && human.isAlive() && !human.isRemoved() ? human : null;
    }
    public static boolean controls(Human human) { return !human.level().isClientSide && RUNS.containsKey(human); }
    public static boolean watching(Human human) {
        return !human.level().isClientSide && WATCHED.getOrDefault(human, Long.MIN_VALUE) >= human.level().getGameTime();
    }
    private static Human resolve(ServerPlayer player, UUID id) {
        if (id == null) return null;
        var entity = player.serverLevel().getEntity(id);
        return entity instanceof Human human && human.isAlive() && player.distanceToSqr(human) <= 128 * 128 ? human : null;
    }
    private static CompoundTag bookmarks(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(NavigationObserverState.KEY);
    }
    private static void save(ServerPlayer player, Session session) {
        var persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var storage = persistent.getCompound(NavigationObserverState.KEY);
        NavigationObserverState.save(storage, session.dimension.location().toString(), session.selection);
        persistent.put(NavigationObserverState.KEY, storage);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
    }
    private static Session session(ServerPlayer player) {
        Session current = SELECTIONS.get(player);
        if (current != null && current.dimension != player.level().dimension()) {
            detach(player); current = null;
        }
        if (current == null && allowed(player)) {
            var saved = NavigationObserverState.load(bookmarks(player), player.level().dimension().location().toString());
            if (!saved.isEmpty()) {
                current = new Session(player.level().dimension());
                current.selection.restore(saved.entries(), saved.focused());
                SELECTIONS.put(player, current);
            }
        }
        return current;
    }
    /** Release runtime control while keeping passive bookmarks for the next connection/dimension. */
    private static void detach(ServerPlayer player) {
        Session session = SELECTIONS.remove(player);
        if (session != null) {
            var level = player.server.getLevel(session.dimension);
            if (level != null) for (var entry : session.selection.entries())
                if (level.getEntity(entry.key()) instanceof Human human) stopOwned(player, human);
        }
        CAMERA_OBSERVERS.remove(player);
        NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY);
    }
    private static Human selected(ServerPlayer player) {
        Session session = session(player);
        return session == null || session.dimension != player.level().dimension() ? null
                : resolve(player, session.selection.focused());
    }
    private static boolean observed(ServerPlayer player, Human human) {
        Session session = session(player);
        return session != null && session.dimension == human.level().dimension()
                && session.dimension == player.level().dimension() && session.selection.contains(human.getUUID())
                && resolve(player, human.getUUID()) == human;
    }
    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("navigation_debug." + key, args), true);
    }
    public static void cycle(ServerPlayer player) {
        if (!allowed(player)) { message(player, "permission"); return; }
        if (spectatorHuman(player) != null || HumanMonitorService.active(player)) return;
        Session session = session(player);
        if (session == null || session.dimension != player.level().dimension()) { message(player, "help"); return; }
        session.selection.cycle(id -> resolve(player, id) != null);
        save(player, session);
        Human human = selected(player);
        if (human != null && HumanMonitorControl.controls(human)) return;
        if (human != null) message(player, "focused", slot(session, human.getUUID()) + 1, human.getDisplayName());
        sendFrame(player, session);
    }
    static void cycleFromDetails(ServerPlayer player) {
        if(!player.getItemBySlot(EquipmentSlot.HEAD).is(ContentRegistry.NAVIGATION_OBSERVER_HELMET.get()))return;
        cycle(player);
    }
    /** Developer-only control operation; observer selection and gameplay soldier orders are unchanged. */
    static void stopFocusedTest(ServerPlayer player) {
        if (!allowed(player)) { message(player, "permission"); return; }
        if (spectatorHuman(player) != null || HumanMonitorService.active(player)) return;
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(ContentRegistry.NAVIGATION_OBSERVER_HELMET.get())) return;
        Human human = selected(player);
        if (human != null && HumanMonitorControl.controls(human)) return;
        if (human == null) { message(player, "select_first"); return; }
        Run run = RUNS.get(human);
        if (run == null) { message(player, "not_testing"); return; }
        if (!run.owner.equals(player.getUUID())) { message(player, "busy"); return; }
        finish(human, "cancelled");
        message(player, "stopped_observing", human.getDisplayName());
        sendFrame(player, SELECTIONS.get(player));
    }
    public static String controlDescription(Human human) {
        Run run = RUNS.get(human);
        if (run == null) return "自主AI运行";
        long seconds = Math.max(0, (run.deadline - human.level().getGameTime() + 19) / 20);
        String route = switch (run.state) {
            case "queued" -> "等待预算";
            case "no_path" -> "未找到路径，仍在重试";
            case "partial" -> "部分路径";
            case "rejected" -> "路线未接纳，仍在重试";
            default -> "完整路径";
        };
        return "自主战斗暂停；剩余" + seconds + "游戏秒；" + route;
    }
    private static int slot(Session session, UUID id) {
        return session.selection.entries().stream().filter(entry -> entry.key().equals(id)).findFirst().orElseThrow().slot();
    }
    public static void clear(ServerPlayer player) {
        // Explicit user removal is different from disconnecting or temporarily losing permission.
        detach(player);
        save(player, new Session(player.level().dimension()));
        message(player, "cleared");
    }
    private static void stopOwned(ServerPlayer player, Human human) {
        Run run = RUNS.get(human);
        if (run != null && run.owner.equals(player.getUUID())) finish(human, "cancelled");
    }
    private static void finish(Human human, String result) { finish(human, RUNS.get(human), result); }
    private static void finish(Human human, Run expected, String result) {
        if (expected == null || !RUNS.remove(human, expected)) return;
        LongRangeNavigation.cancel(human,LongRangeNavigation.Purpose.TEST);
        HumanBehaviorControl.interruptMovement(human, "developer test " + result);
        human.getNavigation().stop();
        MovementContinuity.cancelStep(human);
        human.clearRangedStrafeMotion();
        human.getMoveControl().setWantedPosition(human.getX(), human.getY(), human.getZ(), 0);
        BasicNavigationBudget.cancel(human);
        Trace trace = TRACES.get(human);
        if (trace != null) trace.state = result;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityClick(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getItemStack().getItem() instanceof NavigationTestItem)) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!allowed(player)) { message(player, "permission"); return; }
        if (!(event.getTarget() instanceof Human human) || !human.isAlive() || player.distanceToSqr(human) > 64) {
            message(player, "human_only"); return;
        }
        Session session = session(player);
        if (session == null) { session = new Session(player.level().dimension()); SELECTIONS.put(player, session); }
        if (player.isShiftKeyDown()) {
            if (session.selection.remove(human.getUUID())) { stopOwned(player, human); message(player, "removed", human.getDisplayName()); }
        } else if (!session.selection.add(human.getUUID())) {
            message(player, "limit", NavigationDebugFrame.MAX_ENTITIES); return;
        } else message(player, "selected_multi", slot(session, human.getUUID()) + 1, human.getDisplayName());
        save(player, session);
        sendFrame(player, session);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void blockClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof NavigationTestItem)) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isShiftKeyDown()) { clear(player); return; }
        if (!allowed(player)) { message(player, "permission"); return; }
        Human human = selected(player);
        if (human != null && HumanMonitorControl.controls(human)) return;
        if (human == null) { message(player, "select_first"); return; }
        if (player.distanceToSqr(Vec3.atCenterOf(event.getPos())) > 64) return;
        if (event.getFace() == null) return;
        BlockPos destination = event.getPos().relative(event.getFace());
        if (!player.level().hasChunkAt(destination) || !player.level().getWorldBorder().isWithinBounds(destination)) return;
        Run old = RUNS.get(human);
        if (old != null && !old.owner.equals(player.getUUID())) { message(player, "busy"); return; }
        if (human.isNoAi() || human.isSleeping() || human.isPassenger() || human.isFleeing || human.isInLava()
                || HumanRuntime.isManualInventoryOpen(human) || human.isUsingItem()
                || RecoverySupplies.hasHandCustody(human) || human.hhu$hasPendingMeleeAttack()) {
            message(player, "unavailable"); return;
        }
        // Explicitly hand off the previous movement before publishing the replacement test run.
        HumanBehaviorControl.interruptMovement(human, "developer destination changed");
        LongRangeNavigation.cancel(human, LongRangeNavigation.Purpose.TEST);
        // Store only the temporary movement lease. Owner, soldier order and health remain authoritative.
        RUNS.put(human, new Run(player, destination));
        human.setTarget(null);
        PursuitNavigation.clear(human);
        WaterCombatNavigation.clear(human);
        human.stopSeekingShore();
        MovementContinuity.cancelStep(human);
        human.clearRangedStrafeMotion();
        human.getNavigation().stop();
        human.updateSwimming();
        if (INSTALLED.add(human)) human.addCombatGoal(-100, new TestGoal(human));
        Trace trace = TRACES.computeIfAbsent(human, ignored -> new Trace());
        trace.requested = destination; trace.state = "queued"; trace.obstruction = "SAFE"; trace.samples.clear();
        message(player, "destination", slot(SELECTIONS.get(player), human.getUUID()) + 1, human.getDisplayName(), destination.getX(), destination.getY(), destination.getZ());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            detach(player);
        }
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var original = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (original.contains(NavigationObserverState.KEY)) {
            var persistent = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persistent.put(NavigationObserverState.KEY, original.getCompound(NavigationObserverState.KEY).copy());
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persistent);
        }
        // Respawn/dimension cloning never transfers a live test order or runtime player identity.
        if (event.getOriginal() instanceof ServerPlayer old) detach(old);
    }
    /** Release live slots when an entity leaves; server shutdown retains restart bookmarks. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void knownDeath(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide) return;
        var server = human.getServer();
        if (server == null || !server.isRunning()) return;
        for (var player : server.getPlayerList().getPlayers()) {
            Session active = SELECTIONS.get(player);
            Session affected;
            if (active != null && active.dimension == human.level().dimension()) affected = active;
            else {
                affected = new Session(human.level().dimension());
                var saved = NavigationObserverState.load(bookmarks(player), human.level().dimension().location().toString());
                affected.selection.restore(saved.entries(), saved.focused());
            }
            if (!affected.selection.remove(human.getUUID())) continue;
            stopOwned(player, human);
            save(player, affected);
            if (active == affected) sendFrame(player, affected);
        }
        WATCHED.remove(human);
        TRACES.remove(human);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % PerceptionConfig.get().integer("debug.interval_ticks") != 0) return;
        if (HumanMonitorService.active(player)) { sendFrame(player, null); return; }
        Session session = session(player);
        if (!allowed(player)) {
            boolean wasCameraObserver = CAMERA_OBSERVERS.remove(player);
            if (session != null || wasCameraObserver) detach(player);
            return;
        }
        if (session == null) {
            if (CAMERA_OBSERVERS.contains(player)) sendFrame(player, null);
            return;
        }
        if (spectatorHuman(player) != null) {
            // Retain the pointer's selection/focus, including members far from this camera.
            sendFrame(player, session);
            return;
        }
        for (var entry : session.selection.entries()) {
            Human human = resolve(player, entry.key());
            if (human == null) {
                // Unloaded/distant bookmarks survive; only the runtime test lease is released.
                var entity = player.serverLevel().getEntity(entry.key());
                if (entity instanceof Human old) stopOwned(player, old);
                if (entity != null && (!(entity instanceof Human) || !entity.isAlive() || entity.isRemoved())) {
                    session.selection.remove(entry.key());
                    save(player, session);
                }
            } else if (controls(human) && (human.isNoAi() || human.isSleeping() || human.isPassenger()
                    || human.isFleeing || human.isInLava() || HumanRuntime.isManualInventoryOpen(human))) stopOwned(player, human);
        }
        if (session.selection.isEmpty()) { SELECTIONS.remove(player); save(player, session); }
        sendFrame(player, session);
    }
    private static void sendFrame(ServerPlayer player, Session session) {
        if (HumanMonitorService.active(player)) {
            CAMERA_OBSERVERS.remove(player);
            Human monitor = HumanMonitorService.debugTarget(player);
            if (monitor == null) { NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY); return; }
            WATCHED.put(monitor, monitor.level().getGameTime() + PerceptionConfig.get().integer("debug.interval_ticks") + 5);
            NavigationDebugNetwork.send(player, new NavigationDebugFrame(
                    List.of(new NavigationDebugFrame.Entry(0, snapshot(monitor))), monitor.getUUID()));
            return;
        }
        if (!allowed(player) || !player.getItemBySlot(EquipmentSlot.HEAD).is(ContentRegistry.NAVIGATION_OBSERVER_HELMET.get())) {
            CAMERA_OBSERVERS.remove(player);
            NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY); return;
        }
        Human camera = spectatorHuman(player);
        if (camera != null) {
            // Attaching a camera never grants debug observation: require the pointer's explicit selection.
            // Do not resolve through the player's old physical position while the camera follows the Human.
            if (session == null || session.dimension != player.level().dimension()
                    || !session.selection.contains(camera.getUUID())) {
                CAMERA_OBSERVERS.remove(player);
                NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY);
                return;
            }
            CAMERA_OBSERVERS.add(player);
            WATCHED.put(camera, camera.level().getGameTime() + PerceptionConfig.get().integer("debug.interval_ticks") + 5);
            int cameraSlot = session.selection.entries().stream()
                    .filter(member -> member.key().equals(camera.getUUID())).mapToInt(member -> member.slot()).findFirst().orElseThrow();
            NavigationDebugNetwork.send(player, new NavigationDebugFrame(
                    List.of(new NavigationDebugFrame.Entry(cameraSlot, snapshot(camera))), camera.getUUID()));
            return;
        }
        CAMERA_OBSERVERS.remove(player);
        if (session == null) { NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY); return; }
        List<NavigationDebugFrame.Entry> entries = new ArrayList<>();
        for (var member : session.selection.entries()) {
            Human human = resolve(player, member.key());
            if (human == null) continue;
            WATCHED.put(human, human.level().getGameTime() + PerceptionConfig.get().integer("debug.interval_ticks") + 5);
            entries.add(new NavigationDebugFrame.Entry(member.slot(), snapshot(human)));
        }
        NavigationDebugNetwork.send(player, new NavigationDebugFrame(List.copyOf(entries), session.selection.focused()));
    }
    private static NavigationDebugSnapshot snapshot(Human human) {
        Trace trace = TRACES.computeIfAbsent(human, ignored -> new Trace());
        trace.samples.removeIf(sample -> sample.until() < human.level().getGameTime());
        Path path = human.getNavigation().getPath();
        int first = path == null ? 0 : path.getNextNodeIndex(), total = path == null ? 0 : path.getNodeCount();
        List<Vec3> nodes = new ArrayList<>();
        for (int i = first; i < Math.min(total, first + NavigationDebugSnapshot.MAX_PATH); i++)
            nodes.add(path.getEntityPosAtNode(human, i));
        Run run = RUNS.get(human);
        List<NavigationDebugSnapshot.Sample> samples = trace.samples.stream().map(TimedSample::sample).toList();
        samples = samples.subList(Math.max(0, samples.size() - NavigationDebugFrame.SENT_SAMPLES), samples.size());
        return new NavigationDebugSnapshot(human.level().dimension().location(),
                human.getId(), human.getUUID(), boundedName(human), run == null ? "observe" : "test",
                run == null ? trace.state : run.state, human.getNavigation().getClass().getSimpleName(), routeBudget(human,trace.budget), trace.obstruction,
                human.getHealth(), HumanFallPolicy.damageBudget(human.getHealth()), human.getMaxFallDistance(),
                human.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE), human.position(),
                run == null ? trace.requested : run.destination, human.getTarget() == null ? null : HumanPerception.knownPosition(human, human.getTarget()),
                total == 0 ? null : path.getEntityPosAtNode(human, total - 1),
                List.copyOf(nodes), first, total, path != null && path.canReach(), total - first > nodes.size(),
                trace.searchMillis, List.copyOf(samples), HumanPerception.snapshot(human), BehaviorDebugSnapshot.capture(human));
    }
    private static String routeBudget(Human human,String original) {
        String detail=LongRangeNavigation.describe(human);
        String value=detail.isEmpty()?original:original+"; "+detail;
        return value.substring(0,Math.min(96,value.length()));
    }
    private static String boundedName(Human human) {
        String name = human.getName().getString();
        return name.substring(0, Math.min(name.length(), 80));
    }
    /** Called only for searches that actually occurred; null does not prove budget exhaustion. */
    public static void searched(Human human, BlockPos requested, Path result, long elapsedNanos) {
        if (!watching(human)) return;
        Trace trace = TRACES.computeIfAbsent(human, ignored -> new Trace());
        trace.requested = requested; trace.searchMillis = elapsedNanos / 1_000_000.0;
        trace.state = result == null ? "no_path" : result.canReach() ? "complete" : "partial";
        candidate(human, Vec3.atBottomCenterOf(requested), "navigation", trace.state, Double.NaN);
    }
    public static void candidate(Human human, Vec3 position, String source, String result, double score) {
        if (!watching(human)) return;
        Trace trace = TRACES.computeIfAbsent(human, ignored -> new Trace());
        while (trace.samples.size() >= NavigationDebugSnapshot.MAX_SAMPLES) trace.samples.removeFirst();
        trace.samples.addLast(new TimedSample(human.level().getGameTime() + 100,
                new NavigationDebugSnapshot.Sample(position, source, result, score)));
    }
    public static void budget(Human human, String pool, int requested, int granted) {
        if (watching(human)) TRACES.computeIfAbsent(human, ignored -> new Trace()).budget
                = pool + ":" + granted + "/" + requested;
    }
    public static void blocked(Human human, String reason) {
        if (watching(human)) TRACES.computeIfAbsent(human, ignored -> new Trace()).obstruction = reason;
    }
    private static final class TestGoal extends Goal {
        private final Human human;
        private Run activeRun;
        TestGoal(Human human) { this.human = human; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return controls(human); }
        @Override public boolean canContinueToUse() { return activeRun != null && RUNS.get(human) == activeRun; }
        @Override public void start() { activeRun = RUNS.get(human); }
        @Override public void stop() {
            Run ending = activeRun; activeRun = null;
            if (ending != null) finish(human, ending, "interrupted");
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            Run run = activeRun;
            if (run == null || RUNS.get(human) != run) return;
            ServerPlayer player = human.level().getServer().getPlayerList().getPlayer(run.owner);
            if (player == null || !observed(player, human) || !allowed(player) || !human.isAlive()
                    || human.isNoAi() || human.isSleeping() || human.isPassenger() || HumanRuntime.isManualInventoryOpen(human)) {
                finish(human, run, "cancelled"); return;
            }
            if (human.level().getGameTime() >= run.deadline) { finish(human, run, "timeout"); message(player, "timeout", slot(SELECTIONS.get(player), human.getUUID()) + 1, human.getDisplayName()); return; }
            if (human.position().distanceToSqr(Vec3.atBottomCenterOf(run.destination)) <= 1) {
                finish(human, run, "arrived"); message(player, "arrived", slot(SELECTIONS.get(player), human.getUUID()) + 1, human.getDisplayName()); return;
            }
            MovementSpeedController.normal(human);
            human.getNavigation().setSpeedModifier(1);
            if (human.tickCount < run.nextSearch) return;
            if (!human.getNavigation().isDone() && !human.getNavigation().isStuck()
                    && !(human.getNavigation() instanceof HumanNavigation ground && ground.needsBoundedRefresh())) return;
            var query = LongRangeNavigation.search(human,run.destination,LongRangeNavigation.Purpose.TEST,0);
            if(query.pending()){run.state="queued";return;}
            Path path = query.path();
            if(query.status()==LongRangeNavigation.Status.UNSUPPORTED){
                if(!BasicNavigationBudget.tryAcquire(human,1)){run.state="queued";return;}
                BasicNavigationBudget.cancel(human);
                path=human.getNavigation().createPath(run.destination,0);
            }
            run.nextSearch = human.tickCount + 20;
            run.state = path == null ? "no_path" : path.canReach() ? "complete" : "partial";
            if (path != null && !path.isDone() && !human.getNavigation().moveTo(path, 1)) run.state = "rejected";
            if(path!=null&&human.getNavigation().getPath()!=null)LongRangeNavigation.accepted(human,LongRangeNavigation.Purpose.TEST);
            BasicNavigationBudget.cancel(human);
        }
    }
}
