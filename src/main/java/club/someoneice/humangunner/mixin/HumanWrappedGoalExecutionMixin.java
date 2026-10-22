package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanGoalExecutionBridge;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps native Goal identity and native running transitions; only delegate side effects are scoped. */
@Mixin(value = WrappedGoal.class, remap = false)
public abstract class HumanWrappedGoalExecutionMixin implements HumanGoalExecutionBridge.WrappedAccess {
    @Unique private HumanGoalExecutionBridge.Execution hostileHumans$execution;
    @Override public HumanGoalExecutionBridge.Execution hostileHumans$execution() { return hostileHumans$execution; }
    @Override public void hostileHumans$execution(HumanGoalExecutionBridge.Execution execution) { hostileHumans$execution = execution; }
    @Redirect(method = "m_8056_()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/goal/Goal;m_8056_()V"), require = 1, allow = 1)
    private void hostileHumans$startActualDelegate(Goal delegate) {
        HumanGoalExecutionBridge.startDelegate((WrappedGoal) (Object) this, delegate::start);
    }
    @Redirect(method = "m_8037_()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/goal/Goal;m_8037_()V"), require = 1, allow = 1)
    private void hostileHumans$tickActualDelegate(Goal delegate) {
        HumanGoalExecutionBridge.tickDelegate((WrappedGoal) (Object) this, delegate::tick);
    }
    @Redirect(method = "m_8041_()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/goal/Goal;m_8041_()V"), require = 1, allow = 1)
    private void hostileHumans$stopActualDelegate(Goal delegate) {
        HumanGoalExecutionBridge.stopDelegate((WrappedGoal) (Object) this, delegate::stop);
    }
}
