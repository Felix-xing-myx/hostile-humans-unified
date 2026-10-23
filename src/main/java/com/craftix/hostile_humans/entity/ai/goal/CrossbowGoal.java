package com.craftix.hostile_humans.entity.ai.goal;

import club.someoneice.humangunner.HumanAmmo;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.RangedFiringPosition;
import club.someoneice.humangunner.RangedWeaponCustody;
import club.someoneice.humangunner.RangedStrafeSpeed;
import club.someoneice.humangunner.SoldierOrder;
import club.someoneice.humangunner.ShoreSeekingPolicy;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class CrossbowGoal<T extends HumanEntity & CrossbowAttackMob>
extends Goal {
    public static final UniformInt PATHFINDING_DELAY_RANGE = TimeUtil.rangeOfSeconds((int)1, (int)2);
    private static final int SHOT_DELAY_MIN = 72;
    private static final int SHOT_DELAY_VARIANCE = 32;
    private final T mob;
    private final double speedModifier;
    private final float attackRadiusSqr;
    private CrossbowState crossbowState = CrossbowState.UNCHARGED;
    private int seeTime;
    private int attackDelay;
    private final CrossbowMovementExecution<T> movement;

    public CrossbowGoal(T p_25814_, double p_25815_, float p_25816_) {
        this.mob = p_25814_;
        this.speedModifier = p_25815_;
        this.movement = new CrossbowMovementExecution<>(mob, speedModifier);
        this.attackRadiusSqr = p_25816_ * p_25816_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private double attackRadiusSqr() {
        return mob instanceof Human human ? club.someoneice.humangunner.RangedCombatProfile
                .of(human, human.getMainHandItem()).maximumSqr() : attackRadiusSqr;
    }
    private double minimumBand() {
        return mob instanceof Human human ? club.someoneice.humangunner.RangedCombatProfile
                .of(human, human.getMainHandItem()).minimumBand() : 16;
    }
    private double maximumBand() {
        return mob instanceof Human human ? club.someoneice.humangunner.RangedCombatProfile
                .of(human, human.getMainHandItem()).maximumBand() : 24;
    }

    public boolean canUse() {
        return this.isValidTarget() && this.isHoldingCrossbow();
    }

    private boolean isHoldingCrossbow() {
        if (this.mob instanceof Human human
                && (!HumanAmmo.hasArrows(human) || HumanAmmo.arrowCount(human) == 0 || RangedWeaponCustody.isActive(human)
                || HumanUtil.isMeleeWeapon(human.getMainHandItem()))) {
            return false;
        }
        return mob instanceof Human human ? human.getMainHandItem().getItem() instanceof CrossbowItem
                : this.mob.isHolding(is -> is.getItem() instanceof CrossbowItem);
    }

    public boolean canContinueToUse() {
        return this.canUse();
    }

    private boolean isValidTarget() {
        return this.mob.getTarget() != null && this.mob.getTarget().isAlive();
    }

    public void stop() {
        boolean isSit;
        super.stop();
        if (this.mob instanceof Human human) RangedFiringPosition.cancelVisibleSearch(human);
        if (this.mob instanceof Human human && human.isCombatFiringShore()) {
            human.stopSeekingShore();
        }
        movement.setOrbitSpeed(false);
        this.mob.setAggressive(false);
        this.seeTime = 0;
        movement.reset();
        this.crossbowState = CrossbowState.UNCHARGED;
        this.attackDelay = 0;
        ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
        if (this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof CrossbowItem) {
            ItemStack used = this.mob.getUseItem();
            if (this.mob instanceof Human human) human.humanGunner$shieldDefense().pauseRangedUseForGuard();
            this.mob.stopUsingItem();
            ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
            if (used.getItem() instanceof CrossbowItem) CrossbowItem.setCharged(used, false);
            this.crossbowState = CrossbowState.UNCHARGED;
            this.attackDelay = 0;
        }
        if (isSit = this.mob.isOrderedToSit()) {
            ((HumanMobEntityData)this.mob).setOrderedToPosition((BlockPos)this.mob.getEntityData().get(HumanMobEntityData.DATA_SIT_POS));
        } else if (this.mob.getTarget() == null || !this.mob.getTarget().isAlive()) {
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().setWantedPosition(
                    this.mob.position().x(), this.mob.position().y(), this.mob.position().z(), 0.0D
            );
        }
        if (this.mob instanceof Human human) {
            human.clearRangedStrafeMotion();
        } else {
            movement.strafe(0.0F, 0.0F);
        }
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        super.start();
        movement.start();
        ItemStack held = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(
                this.mob, item -> item instanceof CrossbowItem));
        this.crossbowState = CrossbowItem.isCharged(held)
                ? (mob instanceof Human ? CrossbowState.CHARGED : CrossbowState.READY_TO_ATTACK)
                : CrossbowState.UNCHARGED;
        if (mob instanceof Human human && CrossbowItem.isCharged(held))
            club.someoneice.humangunner.HumanArrowCombat.crossbowLoaded(human, held);
        this.attackDelay = 0;
    }

    public void tick() {
        if (!this.canContinueToUse()) {
            this.stop();
            return;
        }
        if (mob instanceof Human) {
            ItemStack active = mob.isUsingItem() ? mob.getUseItem() : mob.getMainHandItem();
            if (!(active.getItem() instanceof CrossbowItem)) active = mob.getOffhandItem();
            boolean heavy = club.someoneice.humangunner.SpartanRangedCompat.isHeavyCrossbow(active);
            if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof CrossbowItem)
                mob.advanceUseTime(heavy ? 1 : 3);
        }
        LivingEntity livingentity = this.mob.getTarget();
        if (livingentity != null) {
            if (this.mob instanceof Human human) {
                human.updateCombatFiringShore(livingentity, this.speedModifier);
            }
            boolean flag1;
            boolean flag = (this.mob instanceof Human shotHuman ? club.someoneice.humangunner.RangedShotClearance.canShoot(shotHuman, livingentity) : this.mob.getSensing().hasLineOfSight((Entity)livingentity));
            boolean bl = flag1 = this.seeTime > 0;
            if (flag != flag1) {
                this.seeTime = 0;
            }
            this.seeTime = flag ? ++this.seeTime : --this.seeTime;
            if (mob instanceof Human human) club.someoneice.humangunner.RangedTerrainCombat.observe(human, livingentity, flag);
            double d0 = this.mob.distanceToSqr((Entity)livingentity);
            boolean shoreSeeking = this.mob instanceof Human human
                    && ShoreSeekingPolicy.isShoreTransitionActive(
                            human.isSeekingShore(), human.isShoreTransitionPending());
            if (shoreSeeking) {
                movement.setOrbitSpeed(false);
                // Continue charging/firing while the shore goal owns navigation.
                this.mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
                tickShot(livingentity, flag,
                        d0 <= (double)attackRadiusSqr() && this.seeTime >= 5);
                return;
            }
            if (this.mob instanceof Human human && human.isFleeing) {
                movement.setOrbitSpeed(false);
                if (this.mob.isUsingItem()
                        && !(this.mob.getUseItem().getItem() instanceof CrossbowItem)) return;
                this.mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
                tickShot(livingentity, flag, d0 <= attackRadiusSqr() && this.seeTime >= 5);
                return;
            }
            var result = movement.tick(livingentity, d0, flag,
                    new CrossbowMovementExecution.Request(attackRadiusSqr(), minimumBand(), maximumBand(),
                            mob instanceof Human human && club.someoneice.humangunner.RangedCombatProfile
                                    .of(human, human.getMainHandItem()).advance(), canRun(), seeTime));
            tickShot(livingentity, result.visible(), result.canEngage());
        } else {
            movement.setOrbitSpeed(false);
        }
    }

    @Override
    public EnumSet<Goal.Flag> getFlags() {
        if (this.mob instanceof Human human) {
            if (human.isFleeing) {
                return EnumSet.noneOf(Goal.Flag.class);
            }
            if (ShoreSeekingPolicy.isShoreTransitionActive(
                    human.isSeekingShore(), human.isShoreTransitionPending())) {
                return human.isCombatFiringShore()
                        ? EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK)
                        : EnumSet.of(Goal.Flag.LOOK);
            }
        }
        return EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK);
    }

    private void tickShot(LivingEntity livingentity, boolean visible, boolean canEngage) {
        if (mob instanceof Human human && RangedWeaponCustody.isActive(human)) return;
        // A shield (or recovery item) owns active use. Background loading belongs
        // to the shield controller; never publish a failed CHARGING transition.
        if (mob instanceof Human human && human.humanGunner$shieldDefense().reservesRangedUse()) return;
        if (mob.isUsingItem() && !(mob.getUseItem().getItem() instanceof CrossbowItem)) return;
        ItemStack heldCrossbow = mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(
                mob, item -> item instanceof CrossbowItem));
        if (CrossbowItem.isCharged(heldCrossbow)
                && (crossbowState == CrossbowState.UNCHARGED || crossbowState == CrossbowState.CHARGING)) {
            // Background loading may have completed without this goal stopping.
            // Adopt the loaded state without resampling its existing shot deadline.
            crossbowState = CrossbowState.CHARGED;
            ((CrossbowAttackMob)mob).setChargingCrossbow(false);
            if (mob instanceof Human human)
                club.someoneice.humangunner.HumanArrowCombat.crossbowLoaded(human, heldCrossbow);
        }
            if (this.mob instanceof Human human && !HumanAmmo.hasArrows(human)) {
                if (this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof CrossbowItem)
                    this.mob.stopUsingItem();
                ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
                this.crossbowState = CrossbowState.UNCHARGED;
                this.attackDelay = 0;
                return;
            }
            if (visible && (canEngage || this.crossbowState == CrossbowState.READY_TO_ATTACK)
                    && this.mob instanceof Human human) {
                human.markRangedFacing(livingentity);
            }
            if (this.crossbowState == CrossbowState.UNCHARGED) {
                if (canEngage) {
                    if (mob instanceof Human human)
                        club.someoneice.humangunner.HumanArrowCombat.beginCrossbowLoad(human,
                                mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(mob, item -> item instanceof CrossbowItem)));
                    this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof CrossbowItem));
                    if (!this.mob.isUsingItem() || this.mob.getUseItem() != heldCrossbow) return;
                    if (this.mob instanceof Human human) human.humanGunner$shieldDefense().restoreRangedUseAfterGuard();
                    this.crossbowState = CrossbowState.CHARGING;
                    ((CrossbowAttackMob)this.mob).setChargingCrossbow(true);
                }
            } else if (this.crossbowState == CrossbowState.CHARGING) {
                ItemStack itemstack;
                int i;
                if (!this.mob.isUsingItem()) {
                    this.crossbowState = CrossbowState.UNCHARGED;
                    ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
                    return;
                }
                itemstack = this.mob.getUseItem();
                if (!(itemstack.getItem() instanceof CrossbowItem)) {
                    this.crossbowState = CrossbowState.UNCHARGED;
                    ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
                    return;
                }
                if ((i = this.mob.getTicksUsingItem()) >= CrossbowItem.getChargeDuration(itemstack)) {
                    this.mob.releaseUsingItem();
                    this.crossbowState = CrossbowState.CHARGED;
                    if (mob instanceof Human && club.someoneice.humangunner.SpartanRangedCompat.isHeavyCrossbow(itemstack))
                        club.someoneice.humangunner.SpartanRangedCompat.markChargedForNpc(itemstack);
                    if (!CrossbowItem.isCharged(itemstack)) {
                        this.crossbowState = CrossbowState.UNCHARGED;
                        ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
                        return;
                    }
                    if (this.mob instanceof Human)
                        club.someoneice.humangunner.HumanArrowCombat.markNpcLoad(itemstack);
                    // Humans convert the existing accelerated loaded wait into a server-tick
                    // deadline shared with background loading. Goal restarts cannot skip it.
                    // Non-Human callers retain their original local delay.
                    if (!(mob instanceof Human)) this.attackDelay = SHOT_DELAY_MIN
                            + this.mob.getRandom().nextInt(SHOT_DELAY_VARIANCE);
                    if (this.mob instanceof Human human)
                        club.someoneice.humangunner.HumanArrowCombat.crossbowLoaded(human, itemstack);
                    ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
                    T t = this.mob;
                    if (t instanceof Human) {
                        Human human = (Human)t;
                        human.lastCombatTime = human.tickCount;
                    }
                }
            } else if (this.crossbowState == CrossbowState.CHARGED) {
                ItemStack held = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(
                        this.mob, item -> item instanceof CrossbowItem));
                if (mob instanceof Human human
                        ? club.someoneice.humangunner.HumanArrowCombat.crossbowWaitFinished(human, held)
                        : --this.attackDelay == 0) {
                    this.crossbowState = CrossbowState.READY_TO_ATTACK;
                }
            } else if (this.crossbowState == CrossbowState.READY_TO_ATTACK && visible
                    && (!(mob instanceof Human human) || club.someoneice.humangunner.HumanArrowCombat.canShoot(human, livingentity))) {
                ((RangedAttackMob)this.mob).performRangedAttack(livingentity, 1.0f);
                ItemStack itemstack1 = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof CrossbowItem));
                CrossbowItem.setCharged((ItemStack)itemstack1, (boolean)false);
                this.crossbowState = CrossbowState.UNCHARGED;
            }
    }

    private boolean canRun() {
        return this.crossbowState == CrossbowState.UNCHARGED;
    }

    static enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK;

    }
}

