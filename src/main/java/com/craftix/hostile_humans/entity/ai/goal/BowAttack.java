package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.BowRangePolicy;
import club.someoneice.humangunner.HumanAmmo;
import club.someoneice.humangunner.RangedFiringPosition;
import club.someoneice.humangunner.RangedWeaponCustody;
import club.someoneice.humangunner.RangedStrafeSpeed;
import club.someoneice.humangunner.ShoreSeekingPolicy;
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
    private final BowMovementExecution<T> movement;
    private final float attackRadiusSqr;
    private int attackIntervalMin;
    private int attackTime = -1;
    private int seeTime;

    public BowAttack(T p_25792_, double p_25793_, int interval, float p_25795_) {
        this.mob = p_25792_;
        this.speedModifier = p_25793_;
        this.movement = new BowMovementExecution<>(mob, speedModifier);
        this.attackIntervalMin = interval;
        if (mob instanceof Human human) club.someoneice.humangunner.HumanArrowCombat.configureBowInterval(human, interval);
        this.attackRadiusSqr = p_25795_ * p_25795_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    public void setMinAttackInterval(int p_25798_) {
        this.attackIntervalMin = p_25798_;
        if (mob instanceof Human human) club.someoneice.humangunner.HumanArrowCombat.configureBowInterval(human, p_25798_);
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
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.isHoldingBow();
    }

    protected boolean isHoldingBow() {
        if (this.mob instanceof Human human
                && (!HumanAmmo.hasArrows(human) || HumanAmmo.arrowCount(human) == 0 || RangedWeaponCustody.isActive(human)
                || HumanUtil.isMeleeWeapon(human.getMainHandItem()))) {
            return false;
        }
        return mob instanceof Human human ? human.getMainHandItem().getItem() instanceof BowItem
                : this.mob.isHolding(is -> is.getItem() instanceof BowItem);
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
        movement.start();
    }

    public void stop() {
        super.stop();
        if (this.mob instanceof Human human) RangedFiringPosition.cancelVisibleSearch(human);
        if (this.mob instanceof Human human && human.isCombatFiringShore()) {
            human.stopSeekingShore();
        }
        movement.setOrbitSpeed(false);
        this.mob.setAggressive(false);
        this.seeTime = 0;
        if (!(mob instanceof Human)) this.attackTime = -1;
        movement.reset();
        if (this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof BowItem) {
            if (this.mob instanceof Human human) human.humanGunner$shieldDefense().pauseRangedUseForGuard();
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
        movement.clearStrafeInput();
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        if (!this.canContinueToUse()) {
            this.stop();
            return;
        }
        if (mob instanceof Human human) {
            if (mob.isUsingItem() && mob.getUseItem().getItem() instanceof net.minecraft.world.item.BowItem)
                club.someoneice.humangunner.HumanArrowCombat.advanceBowDraw(human);
        }
        LivingEntity livingentity = this.mob.getTarget();
        if (livingentity == null || !livingentity.isAlive()) {
            movement.setOrbitSpeed(false);
            this.mob.getNavigation().stop();
            movement.clearStrafeInput();
            return;
        }
        if (this.mob instanceof Human human) {
            human.updateCombatFiringShore(livingentity, this.speedModifier);
        }
        boolean shoreSeeking = this.mob instanceof Human human
                && ShoreSeekingPolicy.isShoreTransitionActive(
                        human.isSeekingShore(), human.isShoreTransitionPending());
        if ((this.mob instanceof Human human && human.isFleeing) || shoreSeeking) {
            movement.setOrbitSpeed(false);
            if (this.mob.isUsingItem() && !(this.mob.getUseItem().getItem() instanceof BowItem)) return;
            boolean visible = this.mob instanceof Human shotHuman
                    ? club.someoneice.humangunner.RangedShotClearance.canShoot(shotHuman, livingentity)
                    : this.mob.getSensing().hasLineOfSight(livingentity);
            this.seeTime = visible ? Math.max(1, this.seeTime + 1) : Math.min(-1, this.seeTime - 1);
            this.mob.getLookControl().setLookAt(livingentity, 45.0F, 35.0F);
            if (this.mob.distanceToSqr(livingentity) <= attackRadiusSqr()) {
                tickShot(livingentity, visible);
            }
            return;
        }
        {
            boolean flag1;
            double d0 = this.mob.distanceToSqr(livingentity.getX(), livingentity.getY(), livingentity.getZ());
            boolean flag = (this.mob instanceof Human shotHuman ? club.someoneice.humangunner.RangedShotClearance.canShoot(shotHuman, livingentity) : this.mob.getSensing().hasLineOfSight((Entity)livingentity));
            boolean bl = flag1 = this.seeTime > 0;
            if (flag != flag1) {
                this.seeTime = 0;
            }
            this.seeTime = flag ? ++this.seeTime : --this.seeTime;
            if (mob instanceof Human human) club.someoneice.humangunner.RangedTerrainCombat.observe(human, livingentity, flag);
            boolean visibleForShot = movement.tick(livingentity, d0, flag,
                    new BowMovementExecution.Request(attackRadiusSqr(), minimumBand(), maximumBand(),
                            mob instanceof Human human && club.someoneice.humangunner.RangedCombatProfile
                                    .of(human, human.getMainHandItem()).advance()));
            tickShot(livingentity, visibleForShot);
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

    private void tickShot(LivingEntity target, boolean visible) {
        if (mob instanceof Human human && RangedWeaponCustody.isActive(human)) return;
        if (mob instanceof Human human && human.humanGunner$shieldDefense().reservesRangedUse()) return;
        if (this.mob instanceof Human human && !HumanAmmo.hasArrows(human)) {
            if (this.mob.isUsingItem() && this.mob.getUseItem().getItem() instanceof BowItem)
                this.mob.stopUsingItem();
            return;
        }
        if (visible && this.mob instanceof Human human) {
            human.markRangedFacing(target);
        }
        if (this.mob.isUsingItem()) {
            if (!(this.mob.getUseItem().getItem() instanceof BowItem)) return;
            int usedTicks;
            if (!visible && this.seeTime < -60) {
                this.mob.stopUsingItem();
            } else if (visible && (usedTicks = this.mob.getTicksUsingItem()) >= 20
                    && (!(mob instanceof Human human) || club.someoneice.humangunner.HumanArrowCombat.canShoot(human, target))) {
                this.mob.stopUsingItem();
                ((RangedAttackMob)this.mob).performRangedAttack(target,
                        BowItem.getPowerForTime(usedTicks));
                this.attackTime = this.attackIntervalMin;
            }
        } else if ((mob instanceof Human human ? club.someoneice.humangunner.HumanArrowCombat.canDrawBow(human)
                : --this.attackTime <= 0) && this.seeTime >= -60) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(
                    this.mob, item -> item instanceof BowItem));
            if (this.mob instanceof Human human) human.humanGunner$shieldDefense().restoreRangedUseAfterGuard();
        }
    }

}
