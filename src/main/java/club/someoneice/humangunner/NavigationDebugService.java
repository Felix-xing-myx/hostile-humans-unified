package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.HumanNavigation;
import com.craftix.hostile_humans.entity.entities.Human;
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
    private record Selection(UUID human, ResourceKey<Level> dimension) {}
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
    private static final Map<ServerPlayer, Selection> SELECTIONS = new WeakHashMap<>();
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
    private static Human selected(ServerPlayer player) {
        Selection selection = SELECTIONS.get(player);
        if (selection == null || selection.dimension() != player.level().dimension()) return null;
        var entity = player.serverLevel().getEntity(selection.human());
        return entity instanceof Human human && human.isAlive() && player.distanceToSqr(human) <= 128 * 128 ? human : null;
    }
    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("navigation_debug." + key, args), true);
    }
    public static void clear(ServerPlayer player) {
        Selection selection = SELECTIONS.remove(player);
        if (selection != null) {
            var level = player.server.getLevel(selection.dimension());
            if (level != null && level.getEntity(selection.human()) instanceof Human human) stopOwned(player, human);
        }
        NavigationDebugNetwork.send(player, null);
        message(player, "cleared");
    }
    private static void stopOwned(ServerPlayer player, Human human) {
        Run run = RUNS.get(human);
        if (run != null && run.owner.equals(player.getUUID())) finish(human, "cancelled");
    }
    private static void finish(Human human, String result) {
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
        // Clear through the stored dimension even if the old selection is now out of observer range.
        Selection previous = SELECTIONS.get(player);
        if (previous != null && !previous.human().equals(human.getUUID())) clear(player);
        SELECTIONS.put(player, new Selection(human.getUUID(), human.level().dimension()));
        message(player, "selected", human.getDisplayName());
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
        message(player, "destination", destination.getX(), destination.getY(), destination.getZ());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
        if (!SELECTIONS.containsKey(player)) return;
        Human human = selected(player);
        if (!allowed(player) || human == null) { clear(player); return; }
        if (controls(human) && (human.isNoAi() || human.isSleeping() || human.isPassenger()
                || human.isFleeing || human.isInLava() || HumanGunner.isManualInventoryOpen(human))) stopOwned(player, human);
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(HumanGunnerRegistries.NAVIGATION_OBSERVER_HELMET.get())) {
            NavigationDebugNetwork.send(player, null); return;
        }
        WATCHED.put(human, human.level().getGameTime() + 10);
        Trace trace = TRACES.computeIfAbsent(human, ignored -> new Trace());
        trace.samples.removeIf(sample -> sample.until() < human.level().getGameTime());
        Path path = human.getNavigation().getPath();
        int first = path == null ? 0 : path.getNextNodeIndex(), total = path == null ? 0 : path.getNodeCount();
        List<Vec3> nodes = new ArrayList<>();
        for (int i = first; i < Math.min(total, first + NavigationDebugSnapshot.MAX_PATH); i++)
            nodes.add(path.getEntityPosAtNode(human, i));
        Run run = RUNS.get(human);
        NavigationDebugNetwork.send(player, new NavigationDebugSnapshot(human.level().dimension().location(),
                human.getId(), human.getUUID(), boundedName(human), run == null ? "observe" : "test",
                run == null ? trace.state : run.state, human.getNavigation().getClass().getSimpleName(), trace.budget, trace.obstruction,
                human.getHealth(), HumanFallPolicy.damageBudget(human.getHealth()), human.getMaxFallDistance(),
                human.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE),
                run == null ? trace.requested : run.destination, human.getTarget() == null ? null : human.getTarget().position(),
                total == 0 ? null : path.getEntityPosAtNode(human, total - 1),
                List.copyOf(nodes), first, total, path != null && path.canReach(), total - first > nodes.size(),
                trace.searchMillis, trace.samples.stream().map(TimedSample::sample).toList()));
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
            if (player == null || selected(player) != human || !allowed(player) || !human.isAlive()
                    || human.isNoAi() || human.isSleeping() || human.isPassenger() || HumanGunner.isManualInventoryOpen(human)) {
                finish(human, "cancelled"); return;
            }
            if (human.level().getGameTime() >= run.deadline) { finish(human, "timeout"); message(player, "timeout"); return; }
            if (human.position().distanceToSqr(Vec3.atBottomCenterOf(run.destination)) <= 1) {
                finish(human, "arrived"); message(player, "arrived"); return;
            }
            MovementSpeedController.normal(human);
            human.getNavigation().setSpeedModifier(1);
            if (human.tickCount < run.nextSearch) return;
            if (!human.getNavigation().isDone() && !human.getNavigation().isStuck()) return;
            if (!BasicNavigationBudget.tryAcquire(human, 1)) { run.state = "queued"; return; }
            Path path = human.getNavigation() instanceof HumanNavigation navigation
                    ? navigation.createFreshPath(run.destination) : human.getNavigation().createPath(run.destination, 0);
            run.nextSearch = human.tickCount + 20;
            run.state = path == null ? "no_path" : path.canReach() ? "complete" : "partial";
            if (path != null && !path.isDone() && !human.getNavigation().moveTo(path, 1)) run.state = "rejected";
            BasicNavigationBudget.cancel(human);
        }
    }
}
