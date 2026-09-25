package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.SoldierOrder;
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
import net.minecraft.world.phys.Vec3;

public class CrossbowGoal<T extends HumanEntity & CrossbowAttackMob>
extends Goal {
    public static final UniformInt PATHFINDING_DELAY_RANGE = TimeUtil.rangeOfSeconds((int)1, (int)2);
    private final T mob;
    private final double speedModifier;
    private final float attackRadiusSqr;
    private CrossbowState crossbowState = CrossbowState.UNCHARGED;
    private int seeTime;
    private int attackDelay;
    private int updatePathDelay;
    private boolean pursuingTarget;
    private boolean strafingClockwise;
    private int strafingTime;
    // Sixteen blocks is the danger boundary; keep backing off through a
    // four-block buffer so a single step toward the enemy does not re-enter it.
    private static final double RETREAT_DISTANCE_SQR = 20.0D * 20.0D;
    private static final double HOLD_DISTANCE_SQR = 24.0D * 24.0D;

    public CrossbowGoal(T p_25814_, double p_25815_, float p_25816_) {
        this.mob = p_25814_;
        this.speedModifier = p_25815_;
        this.attackRadiusSqr = p_25816_ * p_25816_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public boolean canUse() {
        return this.isValidTarget() && this.isHoldingCrossbow();
    }

    private boolean isHoldingCrossbow() {
        return this.mob.isHolding(is -> is.getItem() instanceof CrossbowItem);
    }

    public boolean canContinueToUse() {
        return this.isValidTarget() && (this.canUse() || !this.mob.getNavigation().isDone()) && this.isHoldingCrossbow();
    }

    private boolean isValidTarget() {
        return this.mob.getTarget() != null && this.mob.getTarget().isAlive();
    }

    public void stop() {
        boolean isSit;
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.pursuingTarget = false;
        if (this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof CrossbowItem) {
            ItemStack used = this.mob.getUseItem();
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
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        if (mob instanceof Human) {
            ItemStack active = mob.isUsingItem() ? mob.getUseItem() : mob.getMainHandItem();
            if (!(active.getItem() instanceof CrossbowItem)) active = mob.getOffhandItem();
            boolean heavy = club.someoneice.humangunner.SpartanRangedCompat.isHeavyCrossbow(active);
            if (attackDelay > 0) attackDelay = Math.max(1, attackDelay - (heavy ? ((mob.tickCount & 1) == 0 ? 2 : 1) : 4));
            if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof CrossbowItem)
                mob.advanceUseTime(heavy ? 1 : 3);
        }
        LivingEntity livingentity = this.mob.getTarget();
        if (livingentity != null) {
            boolean flag1;
            boolean flag = this.mob.getSensing().hasLineOfSight((Entity)livingentity);
            boolean bl = flag1 = this.seeTime > 0;
            if (flag != flag1) {
                this.seeTime = 0;
            }
            this.seeTime = flag ? ++this.seeTime : --this.seeTime;
            double d0 = this.mob.distanceToSqr((Entity)livingentity);
            if (this.mob instanceof Human human && human.isFleeing) {
                if (this.mob.isUsingItem()
                        && !(this.mob.getUseItem().getItem() instanceof CrossbowItem)) return;
                this.mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
                tickShot(livingentity, flag, d0 <= this.attackRadiusSqr && this.seeTime >= 5);
                return;
            }
            boolean holdingPosition = this.mob instanceof Human human
                    && SoldierOrder.isHoldingPosition(human);
            boolean retreating = !holdingPosition && d0 < RETREAT_DISTANCE_SQR;
            boolean needsShootingAngle = this.seeTime < 5;
            boolean canEngage = d0 <= (double)this.attackRadiusSqr && this.seeTime >= 5;
            if (holdingPosition) {
                this.pursuingTarget = false;
                if (!SoldierOrder.isReturningToHoldPosition((Human)this.mob)) {
                    this.mob.getNavigation().stop();
                }
                this.updatePathDelay = 0;
                this.strafingTime = 0;
            } else if (retreating) {
                if (this.pursuingTarget) {
                    this.mob.getNavigation().stop();
                    this.updatePathDelay = 0;
                    this.pursuingTarget = false;
                }
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0) {
                    Vec3 away = DefaultRandomPos.getPosAway(this.mob, 14, 6, livingentity.position());
                    if (away != null && livingentity.distanceToSqr(away) > d0 + 4.0D) {
                        this.mob.getNavigation().moveTo(
                                away.x, away.y, away.z,
                                this.canRun() ? this.speedModifier : this.speedModifier * 0.65D
                        );
                    } else {
                        this.mob.getNavigation().stop();
                        this.mob.getMoveControl().strafe(-0.80F, this.strafingClockwise ? 0.20F : -0.20F);
                    }
                    this.updatePathDelay = 7 + this.mob.getRandom().nextInt(6);
                }
                this.strafingTime = 0;
            } else if (d0 > (double)this.attackRadiusSqr) {
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0) {
                    // Only close the gap from beyond sniper range. A blocked
                    // shooting angle inside that range is handled below.
                    this.mob.getNavigation().moveTo(
                            (Entity)livingentity,
                            this.canRun() ? this.speedModifier : this.speedModifier * 0.5D
                    );
                    this.pursuingTarget = true;
                    this.updatePathDelay = PATHFINDING_DELAY_RANGE.sample(this.mob.getRandom());
                }
                this.strafingTime = 0;
            } else if (needsShootingAngle) {
                if (this.pursuingTarget) {
                    this.mob.getNavigation().stop();
                    this.updatePathDelay = 0;
                    this.pursuingTarget = false;
                }
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0) {
                    // Reposition without creating a direct path into melee.
                    Vec3 angle = DefaultRandomPos.getPosAway(this.mob, 10, 4, livingentity.position());
                    if (angle != null && livingentity.distanceToSqr(angle) >= RETREAT_DISTANCE_SQR) {
                        this.mob.getNavigation().moveTo(angle.x, angle.y, angle.z,
                                this.canRun() ? this.speedModifier : this.speedModifier * 0.65D);
                    } else {
                        this.mob.getNavigation().stop();
                        this.mob.getMoveControl().strafe(0.0F,
                                this.strafingClockwise ? 0.35F : -0.35F);
                    }
                    this.updatePathDelay = 8;
                }
                this.strafingTime = 0;
            } else {
                this.pursuingTarget = false;
                this.updatePathDelay = 0;
                this.mob.getNavigation().stop();
                if (d0 <= HOLD_DISTANCE_SQR) {
                    if (++this.strafingTime >= 36) {
                        if (this.mob.getRandom().nextFloat() < 0.35F) {
                            this.strafingClockwise = !this.strafingClockwise;
                        }
                        this.strafingTime = 0;
                    }
                    // Deliberately much weaker than the bow user's circling.
                    this.mob.getMoveControl().strafe(0.0F, this.strafingClockwise ? 0.25F : -0.25F);
                } else {
                    this.strafingTime = 0;
                }
            }
            if (retreating && this.mob.getNavigation().isDone()) {
                this.mob.getMoveControl().strafe(-0.80F, this.strafingClockwise ? 0.20F : -0.20F);
            }
            this.mob.getLookControl().setLookAt((Entity)livingentity, 30.0f, 30.0f);
            tickShot(livingentity, flag, canEngage);
        }
    }

    @Override
    public EnumSet<Goal.Flag> getFlags() {
        return this.mob instanceof Human human && human.isFleeing
                ? EnumSet.noneOf(Goal.Flag.class)
                : EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK);
    }

    private void tickShot(LivingEntity livingentity, boolean visible, boolean canEngage) {
            if (this.crossbowState == CrossbowState.UNCHARGED) {
                if (canEngage) {
                    this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof CrossbowItem));
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
                    this.attackDelay = 16 + this.mob.getRandom().nextInt(16);
                    ((CrossbowAttackMob)this.mob).setChargingCrossbow(false);
                    T t = this.mob;
                    if (t instanceof Human) {
                        Human human = (Human)t;
                        human.lastCombatTime = human.tickCount;
                    }
                }
            } else if (this.crossbowState == CrossbowState.CHARGED) {
                --this.attackDelay;
                if (this.attackDelay == 0) {
                    this.crossbowState = CrossbowState.READY_TO_ATTACK;
                }
            } else if (this.crossbowState == CrossbowState.READY_TO_ATTACK && visible) {
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

