package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.navigation.SoldierFollowNavigation;

import java.util.EnumSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/** Temporary reinforcements follow through the same MOVE arbitration as hired soldiers. */
public final class TemporarySupportFollowGoal extends Goal {
    private final Human human;
    public TemporarySupportFollowGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    private ServerPlayer owner() {
        if (!human.getPersistentData().hasUUID(HumanRelations.TEMP_OWNER)) return null;
        var player = human.level().getPlayerByUUID(human.getPersistentData().getUUID(HumanRelations.TEMP_OWNER));
        return player instanceof ServerPlayer owner && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }
    @Override public boolean canUse() {
        return !human.hasOwner() && owner() != null && human.getTarget() == null
                && !human.isFleeing && !human.isInLava() && !human.isSeekingShore()
                && !human.isNoAi() && !human.isSleeping() && !human.isPassenger()
                && !HumanRuntime.isManualInventoryOpen(human) && !human.isActivelyCollectingLoot();
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() {
        ServerPlayer owner = owner();
        if (owner != null) SoldierFollowNavigation.tick(human, owner);
    }
    @Override public void stop() { SoldierFollowNavigation.stopOwned(human); }
}
