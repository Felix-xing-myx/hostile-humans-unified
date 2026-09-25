package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.goal.Goal;

/** Uses the same finite recovery inventory while the human is out of combat. */
public final class IdleRecoveryGoal extends Goal {
    private static final int BATCH_PAUSE_TICKS = 36;
    private final Human human;
    private final CombatAiConfig config;
    private RecoverySupplies.UseSession session;
    private int nextUseTick;
    private int mealsInBatch;

    public IdleRecoveryGoal(Human human) {
        this.human = human;
        this.config = CombatAiConfig.get();
    }

    @Override
    public boolean canUse() {
        return config.enabled()
                && config.itemRecoveryEnabled()
                && human.isAlive()
                && human.getTarget() == null
                && outOfCombatLongEnough()
                && needsMoreRecovery()
                && RecoverySupplies.hasUsableSupply(human);
    }

    @Override
    public boolean canContinueToUse() {
        return human.isAlive()
                && (session != null || (human.getTarget() == null
                && outOfCombatLongEnough()
                && needsMoreRecovery()
                && RecoverySupplies.hasUsableSupply(human)));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        nextUseTick = human.tickCount;
        mealsInBatch = 0;
        MovementSpeedController.normal(human);
        human.getPersistentData().putString("humangunner:ai_phase", "idle_recovery");
    }

    @Override
    public void stop() {
        if (session != null) {
            boolean completed = RecoverySupplies.completed(human, session);
            RecoverySupplies.finishUse(human, session, completed);
            session = null;
        }
        mealsInBatch = 0;
        human.getPersistentData().putString("humangunner:ai_phase", "idle");
        if (human.getTarget() == null) {
            MovementSpeedController.normal(human);
        } else {
            MovementSpeedController.combat(human, false);
        }
    }

    @Override
    public void tick() {
        if (session == null) {
            if (human.tickCount < nextUseTick || !needsMoreRecovery()) {
                return;
            }
            session = RecoverySupplies.beginIdleUse(human);
            return;
        }
        if (human.isUsingItem()) {
            return;
        }
        boolean completed = RecoverySupplies.completed(human, session);
        if (completed && session.food() && mealsInBatch + 1 < RecoverySupplies.MEALS_PER_BATCH) {
            mealsInBatch++;
            RecoverySupplies.UseSession continued = RecoverySupplies.continueFoodUse(
                    human, session, 1.0D
            );
            if (continued != null) {
                session = continued;
                return;
            }
        } else if (completed && session.food()) {
            mealsInBatch++;
        }
        RecoverySupplies.finishUse(human, session, completed);
        session = null;
        boolean batchLimitReached = completed && mealsInBatch >= RecoverySupplies.MEALS_PER_BATCH;
        nextUseTick = human.tickCount + (batchLimitReached && needsMoreRecovery()
                ? BATCH_PAUSE_TICKS : completed ? 6 : 20);
        mealsInBatch = 0;
    }

    private boolean needsMoreRecovery() {
        return RecoverySupplies.projectedHealth(human) / Math.max(1.0F, human.getMaxHealth()) < 0.99D;
    }

    private boolean outOfCombatLongEnough() {
        return human.getLastHurtByMob() == null
                || human.tickCount - human.getLastHurtByMobTimestamp() > 100;
    }
}
