package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.ActionCoordinator;

import dev.felix.hostilehumans.behavior.HumanGoalExecutionBridge;
import dev.felix.hostilehumans.inventory.HumanInventorySession;

import net.minecraft.world.entity.ai.control.JumpControl;

/** Landing and original-source validation both precede consumption of a delayed voluntary jump. */
public final class HumanSafeJumpControl extends JumpControl {
    private final Human human;
    private boolean pending, environmental;
    private ActionCoordinator.Token base, modifier;
    public HumanSafeJumpControl(Human human) { super(human); this.human = human; }
    @Override public void jump() {
        if (!available() || !HumanTerrainSafety.safeCombatJump(human)) return;
        ActionCoordinator.Token requestedModifier = HumanMovementModifiers.currentToken(human);
        ActionCoordinator.Token requestedBase = HumanNavigationOwnership.currentToken(human);
        if (requestedBase == null) requestedBase = HumanMovementModifiers.currentBase(human);
        boolean water = human.isInWater() || human.isInLava();
        if (HumanGoalExecutionBridge.bound(human) && !water
                && !HumanGoalExecutionBridge.ownsBaseMove(human, requestedBase)) return;
        if (requestedModifier != null && !HumanMovementModifiers.modifierValid(human, requestedModifier)) return;
        base = requestedBase; modifier = requestedModifier; environmental = water; pending = true;
    }
    public void cancelPending() { pending = environmental = false; base = modifier = null; }
    @Override public void tick() {
        if (pending && available()
                && (!HumanGoalExecutionBridge.bound(human) || environmental && (human.isInWater() || human.isInLava())
                    || HumanGoalExecutionBridge.ownsBaseMove(human, base))
                && (modifier == null || HumanMovementModifiers.modifierValid(human, modifier))
                && HumanTerrainSafety.safeCombatJump(human)) super.jump();
        cancelPending();
        super.tick();
    }
    private boolean available() {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !human.isSleeping() && !human.isPassenger() && !HumanInventorySession.isEditing(human);
    }
}
