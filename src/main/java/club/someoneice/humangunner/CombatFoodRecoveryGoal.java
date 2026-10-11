package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Handles bounded combat-food batches and urgent instant healing without claiming
 * movement or look flags. Only urgent healing preempts a meal's item custody.
 */
final class CombatFoodRecoveryGoal extends Goal {
    private static final double START_HEALTH_RATIO = 2.0D / 3.0D;
    private static final double FULL_HEALTH_RATIO = 1.0D;
    private static final int BATCH_PAUSE_TICKS = 36;
    private static final String ACTIVE_KEY = HumanGunner.MOD_ID + ":combat_food_active";

    private final Human human;
    private final CombatAiConfig config;
    private RecoverySupplies.UseSession session;
    private int nextUseTick;
    private int mealsInBatch;

    CombatFoodRecoveryGoal(Human human) {
        this.human = human;
        this.config = CombatAiConfig.forHuman(human);
        human.getPersistentData().remove(ACTIVE_KEY);
    }

    @Override
    public boolean canUse() {
        if (human.humanGunner$shieldDefense().shouldSuppressRecovery()) return false;
        boolean urgent = RecoverySupplies.hasUrgentInstantHealing(human);
        return config.enabled()
                && config.itemRecoveryEnabled()
                && human.isAlive()
                && (urgent || (human.getTarget() != null && mayStartFood()))
                && (urgent ? !human.isUsingItem() : RecoverySupplies.canStartCombatFood(human))
                && !RecoverySupplies.hasActiveUse(human)
                && (urgent || (human.tickCount >= nextUseTick && needsCombatFood()
                && RecoverySupplies.hasOrdinaryFood(human)));
    }

    @Override
    public boolean canContinueToUse() {
        if (human.humanGunner$shieldDefense().shouldSuppressRecovery()) return false;
        return human.isAlive()
                && (session != null
                || RecoverySupplies.hasUrgentInstantHealing(human)
                || (human.getTarget() != null
                && mayStartFood()
                && needsCombatFood()
                && RecoverySupplies.hasOrdinaryFood(human)));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mealsInBatch = 0;
        human.getPersistentData().putBoolean(ACTIVE_KEY, true);
        human.getPersistentData().putString("hostile_humans_unified:ai_phase", "combat_eating");
    }

    @Override
    public void stop() {
        finishSession();
        human.getPersistentData().remove(ACTIVE_KEY);
    }

    @Override
    public void tick() {
        if (human.humanGunner$shieldDefense().shouldSuppressRecovery()) { stop(); return; }
        boolean urgent = RecoverySupplies.hasUrgentInstantHealing(human);
        if (session != null && session.food() && urgent) {
            boolean completed = RecoverySupplies.completed(human, session);
            RecoverySupplies.finishUse(human, session, completed);
            session = null;
            mealsInBatch = 0;
            nextUseTick = human.tickCount;
            return;
        }
        if (session == null) {
            if ((!urgent && (human.tickCount < nextUseTick || !mayStartFood() || !needsCombatFood()))
                    || RecoverySupplies.hasActiveUse(human)) {
                return;
            }
            session = urgent ? RecoverySupplies.beginUrgentInstantHealing(human)
                    : RecoverySupplies.beginCombatFoodUse(human);
            if (session == null) {
                nextUseTick = human.tickCount + 20;
            }
            return;
        }
        if (RecoverySupplies.isUseInProgress(human, session)) {
            return;
        }
        boolean completed = RecoverySupplies.completed(human, session);
        if (completed && session.food() && mealsInBatch + 1 < RecoverySupplies.MEALS_PER_BATCH) {
            mealsInBatch++;
            RecoverySupplies.UseSession continued = RecoverySupplies.continueFoodUse(
                    human, session, FULL_HEALTH_RATIO
            );
            if (continued != null) {
                session = continued;
                nextUseTick = human.tickCount;
                return;
            }
        } else if (completed && session.food()) {
            mealsInBatch++;
        }
        RecoverySupplies.finishUse(human, session, completed);
        session = null;
        boolean batchLimitReached = completed && mealsInBatch >= RecoverySupplies.MEALS_PER_BATCH;
        boolean stillNeedsFood = needsCombatFood();
        if (batchLimitReached || !stillNeedsFood || !completed) {
            mealsInBatch = 0;
        }
        nextUseTick = human.tickCount + (batchLimitReached && stillNeedsFood
                ? BATCH_PAUSE_TICKS : completed ? 8 : 20);
    }

    private boolean needsCombatFood() {
        double maximum = Math.max(1.0F, human.getMaxHealth());
        return RecoverySupplies.shouldContinueCombatEating(
                RecoverySupplies.projectedHealth(human) / maximum,
                START_HEALTH_RATIO,
                RecoverySupplies.hasOrdinaryFood(human)
        );
    }

    private boolean mayStartFood() {
        return !human.isFleeing || RetreatRecoveryPolicy.canStart(human, human.getTarget());
    }

    private void finishSession() {
        if (session == null) {
            return;
        }
        boolean completed = RecoverySupplies.completed(human, session);
        RecoverySupplies.finishUse(human, session, completed);
        session = null;
    }

    static boolean isActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE_KEY);
    }
}
