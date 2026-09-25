package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.EnumSet;

/** Collects worthwhile loot while idle without interrupting combat behavior. */
final class ValuableItemPickupGoal extends Goal {
    private final Human human;
    private ItemEntity targetItem;
    private int nextSearchTick;
    private int nextRepathTick;

    ValuableItemPickupGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!human.isAlive()
                || human.getTarget() != null
                || human.isFleeing
                || human.isUsingItem()
                || !SoldierPickupPolicy.isEnabled(human)
                || SoldierOrder.isHoldingPosition(human)
                || human.tickCount < nextSearchTick) {
            return false;
        }
        nextSearchTick = human.tickCount + 20 + human.getRandom().nextInt(20);
        targetItem = HumanLootManager.findBestNearby(human, 12.0D);
        return targetItem != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetItem != null
                && targetItem.isAlive()
                && !targetItem.getItem().isEmpty()
                && human.getTarget() == null
                && !human.isFleeing
                && SoldierPickupPolicy.isEnabled(human)
                && !SoldierOrder.isHoldingPosition(human)
                && human.distanceToSqr(targetItem) <= 256.0D;
    }

    @Override
    public void start() {
        nextRepathTick = 0;
        moveToItem();
    }

    @Override
    public void tick() {
        if (targetItem == null) {
            return;
        }
        human.getLookControl().setLookAt(targetItem, 25.0F, 25.0F);
        if (human.distanceToSqr(targetItem) <= 2.25D) {
            HumanLootManager.collect(human, targetItem);
            targetItem = null;
            return;
        }
        if (human.tickCount >= nextRepathTick || human.getNavigation().isDone()) {
            moveToItem();
        }
        if (human.horizontalCollision) {
            NavigationSupport.openBlockingPassage(human);
        }
    }

    @Override
    public void stop() {
        targetItem = null;
        human.getNavigation().stop();
    }

    private void moveToItem() {
        if (targetItem != null) {
            human.getNavigation().moveTo(targetItem, 0.9D);
            nextRepathTick = human.tickCount + 16;
        }
    }
}
