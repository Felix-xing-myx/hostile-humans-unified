package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.data.HumanData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Collects worthwhile loot while idle without interrupting combat behavior. */
final class ValuableItemPickupGoal extends Goal {
    private static final int MAX_SCREENED_ITEMS = 32;
    private static final int SCREENED_COOLDOWN_TICKS = 120;
    private final Human human;
    private final Map<UUID, ScreenedItem> screenedItems = new LinkedHashMap<>();
    private final Map<UUID, FailedItem> failedItems = new LinkedHashMap<>();
    private final Map<FailedItem, Boolean> failureContextChecks = new java.util.HashMap<>();
    private final dev.felix.hostilehumans.core.PickupRouteProgress progress = new dev.felix.hostilehumans.core.PickupRouteProgress();
    private Vec3 knownItemPosition;
    private boolean waitingForPath, directWaterFallback;
    private int arrivedAt = -1;
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();
    private final ItemStack[] screenedEquipment = new ItemStack[EQUIPMENT_SLOTS.length];
    private HumanData screenedInventory;
    private HumanLootManager.NearbySearch pendingSearch;
    private int lastSelectionTick = Integer.MIN_VALUE;
    private boolean selectionDeferred;
    private ItemEntity targetItem;
    private Vec3 pileCenter;
    private Vec3 lastPathItemPosition;
    private net.minecraft.world.level.pathfinder.Path ownedPath;
    private int nextSearchTick;
    private int nextRepathTick;
    private int nextValidationTick;

    ValuableItemPickupGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!SoldierPickupPolicy.canCollectNow(human)
                || !human.isAlive()
                || human.getTarget() != null
                || human.isFleeing
                || human.isUsingItem()
                || !SoldierPickupPolicy.isEnabled(human)
                || !SoldierOrder.allowsLootPosition(human, human.getX(), human.getZ())
                || SoldierOrder.isHoldingPosition(human)) {
            pendingSearch = null;
            selectionDeferred = false;
            pileCenter = null;
            return false;
        }
        if (human.tickCount < nextSearchTick) {
            return false;
        }
        nextSearchTick = human.tickCount + 20 + human.getRandom().nextInt(20);
        targetItem = chooseTarget();
        knownItemPosition = targetItem == null ? null : HumanLootVision.lastSeen(human, targetItem);
        return targetItem != null && knownItemPosition != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetItem != null
                && SoldierPickupPolicy.canCollectNow(human)
                && targetItem.isAlive()
                && !targetItem.hasPickUpDelay()
                && !targetItem.getItem().isEmpty()
                && human.getTarget() == null
                && !human.isFleeing
                && !human.isUsingItem()
                && SoldierPickupPolicy.isEnabled(human)
                && !SoldierOrder.isHoldingPosition(human)
                && knownItemPosition != null
                && SoldierOrder.allowsLootPosition(human, knownItemPosition.x, knownItemPosition.z);
    }

    @Override
    public void start() {
        if (!canContinueToUse()) { SoldierPickupPolicy.interruptForCombat(human); return; }
        human.setActivelyCollectingLoot(true);
        beginTarget();
    }

    @Override
    public void tick() {
        if (!canContinueToUse()) {
            stop();
            return;
        }
        HumanLootVision.Sight sight = HumanLootVision.inspect(human, targetItem);
        if (sight == HumanLootVision.Sight.VISIBLE) {
            Vec3 observed = HumanLootVision.lastSeen(human, targetItem);
            if (observed != null) knownItemPosition = observed;
        }
        human.setPursuingWaterLoot(human.isInWater()
                || human.level().getFluidState(net.minecraft.core.BlockPos.containing(knownItemPosition)).is(net.minecraft.tags.FluidTags.WATER));
        if (sight == HumanLootVision.Sight.VISIBLE && human.tickCount >= nextValidationTick) {
            nextValidationTick = human.tickCount + 20;
            if (!HumanLootManager.isWorthCollecting(human, targetItem)) {
                rememberScreened(targetItem, SCREENED_COOLDOWN_TICKS);
                advanceTarget();
                return;
            }
        }
        human.getLookControl().setLookAt(knownItemPosition.x, knownItemPosition.y + .15D, knownItemPosition.z, 25.0F, 25.0F);
        HumanAttention.observeLoot(human, knownItemPosition.add(0,.15D,0));
        if (sight == HumanLootVision.Sight.VISIBLE && human.distanceToSqr(targetItem) <= 2.25D) {
            if (!HumanLootManager.collect(human, targetItem)) {
                rememberScreened(targetItem, SCREENED_COOLDOWN_TICKS);
            }
            failedItems.remove(targetItem.getUUID());
            advanceTarget();
            return;
        }
        if (human.getNavigation().isDone() && human.position().distanceToSqr(knownItemPosition) <= 2.25D) {
            if (arrivedAt < 0) arrivedAt = human.tickCount;
            if (sight == HumanLootVision.Sight.DEFERRED || human.tickCount - arrivedAt < 10) return;
            failTarget("到达最后目视位置，仍不能确认并拾取物品", null);
            return;
        }
        arrivedAt = -1;
        var route = human.getNavigation().getPath();
        boolean owned = ownsPath(route, ownedPath) || MovementContinuity.ownsRoute(human, ownedPath);
        boolean activeRoute = owned && route != null && !route.isDone();
        Vec3 next = activeRoute ? route.getNextEntityPos(human) : knownItemPosition;
        boolean waiting = waitingForPath && !activeRoute || directWaterFallback && sight == HumanLootVision.Sight.DEFERRED;
        if (progress.stalled(human.tickCount, activeRoute ? route : null, activeRoute ? route.getNextNodeIndex() : -1,
                human.position().distanceTo(next), human.getX(), human.getY(), human.getZ(), waiting)) {
            failTarget("拾取路线无进展", route);
            return;
        }
        if (human.tickCount >= nextRepathTick
                && (human.getNavigation().isDone() || human.getNavigation().isStuck()
                || human.getNavigation() instanceof com.craftix.hostile_humans.entity.ai.HumanNavigation ground && ground.needsBoundedRefresh()
                || lastPathItemPosition == null || lastPathItemPosition.distanceToSqr(knownItemPosition) > 4.0D)) {
            moveToItem();
        }
        if (targetItem == null) return;
        // An unseen moving item must not steer the human using its hidden current coordinates.
        if (sight == HumanLootVision.Sight.VISIBLE) human.approachWaterLoot(targetItem, 0.9D);
        if (human.horizontalCollision) {
            NavigationSupport.openBlockingPassage(human);
        }
    }

    @Override
    public void stop() {
        boolean ownedMovement = ownsPath(human.getNavigation().getPath(), ownedPath)
                || MovementContinuity.ownsRoute(human, ownedPath)
                || human.isPursuingWaterLoot() && human.getNavigation().isDone();
        targetItem = null;
        // A normal target completion can leave an unfinished decision. It
        // owns no navigation or water movement; combat interrupts discard it.
        if (pendingSearch == null && !selectionDeferred) pileCenter = null;
        human.setActivelyCollectingLoot(false);
        human.setPursuingWaterLoot(false);
        if (ownsPath(human.getNavigation().getPath(), ownedPath)
                || MovementContinuity.ownsRoute(human, ownedPath)) human.getNavigation().stop();
        if (ownedMovement) {
            MovementContinuity.cancelStep(human);
            human.getMoveControl().setWantedPosition(human.getX(), human.getY(), human.getZ(), 0);
            human.setXxa(0); human.setZza(0);
        }
        ownedPath = null;
        knownItemPosition = null;
        waitingForPath = directWaterFallback = false;
        BasicNavigationBudget.cancel(human);
        LongRangeNavigation.cancel(human,LongRangeNavigation.Purpose.LOOT);
    }

    void interruptForCombat() {
        pendingSearch = null;
        selectionDeferred = false;
        pileCenter = null;
        nextSearchTick = Math.max(nextSearchTick, human.tickCount + 20);
        stop();
    }

    private ItemEntity chooseTarget() {
        if (lastSelectionTick == human.tickCount) {
            selectionDeferred = true;
            nextSearchTick = human.tickCount + 1;
            return null;
        }
        lastSelectionTick = human.tickCount;
        selectionDeferred = false;
        refreshScreeningEquipment();
        failureContextChecks.clear();
        screenedItems.entrySet().removeIf(entry -> entry.getValue().expiresAt <= human.tickCount);
        if (pendingSearch == null || !pendingSearch.isCurrent(human)) {
            pendingSearch = new HumanLootManager.NearbySearch(human, 12.0D, pileCenter);
        }
        ItemEntity chosen = pendingSearch.advance(64,
                item -> isScreened(item) || isFailed(item) || !SoldierOrder.allowsLootPosition(human, item.getX(), item.getZ()),
                item -> rememberScreened(item, SCREENED_COOLDOWN_TICKS));
        if (!pendingSearch.isDone()) {
            nextSearchTick = human.tickCount + 1;
            return null;
        }
        pendingSearch = null;
        if (chosen == null) {
            pileCenter = null;
        } else if (pileCenter == null || chosen.position().distanceToSqr(pileCenter) > 16.0D) {
            // Commit to one local pile until it has no useful reachable item.
            pileCenter = HumanLootVision.lastSeen(human, chosen);
        }
        return chosen;
    }

    static boolean ownsPath(Object current, Object assigned) {
        return assigned != null && current == assigned;
    }

    private void beginTarget() {
        if (targetItem == null) return;
        knownItemPosition = HumanLootVision.lastSeen(human, targetItem);
        if (knownItemPosition == null) { targetItem = null; return; }
        progress.reset(human.tickCount, human.getX(), human.getY(), human.getZ());
        arrivedAt = -1;
        waitingForPath = directWaterFallback = false;
        nextValidationTick = human.tickCount + 20;
        nextRepathTick = 0;
        lastPathItemPosition = null;
        human.setPursuingWaterLoot(human.isInWater());
        if (human.position().distanceToSqr(knownItemPosition) <= 2.25D) { nextRepathTick = human.tickCount + 10; return; }
        moveToItem();
    }

    private void releasePickupRoute() {
        boolean ownRoute = ownsPath(human.getNavigation().getPath(), ownedPath) || MovementContinuity.ownsRoute(human, ownedPath);
        boolean ownDirectInput = directWaterFallback && human.getNavigation().isDone();
        if (ownRoute || ownDirectInput) {
            if (ownRoute) human.getNavigation().stop();
            MovementContinuity.cancelStep(human);
            human.getMoveControl().setWantedPosition(human.getX(), human.getY(), human.getZ(), 0);
            human.setXxa(0);human.setZza(0);
        }
        ownedPath = null;
        directWaterFallback = false;
    }

    private void advanceTarget() {
        releasePickupRoute();
        human.setPursuingWaterLoot(false);
        waitingForPath = false;
        targetItem = chooseTarget();
        if (targetItem != null) beginTarget();
    }

    private void moveToItem() {
        if (targetItem == null || knownItemPosition == null) return;
        var nav = human.getNavigation();
        var previous = nav.getPath();
        boolean hadOwnedRoute = previous != null && !previous.isDone()
                && (ownsPath(previous, ownedPath) || MovementContinuity.ownsRoute(human, ownedPath));
        var goal = net.minecraft.core.BlockPos.containing(knownItemPosition);
        var query = LongRangeNavigation.search(human,goal,LongRangeNavigation.Purpose.LOOT,0);
        if (query.pending()) {
            waitingForPath = true;
            nextRepathTick = human.tickCount + 1;
            HumanPerception.decision(human,"等待拾取路径","分档或配额等待，不算不可达");
            return;
        }
        waitingForPath = false;
        net.minecraft.world.level.pathfinder.Path path = query.path();
        if (query.status() == LongRangeNavigation.Status.UNSUPPORTED) {
            if (!BasicNavigationBudget.tryAcquire(human,1)) { waitingForPath=true;nextRepathTick=human.tickCount+1;return; }
            BasicNavigationBudget.cancel(human);
            path=nav.createPath(goal,0);
        }
        boolean safe = usableLootPath(path);
        if (safe && nav.moveTo(path, .9D)) {
            ownedPath = nav.getPath();
            LongRangeNavigation.accepted(human,LongRangeNavigation.Purpose.LOOT);
            lastPathItemPosition = knownItemPosition;
            nextRepathTick = human.tickCount + 20;
            directWaterFallback = false;
            HumanPerception.decision(human,"拾取已观察物品","沿完整安全路径前往最后目视位置");
            return;
        }
        // A failed replacement cannot erase an already usable owned route.
        if (hadOwnedRoute && nav.getPath() != previous && usableLootPath(previous)
                && nav.moveTo(previous, .9D)) ownedPath = nav.getPath();
        var current = nav.getPath();
        if (usableLootPath(current) && (ownsPath(current, ownedPath) || MovementContinuity.ownsRoute(human, ownedPath))) {
            nextRepathTick = human.tickCount + 20;
            return;
        }
        if (hadOwnedRoute) releasePickupRoute();
        if (targetItem.isInWater() && (human.shouldUseWaterMovement() || Math.abs(human.getY() - targetItem.getY()) <= 2)
                && HumanLootManager.isReachableWithoutDiving(targetItem)) {
            HumanLootVision.Sight sight = HumanLootVision.inspect(human, targetItem);
            if (sight == HumanLootVision.Sight.DEFERRED) {
                waitingForPath = true;nextRepathTick = human.tickCount + 1;
                return;
            }
            if (sight == HumanLootVision.Sight.VISIBLE) {
                directWaterFallback = true;nextRepathTick = human.tickCount + 20;
                return;
            }
        }
        failTarget("没有完整、安全且命令允许的拾取路线", path);
    }

    private boolean usableLootPath(net.minecraft.world.level.pathfinder.Path path) {
        if (path == null || !path.canReach() || path.isDone() || !HumanTerrainSafety.safePath(human, path)) return false;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (!SoldierOrder.allowsLootPosition(human, node.x + .5D, node.z + .5D)) return false;
        }
        return true;
    }

    private void failTarget(String reason, net.minecraft.world.level.pathfinder.Path path) {
        if (targetItem == null || knownItemPosition == null) return;
        FailedItem old = failedItems.get(targetItem.getUUID());
        int attempts = old == null || old.position.distanceToSqr(knownItemPosition) > 4
                || old.origin.distanceToSqr(human.position()) > 16 ? 1 : Math.min(4, old.attempts + 1);
        Vec3 probe = path != null && path.getEndNode() != null
                ? new Vec3(path.getEndNode().x + .5, path.getEndNode().y, path.getEndNode().z + .5) : knownItemPosition;
        FailedItem failed = new FailedItem(knownItemPosition, human.position(), probe, human.tickCount, attempts,
                sceneHash(knownItemPosition, human.position(), probe));
        failedItems.remove(targetItem.getUUID());
        while (failedItems.size() >= MAX_SCREENED_ITEMS) failedItems.remove(failedItems.keySet().iterator().next());
        failedItems.put(targetItem.getUUID(), failed);
        HumanPerception.decision(human,"暂缓不可达物品",reason+"；保留位置失败记录，不反复顶墙");
        NavigationDebugService.candidate(human, knownItemPosition,"loot", "unreachable",Double.NaN);
        advanceTarget();
    }

    private boolean isFailed(ItemEntity item) {
        FailedItem failed = failedItems.get(item.getUUID());
        if (failed == null) for (FailedItem other : failedItems.values()) {
            if (other.position.distanceToSqr(item.position()) < 1) { failed = other; break; }
        }
        if (failed == null) return false;
        if (failed.position.distanceToSqr(item.position()) > 4 || failed.origin.distanceToSqr(human.position()) > 16) return false;
        int elapsed = human.tickCount - failed.tick;
        int cooldown = dev.felix.hostilehumans.core.LootRetryPolicy.cooldown(failed.attempts);
        if (!dev.felix.hostilehumans.core.LootRetryPolicy.blocked(elapsed, failed.attempts, false)) return false;
        if (elapsed < cooldown) return true;
        final FailedItem snapshot = failed;
        return !failureContextChecks.computeIfAbsent(failed,
                key -> sceneHash(snapshot.position, snapshot.origin, snapshot.probe) != snapshot.scene);
    }

    private int sceneHash(Vec3 position, Vec3 origin, Vec3 probe) {
        int hash = 1;
        for (Vec3 point : new Vec3[]{position, origin, probe}) {
            var center = net.minecraft.core.BlockPos.containing(point);
            for (var cell : new net.minecraft.core.BlockPos[]{center,center.above(),center.below(),center.north(),center.south(),center.east(),center.west()}) {
                hash = 31 * hash + (human.level().hasChunkAt(cell) ? human.level().getBlockState(cell).hashCode() : 0);
            }
        }
        return hash;
    }

    private record FailedItem(Vec3 position, Vec3 origin, Vec3 probe, int tick, int attempts, int scene) {}

    private boolean isScreened(ItemEntity item) {
        ScreenedItem cached = screenedItems.get(item.getUUID());
        if (cached == null) return false;
        if (cached.expiresAt <= human.tickCount
                || (cached.inventorySensitive && cached.inventoryRevision != inventoryRevision())
                || cached.position.distanceToSqr(item.position()) > 4.0D
                || cached.stack.getCount() != item.getItem().getCount()
                || !ItemStack.isSameItemSameTags(cached.stack, item.getItem())) {
            screenedItems.remove(item.getUUID());
            return false;
        }
        return true;
    }

    private void rememberScreened(ItemEntity item, int cooldownTicks) {
        if (!item.isAlive() || item.getItem().isEmpty()) return;
        screenedItems.remove(item.getUUID());
        if (screenedItems.size() >= MAX_SCREENED_ITEMS) {
            Iterator<UUID> oldest = screenedItems.keySet().iterator();
            if (oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
        screenedItems.put(item.getUUID(), new ScreenedItem(item.getItem().copy(),
                item.position(), human.tickCount + cooldownTicks, inventoryRevision(),
                cooldownTicks == SCREENED_COOLDOWN_TICKS));
    }

    private int inventoryRevision() {
        return human.getData() == null ? 0 : human.getData().getInventoryRevision();
    }

    private void refreshScreeningEquipment() {
        // Once per selection batch, not once per ground item. Copies are made
        // only on change; direct durability/enchantment edits are detected too.
        HumanData currentInventory = human.getData();
        boolean changed = screenedInventory != currentInventory;
        screenedInventory = currentInventory;
        for (int i = 0; i < EQUIPMENT_SLOTS.length; i++) {
            ItemStack current = human.getItemBySlot(EQUIPMENT_SLOTS[i]);
            if (!sameScreeningEquipment(screenedEquipment[i], current)) {
                screenedEquipment[i] = current.copy();
                changed = true;
            }
        }
        if (changed) {
            screenedItems.entrySet().removeIf(entry -> entry.getValue().inventorySensitive);
            pendingSearch = null;
        }
    }

    static boolean sameScreeningEquipment(ItemStack previous, ItemStack current) {
        return previous != null && previous.getCount() == current.getCount()
                && ItemStack.isSameItemSameTags(previous, current);
    }

    private record ScreenedItem(ItemStack stack, Vec3 position, int expiresAt,
                                int inventoryRevision, boolean inventorySensitive) {}
}
