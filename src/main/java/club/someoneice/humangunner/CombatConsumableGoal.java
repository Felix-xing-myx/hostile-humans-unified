package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Buff drinking delegates to the common recovery selector and cooldown; debuffs run independently. */
public final class CombatConsumableGoal extends Goal {
    private final Human human;
    private RecoverySupplies.UseSession session;
    private int nextDecisionTick;

    public CombatConsumableGoal(Human human) {
        this.human = human;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = human.getTarget();
        if (!human.isAlive()
                || target == null
                || !target.isAlive()
                || human.isUsingItem()
                || RecoverySupplies.hasActiveUse(human)
                || human.humanGunner$shieldDefense().shouldSuppressRecovery()
                || human.tickCount < nextDecisionTick) {
            return false;
        }

        if (RecoverySupplies.hasRecoveryChoice(human, true)) return false;

        // Decisions are deliberately sparse: supplies are finite and drinking
        // should create an occasional tactical window, not suppress attacks.
        nextDecisionTick = human.tickCount + human.getRandom().nextInt(50, 101);
        if (RecoverySupplies.hasUsefulDrinkableBuff(human)) {
            return true;
        }

        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return session != null && human.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        session = RecoverySupplies.beginCombatBuffUse(human);
        if (session == null) {
            nextDecisionTick = human.tickCount + 40;
        } else {
            human.getPersistentData().putString(
                    "hostile_humans_unified:ai_phase",
                    "combat_drinking_buff"
            );
        }
    }

    @Override
    public void tick() {
        if (session == null || RecoverySupplies.isUseInProgress(human, session)) {
            return;
        }
        finishSession();
    }

    @Override
    public void stop() {
        if (session != null) {
            finishSession();
        }
    }

    private void finishSession() {
        boolean completed = RecoverySupplies.completed(human, session);
        RecoverySupplies.finishUse(human, session, completed);
        session = null;
        nextDecisionTick = human.tickCount + (completed ? 100 : 40);
    }

}
