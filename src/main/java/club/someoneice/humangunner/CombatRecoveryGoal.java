package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.goal.Goal;

/** Single recovery selector in combat; no movement/look/attack claims. */
final class CombatRecoveryGoal extends Goal {
    private static final String LEGACY_ACTIVE_KEY = HumanGunner.MOD_ID + ":combat_food_active";
    private final Human human;
    private RecoverySupplies.UseSession session;
    private int nextUseTick;
    CombatRecoveryGoal(Human human) { this.human = human; human.getPersistentData().remove(LEGACY_ACTIVE_KEY); }
    @Override public boolean canUse() {
        var config = CombatAiConfig.forHuman(human);
        return config.enabled() && config.itemRecoveryEnabled() && human.isAlive()
                && !human.humanGunner$shieldDefense().shouldSuppressRecovery()
                && human.tickCount >= nextUseTick && !RecoverySupplies.hasActiveUse(human)
                && RecoverySupplies.canStartCombatFood(human)
                && (human.getTarget() != null || RecoverySupplies.hasUrgentInstantHealing(human))
                && RecoverySupplies.hasRecoveryChoice(human, true);
    }
    @Override public boolean canContinueToUse() { return session != null && human.isAlive(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        session = RecoverySupplies.beginUse(human);
    }
    @Override public void tick() {
        if (session == null || RecoverySupplies.isUseInProgress(human, session)) return;
        stop();
    }
    @Override public void stop() {
        if (session != null) {
            RecoverySupplies.finishUse(human, session, RecoverySupplies.completed(human, session));
            session = null;
        }
        nextUseTick = human.tickCount + 1;
    }
}
