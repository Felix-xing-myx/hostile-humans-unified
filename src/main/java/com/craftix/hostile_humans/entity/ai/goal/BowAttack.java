package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.BowRangePolicy;
import club.someoneice.humangunner.RangedFiringPosition;
import club.someoneice.humangunner.RangedWeaponCustody;
import club.someoneice.humangunner.SoldierOrder;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class BowAttack<T extends HumanEntity>
extends Goal {
    private final T mob;
    private final double speedModifier;
    private final float attackRadiusSqr;
    private int attackIntervalMin;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private int strafingTime = -1;
    private int updatePathDelay;
    private int nextFiringPositionTick;

    public BowAttack(T p_25792_, double p_25793_, int interval, float p_25795_) {
        this.mob = p_25792_;
        this.speedModifier = p_25793_;
        this.attackIntervalMin = interval;
        this.attackRadiusSqr = p_25795_ * p_25795_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public void setMinAttackInterval(int p_25798_) {
        this.attackIntervalMin = p_25798_;
    }

    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.isHoldingBow();
    }

    protected boolean isHoldingBow() {
        if (this.mob instanceof Human human
                && (RangedWeaponCustody.isActive(human)
                || HumanUtil.isMeleeWeapon(human.getMainHandItem()))) {
            return false;
        }
        return this.mob.isHolding(is -> is.getItem() instanceof BowItem);
    }

    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        // Do not keep the orbit goal alive merely because its last pursuit path
        // has not finished. That leaves its strafe command active after combat.
        return target != null && target.isAlive() && this.isHoldingBow();
    }

    public void start() {
        super.start();
        this.mob.setAggressive(true);
        this.nextFiringPositionTick = this.mob.tickCount;
        this.strafingTime = -1;
    }

    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = -1;
        this.strafingTime = -1;
        if (this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof BowItem) {
            this.mob.stopUsingItem();
        }
        boolean isSit = this.mob.isOrderedToSit();
        if (isSit) {
            ((HumanMobEntityData)this.mob).setOrderedToPosition((BlockPos)this.mob.getEntityData().get(HumanMobEntityData.DATA_SIT_POS));
        } else if (this.mob.getTarget() == null || !this.mob.getTarget().isAlive()) {
            this.mob.getNavigation().stop();
            this.mob.getMoveControl().setWantedPosition(
                    this.mob.position().x(), this.mob.position().y(), this.mob.position().z(), 0.0D
            );
        }
        clearStrafeInput();
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        if (mob instanceof Human) {
            if (attackTime > 0) attackTime = Math.max(1, attackTime - 1);
            if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem)
                mob.advanceUseTime(2);
        }
        LivingEntity livingentity = this.mob.getTarget();
        if (livingentity == null || !livingentity.isAlive()) {
            this.mob.getNavigation().stop();
            clearStrafeInput();
            return;
        }
        if (this.mob instanceof Human human && human.isFleeing) {
            if (this.mob.isUsingItem() && !(this.mob.getUseItem().getItem() instanceof BowItem)) return;
            boolean visible = this.mob.getSensing().hasLineOfSight(livingentity);
            this.seeTime = visible ? Math.max(1, this.seeTime + 1) : Math.min(-1, this.seeTime - 1);
            this.mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
            if (this.mob.distanceToSqr(livingentity) <= this.attackRadiusSqr) {
                tickShot(livingentity, visible);
            }
            return;
        }
        {
            boolean flag1;
            double d0 = this.mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
            boolean flag = this.mob.getSensing().hasLineOfSight((Entity)livingentity);
            boolean bl = flag1 = this.seeTime > 0;
            if (flag != flag1) {
                this.seeTime = 0;
            }
            this.seeTime = flag ? ++this.seeTime : --this.seeTime;
            boolean holdingPosition = this.mob instanceof Human human
                    && SoldierOrder.isHoldingPosition(human);
            boolean retreating = !holdingPosition && BowRangePolicy.shouldPathRetreat(d0);
            boolean blockedWhileEngaging = !flag
                    && !retreating
                    && !holdingPosition
                    && !BowRangePolicy.shouldPursue(d0, this.attackRadiusSqr);
            if (holdingPosition) {
                if (!SoldierOrder.isReturningToHoldPosition((Human)this.mob)) {
                    this.mob.getNavigation().stop();
                }
                this.updatePathDelay = 0;
                this.strafingTime = -1;
            } else if (retreating) {
                --this.updatePathDelay;
                if (this.updatePathDelay <= 0) {
                    Vec3 away = DefaultRandomPos.getPosAway(this.mob, 12, 6, livingentity.position());
                    if (away != null && BowRangePolicy.usefulRetreatDestination(
                            d0, livingentity.distanceToSqr(away))) {
                        this.mob.getNavigation().moveTo(away.x, away.y, away.z, this.speedModifier * 1.1D);
                    } else {
                        this.mob.getNavigation().stop();
                        this.mob.getMoveControl().strafe(
                                -1.0F, this.strafingClockwise ? 0.45F : -0.45F
                        );
                    }
                    this.updatePathDelay = 6 + this.mob.getRandom().nextInt(5);
                }
                this.strafingTime = -1;
            } else if (blockedWhileEngaging) {
                // Orbiting blindly can carry the archer behind cover. While
                // still in attack posture, pause lateral input and seek a
                // reachable point with a verified firing lane instead.
                if (this.mob.getNavigation().isDone() || this.mob.getNavigation().isStuck()) {
                    if (this.mob.tickCount >= this.nextFiringPositionTick) {
                        Path firingPath = this.mob instanceof Human human
                                ? RangedFiringPosition.findVisiblePath(
                                        human, livingentity, 14.0D,
                                        Math.sqrt(this.attackRadiusSqr), 10, 12)
                                : null;
                        if (firingPath != null) {
                            this.mob.getNavigation().moveTo(firingPath, this.speedModifier);
                            this.updatePathDelay = 0;
                        } else {
                            this.mob.getNavigation().stop();
                            this.updatePathDelay = 0;
                        }
                        this.nextFiringPositionTick = this.mob.tickCount + 10;
                    }
                }
                this.strafingTime = -1;
                clearStrafeInput();
                this.mob.getLookControl().setLookAt(livingentity, 30.0F, 30.0F);
                tickShot(livingentity, false);
                return;
            } else if (!BowRangePolicy.shouldPursue(d0, this.attackRadiusSqr)) {
                // Losing sight inside shooting range is not permission to
                // charge. Orbit to change the angle while preserving spacing.
                this.mob.getNavigation().stop();
                this.updatePathDelay = 0;
                ++this.strafingTime;
            } else {
                this.mob.getNavigation().moveTo((Entity)livingentity, this.speedModifier);
                this.updatePathDelay = 0;
                this.strafingTime = -1;
            }
            if (this.strafingTime >= 20) {
                if ((double)this.mob.getRandom().nextFloat() < 0.3) {
                    boolean bl2 = this.strafingClockwise = !this.strafingClockwise;
                }
                this.strafingTime = 0;
            }
            if (this.strafingTime > -1) {
                // One controller owns both radial spacing and lateral orbiting.
                // The 14-16 block buffer deliberately mixes backward and side
                // input so circling cannot silently erase the retreat margin.
                float side = BowRangePolicy.orbitSideInput(d0);
                this.mob.getMoveControl().strafe(
                        BowRangePolicy.orbitForwardInput(d0),
                        this.strafingClockwise ? side : -side
                );
                this.mob.lookAt((Entity)livingentity, 30.0f, 30.0f);
            } else if (retreating && this.mob.getNavigation().isDone()) {
                // Pathfinding can fail in cramped terrain; keep backpedalling
                // and firing instead of freezing beside the attacker.
                this.mob.getMoveControl().strafe(-1.0F, this.strafingClockwise ? 0.45F : -0.45F);
                this.mob.lookAt((Entity)livingentity, 30.0F, 30.0F);
            } else {
                this.mob.getLookControl().setLookAt((Entity)livingentity, 30.0f, 30.0f);
            }
            tickShot(livingentity, flag);
        }
    }

    @Override
    public EnumSet<Goal.Flag> getFlags() {
        return this.mob instanceof Human human && human.isFleeing
                ? EnumSet.noneOf(Goal.Flag.class)
                : EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK);
    }

    private void tickShot(LivingEntity target, boolean visible) {
        if (this.mob.isUsingItem()) {
            int usedTicks;
            if (!visible && this.seeTime < -60) {
                this.mob.stopUsingItem();
            } else if (visible && (usedTicks = this.mob.getTicksUsingItem()) >= 20) {
                this.mob.stopUsingItem();
                ((RangedAttackMob)this.mob).performRangedAttack(target,
                        BowItem.getPowerForTime(usedTicks));
                this.attackTime = this.attackIntervalMin;
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(
                    this.mob, item -> item instanceof BowItem));
        }
    }

    private void clearStrafeInput() {
        this.mob.getMoveControl().strafe(0.0F, 0.0F);
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }
}

