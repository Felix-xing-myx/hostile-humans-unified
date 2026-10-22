package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.goal.Goal;

/** Out-of-combat entry to the same ordered real-supply executor. */
public final class IdleRecoveryGoal extends Goal {
    private final Human human;
    private RecoverySupplies.UseSession session;
    public IdleRecoveryGoal(Human human) { this.human = human; }
    @Override public boolean canUse() {
        var config = CombatAiConfig.forHuman(human);
        return config.enabled() && config.itemRecoveryEnabled() && human.isAlive() && human.getTarget() == null
                && !human.isFleeing && !human.humanGunner$shieldDefense().shouldSuppressRecovery()
                && !RecoverySupplies.hasActiveUse(human) && RecoverySupplies.canStartCombatFood(human)
                && RecoverySupplies.hasRecoveryChoice(human, true);
    }
    @Override public boolean canContinueToUse() { return session != null && human.isAlive(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { session = RecoverySupplies.beginIdleUse(human); }
    @Override public void tick() {
        if (session == null || RecoverySupplies.isUseInProgress(human, session)) return;
        stop();
    }
    @Override public void stop() {
        if (session != null) {
            RecoverySupplies.finishUse(human, session, RecoverySupplies.completed(human, session));
            session = null;
        }
    }
}
