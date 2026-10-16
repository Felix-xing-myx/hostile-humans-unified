package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.HumanNavigation;
import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.NavigationObserverSelection;
import java.util.*;
import net.minecraft.core.BlockPos;
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
@Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID)
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
    private NavigationDebugService() {}
    private static boolean allowed(ServerPlayer player) { return player.isCreative() || player.hasPermissions(2); }
    public static boolean controls(Human human) { return !human.level().isClientSide && RUNS.containsKey(human); }
    public static boolean watching(Human human) {
        return !human.level().isClientSide && WATCHED.getOrDefault(human, Long.MIN_VALUE) >= human.level().getGameTime();
    }
    private static Human resolve(ServerPlayer player, UUID id) {
        if (id == null) return null;
        var entity = player.serverLevel().getEntity(id);
        return entity instanceof Human human && human.isAlive() && player.distanceToSqr(human) <= 128 * 128 ? human : null;
    }
    private static Human selected(ServerPlayer player) {
        Session session = SELECTIONS.get(player);
        return session == null || session.dimension != player.level().dimension() ? null
                : resolve(player, session.selection.focused());
    }
    private static boolean observed(ServerPlayer player, Human human) {
        Session session = SELECTIONS.get(player);
        return session != null && session.dimension == human.level().dimension()
                && session.dimension == player.level().dimension() && session.selection.contains(human.getUUID())
                && resolve(player, human.getUUID()) == human;
    }
    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("navigation_debug." + key, args), true);
    }
    public static void cycle(ServerPlayer player) {
        if (!allowed(player)) { message(player, "permission"); return; }
        Session session = SELECTIONS.get(player);
        if (session == null || session.dimension != player.level().dimension()) { message(player, "help"); return; }
        for(var entry:session.selection.entries())if(resolve(player,entry.key())==null){
            if(player.serverLevel().getEntity(entry.key()) instanceof Human old)stopOwned(player,old);
            session.selection.remove(entry.key());
        }
        session.selection.cycle();
        Human human = selected(player);
        if (human != null) message(player, "focused", slot(session, human.getUUID()) + 1, human.getDisplayName());
        sendFrame(player, session);
    }
    static void cycleFromDetails(ServerPlayer player) {
        if(!player.getItemBySlot(EquipmentSlot.HEAD).is(HumanGunnerRegistries.NAVIGATION_OBSERVER_HELMET.get()))return;
        cycle(player);
    }
    /** Developer-only control operation; observer selection and gameplay soldier orders are unchanged. */
    static void stopFocusedTest(ServerPlayer player) {
        if (!allowed(player)) { message(player, "permission"); return; }
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(HumanGunnerRegistries.NAVIGATION_OBSERVER_HELMET.get())) return;
        Human human = selected(player);
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
        Session session = SELECTIONS.remove(player);
        if (session != null) {
            var level = player.server.getLevel(session.dimension);
            if (level != null) for (var entry : session.selection.entries())
                if (level.getEntity(entry.key()) instanceof Human human) stopOwned(player, human);
        }
        NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY);
        message(player, "cleared");
    }
    private static void stopOwned(ServerPlayer player, Human human) {
        Run run = RUNS.get(human);
        if (run != null && run.owner.equals(player.getUUID())) finish(human, "cancelled");
    }
    private static void finish(Human human, String result) {
        LongRangeNavigation.cancel(human,LongRangeNavigation.Purpose.TEST);
        RUNS.remove(human);
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
        Session session = SELECTIONS.get(player);
        if (session != null && session.dimension != player.level().dimension()) { clear(player); session = null; }
        if (session == null) { session = new Session(player.level().dimension()); SELECTIONS.put(player, session); }
        if (player.isShiftKeyDown()) {
            if (session.selection.remove(human.getUUID())) { stopOwned(player, human); message(player, "removed", human.getDisplayName()); }
        } else if (!session.selection.add(human.getUUID())) {
            message(player, "limit", NavigationDebugFrame.MAX_ENTITIES); return;
        } else message(player, "selected_multi", slot(session, human.getUUID()) + 1, human.getDisplayName());
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
        if (human == null) { message(player, "select_first"); return; }
        if (player.distanceToSqr(Vec3.atCenterOf(event.getPos())) > 64) return;
        if (event.getFace() == null) return;
        BlockPos destination = event.getPos().relative(event.getFace());
        if (!player.level().hasChunkAt(destination) || !player.level().getWorldBorder().isWithinBounds(destination)) return;
        Run old = RUNS.get(human);
        if (old != null && !old.owner.equals(player.getUUID())) { message(player, "busy"); return; }
        if (human.isNoAi() || human.isSleeping() || human.isPassenger() || human.isFleeing || human.isInLava()
                || HumanGunner.isManualInventoryOpen(human) || human.isUsingItem()
                || RecoverySupplies.hasHandCustody(human) || human.humanGunner$hasPendingMeleeAttack()) {
            message(player, "unavailable"); return;
        }
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
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % PerceptionConfig.get().integer("debug.interval_ticks") != 0) return;
        Session session = SELECTIONS.get(player);
        if (session == null) return;
        if (!allowed(player) || session.dimension != player.level().dimension()) { clear(player); return; }
        for (var entry : session.selection.entries()) {
            Human human = resolve(player, entry.key());
            if (human == null) {
                // Out-of-range members must release only this player's task, not another observer's task.
                if (player.serverLevel().getEntity(entry.key()) instanceof Human old) stopOwned(player, old);
                session.selection.remove(entry.key());
            } else if (controls(human) && (human.isNoAi() || human.isSleeping() || human.isPassenger()
                    || human.isFleeing || human.isInLava() || HumanGunner.isManualInventoryOpen(human))) stopOwned(player, human);
        }
        if (session.selection.isEmpty()) SELECTIONS.remove(player);
        sendFrame(player, session);
    }
    private static void sendFrame(ServerPlayer player, Session session) {
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(HumanGunnerRegistries.NAVIGATION_OBSERVER_HELMET.get())) {
            NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY); return;
        }
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
                trace.searchMillis, List.copyOf(samples), HumanPerception.snapshot(human));
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
        TestGoal(Human human) { this.human = human; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return controls(human); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void tick() {
            Run run = RUNS.get(human);
            if (run == null) return;
            ServerPlayer player = human.level().getServer().getPlayerList().getPlayer(run.owner);
            if (player == null || !observed(player, human) || !allowed(player) || !human.isAlive()
                    || human.isNoAi() || human.isSleeping() || human.isPassenger() || HumanGunner.isManualInventoryOpen(human)) {
                finish(human, "cancelled"); return;
            }
            if (human.level().getGameTime() >= run.deadline) { finish(human, "timeout"); message(player, "timeout", slot(SELECTIONS.get(player), human.getUUID()) + 1, human.getDisplayName()); return; }
            if (human.position().distanceToSqr(Vec3.atBottomCenterOf(run.destination)) <= 1) {
                finish(human, "arrived"); message(player, "arrived", slot(SELECTIONS.get(player), human.getUUID()) + 1, human.getDisplayName()); return;
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
