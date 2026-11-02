package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.behavior.HumanGoalExecutionBridge;
import java.util.Map;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inert for every selector except the explicitly bound Human.goalSelector. */
@Mixin(value = GoalSelector.class, remap = false)
public abstract class HumanGoalSelectorExecutionMixin implements HumanGoalExecutionBridge.SelectorAccess {
    @Shadow @Final private Map<Goal.Flag, WrappedGoal> f_25344_;
    @Unique private HumanGoalExecutionBridge.State hostileHumans$state;
    @Override public HumanGoalExecutionBridge.State hostileHumans$executionState() { return hostileHumans$state; }
    @Override public void hostileHumans$executionState(HumanGoalExecutionBridge.State state) { hostileHumans$state = state; }
    @Override public Map<Goal.Flag, WrappedGoal> hostileHumans$lockedFlags() { return f_25344_; }

    @Inject(method = {"m_25373_()V", "m_186081_(Z)V"}, at = @At("HEAD"), require = 2, allow = 2)
    private void hostileHumans$validateExecutingGoals(CallbackInfo callback) {
        HumanGoalExecutionBridge.beforeSelector(hostileHumans$state, f_25344_);
    }
    @Redirect(method = "m_25373_()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;m_8056_()V"), require = 1, allow = 1)
    private void hostileHumans$admitSelectedGoal(WrappedGoal wrapped) {
        HumanGoalExecutionBridge.start(hostileHumans$state, wrapped, f_25344_);
    }
    @Redirect(method = "m_186081_(Z)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/goal/WrappedGoal;m_8037_()V"), require = 1, allow = 1)
    private void hostileHumans$tickAdmittedGoal(WrappedGoal wrapped) {
        HumanGoalExecutionBridge.tick(hostileHumans$state, wrapped, f_25344_);
    }
}
