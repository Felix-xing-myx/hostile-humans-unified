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
    private static final int FAILED_TARGET_COOLDOWN_TICKS = 200;
    private static final int STALLED_TARGET_TICKS = 60;
    private final Human human;
    private final Map<UUID, ScreenedItem> screenedItems = new LinkedHashMap<>();
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
    private int lastProgressTick;
    private double bestTargetDistance;

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
        return targetItem != null;
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
                && SoldierOrder.allowsLootPosition(human, targetItem.getX(), targetItem.getZ())
                && human.distanceToSqr(targetItem) <= 256.0D;
    }

    @Override
    public void start() {
        human.setActivelyCollectingLoot(true);
        beginTarget();
    }

    @Override
    public void tick() {
        if (!canContinueToUse()) {
            stop();
            return;
        }
        human.setPursuingWaterLoot(human.isInWater() || targetItem.isInWater());
        if (human.tickCount >= nextValidationTick) {
            nextValidationTick = human.tickCount + 20;
            if (!HumanLootManager.isWorthCollecting(human, targetItem)) {
                rememberScreened(targetItem, SCREENED_COOLDOWN_TICKS);
                advanceTarget();
                return;
            }
        }
        human.getLookControl().setLookAt(targetItem, 25.0F, 25.0F);
        double distance = human.distanceTo(targetItem);
        if (distance <= 1.5D) {
            if (!HumanLootManager.collect(human, targetItem)) {
                rememberScreened(targetItem, FAILED_TARGET_COOLDOWN_TICKS);
            }
            advanceTarget();
            return;
        }
        if (distance < bestTargetDistance - 0.35D) {
            bestTargetDistance = distance;
            lastProgressTick = human.tickCount;
        } else if (human.tickCount - lastProgressTick >= STALLED_TARGET_TICKS) {
            rememberScreened(targetItem, FAILED_TARGET_COOLDOWN_TICKS);
            advanceTarget();
            return;
        }
        if (targetItem.isInWater() && human.shouldUseWaterMovement()
                && human.tickCount - lastProgressTick >= 20
                && !human.getNavigation().isDone()) {
            // A live but stalled water path can point at a stale water node.
            // Briefly let the existing direct-water pickup steering take over.
            human.getNavigation().stop();
            nextRepathTick = human.tickCount + 20;
        }
        if (human.tickCount >= nextRepathTick
                && (human.getNavigation().isDone() || human.getNavigation().isStuck()
                || lastPathItemPosition == null
                || lastPathItemPosition.distanceToSqr(targetItem.position()) > 4.0D)) {
            moveToItem();
        }
        human.approachWaterLoot(targetItem, 0.9D);
        if (human.horizontalCollision) {
            NavigationSupport.openBlockingPassage(human);
        }
    }

    @Override
    public void stop() {
        targetItem = null;
        // A normal target completion can leave an unfinished decision. It
        // owns no navigation or water movement; combat interrupts discard it.
        if (pendingSearch == null && !selectionDeferred) pileCenter = null;
        human.setActivelyCollectingLoot(false);
        human.setPursuingWaterLoot(false);
        if (ownsPath(human.getNavigation().getPath(), ownedPath)
                || MovementContinuity.ownsRoute(human, ownedPath)) human.getNavigation().stop();
        ownedPath = null;
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
        screenedItems.entrySet().removeIf(entry -> entry.getValue().expiresAt <= human.tickCount);
        if (pendingSearch == null || !pendingSearch.isCurrent(human)) {
            pendingSearch = new HumanLootManager.NearbySearch(human, 12.0D, pileCenter);
        }
        ItemEntity chosen = pendingSearch.advance(64,
                item -> isScreened(item) || !SoldierOrder.allowsLootPosition(human, item.getX(), item.getZ()),
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
            pileCenter = chosen.position();
        }
        return chosen;
    }

    static boolean ownsPath(Object current, Object assigned) {
        return assigned != null && current == assigned;
    }

    private void beginTarget() {
        if (targetItem == null) return;
        bestTargetDistance = human.distanceTo(targetItem);
        lastProgressTick = human.tickCount;
        nextValidationTick = human.tickCount + 20;
        nextRepathTick = 0;
        lastPathItemPosition = null;
        human.setPursuingWaterLoot(human.isInWater() || targetItem.isInWater());
        moveToItem();
    }

    private void advanceTarget() {
        targetItem = chooseTarget();
        if (targetItem != null) {
            beginTarget();
        } else {
            human.setPursuingWaterLoot(false);
            human.getNavigation().stop();
        }
    }

    private void moveToItem() {
        if (targetItem != null) {
            net.minecraft.world.level.pathfinder.Path path = human.getNavigation().createPath(targetItem, 0);
            if (path != null) {
                for (int i = 0; i < path.getNodeCount(); i++) {
                    var node = path.getNode(i);
                    if (!SoldierOrder.allowsLootPosition(human, node.x + 0.5D, node.z + 0.5D)) {
                        rememberScreened(targetItem, FAILED_TARGET_COOLDOWN_TICKS);
                        targetItem = null;
                        human.getNavigation().stop();
                        return;
                    }
                }
            }
            if (human.getNavigation().moveTo(path, 0.9D)) ownedPath = human.getNavigation().getPath();
            lastPathItemPosition = targetItem.position();
            nextRepathTick = human.tickCount + 20;
        }
    }

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
