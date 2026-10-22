package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** Uses finite buff, debuff and emergency supplies without stopping combat movement. */
public final class CombatConsumableGoal extends Goal {
    private enum Action {
        DRINK_BUFF,
        THROW_DEBUFF
    }

    private final Human human;
    private Action action;
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
            action = Action.DRINK_BUFF;
            return true;
        }

        if (PotionThrowing.inRange(human, target)
                && human.getSensing().hasLineOfSight(target)
                && RecoverySupplies.findSplashDebuffSlot(human) >= 0
                && noAllyNearTarget(target)
                && human.getRandom().nextFloat() < 0.55F) {
            action = Action.THROW_DEBUFF;
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
        if (action == Action.THROW_DEBUFF) {
            throwDebuffPotion();
            nextDecisionTick = human.tickCount + human.getRandom().nextInt(120, 201);
            return;
        }
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

    private void throwDebuffPotion() {
        LivingEntity target = human.getTarget();
        int slot = RecoverySupplies.findSplashDebuffSlot(human);
        if (target == null || slot < 0 || !PotionThrowing.inRange(human, target)
                || !human.getSensing().hasLineOfSight(target) || !noAllyNearTarget(target)) {
            return;
        }
        ItemStack potionStack = RecoverySupplies.takeOneFromInventory(human, slot);
        if (potionStack.isEmpty()) {
            return;
        }

        ThrownPotion projectile = new ThrownPotion(human.level(), human);
        projectile.setItem(potionStack);
        PotionThrowing.shoot(projectile, target);
        human.level().addFreshEntity(projectile);
        human.level().playSound(
                null, human.getX(), human.getY(), human.getZ(),
                SoundEvents.SPLASH_POTION_THROW, SoundSource.HOSTILE, 0.8F,
                0.9F + human.getRandom().nextFloat() * 0.2F
        );
        human.getPersistentData().putString("hostile_humans_unified:ai_phase", "combat_throwing_debuff");
    }

    private boolean noAllyNearTarget(LivingEntity target) {
        AABB dangerZone = target.getBoundingBox().inflate(3.0D);
        return human.level().getEntitiesOfClass(
                Human.class,
                dangerZone,
                other -> other != human && HumanRelations.allied(human, other)
        ).isEmpty();
    }
}
