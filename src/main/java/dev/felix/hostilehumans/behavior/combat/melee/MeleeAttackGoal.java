package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.HumanEntity;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.entity.HumanMobEntityData;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeAttackTiming;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeCombatRange;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeSpacing;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class MeleeAttackGoal
extends Goal {
    protected final HumanEntity mob;
    private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 5L;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private int recoveryRouteUntilTick;
    private int recoveryTargetId;
    private final dev.felix.hostilehumans.behavior.combat.melee.MeleePursuitProgress pursuitProgress =
            new dev.felix.hostilehumans.behavior.combat.melee.MeleePursuitProgress();

    public MeleeAttackGoal(HumanEntity humanEntity, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.mob = humanEntity;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isGunItem(this.mob.getMainHandItem())) return false;
        if (this.mob instanceof Human human && human.getMainHandItem().isEmpty()
                && human.getTarget() != null) {
            dev.felix.hostilehumans.inventory.HumanLootManager.equipMeleeFallback(human);
        }
        long gameTime;
        HumanEntity humanEntity = this.mob;
        if (humanEntity instanceof Human) {
            Human human = (Human)humanEntity;
            if (human.isFleeing) {
                return false;
            }
        }
        if ((gameTime = this.mob.level().getGameTime()) - this.lastCanUseCheck < COOLDOWN_BETWEEN_CAN_USE_CHECKS) {
            return false;
        }
        this.lastCanUseCheck = gameTime;
        LivingEntity livingEntity = this.mob.getTarget();
        if (livingEntity == null || !livingEntity.isAlive()
                || !this.mob.canAttack(livingEntity)) {
            return false;
        }
        if (this.mob instanceof Human human && SoldierOrder.isHoldingPosition(human)) {
            return true;
        }
        if (canStrike(livingEntity)) {
            // Starting an in-reach attack does not need a pursuit path.
            this.path = null;
            return true;
        }
        if (this.mob instanceof Human human
                && dev.felix.hostilehumans.navigation.CombatTerrainNavigation.hasWork(human, livingEntity)) {
            this.path = null;
            return true;
        }
        if (this.mob instanceof Human human
                && !dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1)) {
            this.path = null;
            return true;
        }
        this.path = this.mob.getNavigation().createPath((Entity)livingEntity, 0);
        if (this.path != null) {
            return true;
        }
        if (this.mob instanceof Human human && human.shouldUseWaterMovement()) {
            // A water navigator may not produce a complete path to a target
            // on the opposite bank. Keep the combat goal active so its direct
            // water fallback can continue the crossing.
            return true;
        }
        // Keep the goal alive to retry/recover an unreachable or partial path.
        // Otherwise a mutually blocked pair never runs its movement watchdog.
        return this.mob instanceof Human;
    }

    public boolean canContinueToUse() {
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isGunItem(this.mob.getMainHandItem())) return false;
        HumanEntity humanEntity = this.mob;
        if (humanEntity instanceof Human) {
            Human human = (Human)humanEntity;
            if (human.isFleeing) {
                return false;
            }
        }
        LivingEntity livingEntity = this.mob.getTarget();
        if (livingEntity == null || !livingEntity.isAlive()
                || !this.mob.canAttack(livingEntity)) {
            return false;
        }
        if (!this.followingTargetEvenIfNotSeen) {
            if (this.mob instanceof Human human
                    && dev.felix.hostilehumans.navigation.CombatTerrainNavigation.hasWork(human, livingEntity)) return true;
            return !this.mob.getNavigation().isDone();
        }
        return this.mob.isWithinRestriction(livingEntity.blockPosition());
    }

    public void start() {
        this.recoveryRouteUntilTick = 0;
        boolean canPursue = !this.mob.isOrderedToSit();
        if (this.mob instanceof Human human) {
            canPursue &= !SoldierOrder.isHoldingPosition(human);
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                canPursue = canPursue && (!MeleeCombatRange.canStrike(human, target)
                        || this.mob.distanceTo(target)
                        > MeleeSpacing.preferredDistance(human,target));
            }
        }
        if (canPursue && !(this.mob instanceof Human human
                && dev.felix.hostilehumans.navigation.CombatTerrainNavigation.hasWork(human, this.mob.getTarget()))) {
            if (this.path != null) this.mob.getNavigation().moveTo(this.path, this.speedModifier);
            else this.mob.getNavigation().stop();
        }
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    public void stop() {
        // Target ownership belongs to targeting/order logic, not the movement
        // goal. Defense, a weapon handoff or a failed route may interrupt us.
        LivingEntity target = this.mob.getTarget();
        if (target != null && (!target.isAlive() || !this.mob.canAttack(target))) this.mob.setTarget(null);
        this.mob.setAggressive(false);
        if (this.mob instanceof Human human) dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.cancel(human, "melee goal stopped");
        // A defensive interruption is not a failed target/path acquisition.
        // Let melee resume at the next selector pass instead of idling through
        // the expensive-acquisition throttle after every received hit.
        this.lastCanUseCheck = this.mob.level().getGameTime() - COOLDOWN_BETWEEN_CAN_USE_CHECKS;
        boolean isSit = this.mob.isOrderedToSit();
        if (isSit) {
            this.mob.setOrderedToPosition((BlockPos)this.mob.getEntityData().get(HumanMobEntityData.DATA_SIT_POS));
        } else if (target == null || !target.isAlive()) {
            this.mob.getMoveControl().setWantedPosition(this.mob.position().x(), this.mob.position().y(), this.mob.position().z(), 1.0);
        }
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        if (this.mob instanceof Human human) {
            dev.felix.hostilehumans.behavior.combat.melee.MeleeActionControl.tick(human);
            if (human.isFleeing || !human.hhu$shieldDefense().permitsMeleeMovement()) return;
            // An advance shield forbids strikes, not the existing pursuit/terrain executor.
            // Equipment preparation still belongs to an available attack hand.
            if (human.hhu$canMeleeAttack()) dev.felix.hostilehumans.behavior.combat.melee.NpcDualWield.prepare(human);
        }
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isGunItem(this.mob.getMainHandItem())) return;
        LivingEntity livingEntity = this.mob.getTarget();
        if (livingEntity != null) {
            Player player;
            ItemStack itemStack;
            if (livingEntity instanceof Player && ((itemStack = (player = (Player)livingEntity).getItemInHand(player.getUsedItemHand())).is(this.mob.getTameItem()) || this.mob.isOwnedBy((LivingEntity)player))) {
                this.mob.setTarget(null);
                this.stop();
                return;
            }
            this.mob.getLookControl().setLookAt((Entity)livingEntity, 30.0f, 30.0f);
            double distance = this.mob.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
            if (this.mob instanceof Human human && distance <= this.getAttackReachSqr(livingEntity) + 4.0D)
                MeleeCombatRange.faceTarget(human, livingEntity);
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            boolean holdingPosition = this.mob instanceof Human human
                    && SoldierOrder.isHoldingPosition(human);
            if (!holdingPosition && this.mob instanceof Human human
                    && dev.felix.hostilehumans.navigation.WaterCombatNavigation.controlMelee(human, livingEntity, this.speedModifier)) {
                pursuitProgress.stalled(this.mob.tickCount, livingEntity.getId(),
                        this.mob.getX(), this.mob.getY(), this.mob.getZ(), false);
                this.tickAttackOnly(livingEntity);
                return;
            }
            if (this.mob instanceof Human human)
                dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.tryStart(human, livingEntity, this.ticksUntilNextAttack);
            boolean spacing = !holdingPosition && this.mob instanceof Human human
                    && !dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.active(human)
                    && !dev.felix.hostilehumans.navigation.CombatTerrainNavigation.hasWork(human, livingEntity)
                    && MeleeSpacing.control(human, livingEntity);
            boolean stalled = pursuitProgress.stalled(this.mob.tickCount, livingEntity.getId(),
                    this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    !holdingPosition && !spacing && !canStrike(livingEntity)
                            && !this.mob.isUsingItem());
            if (stalled) {
                recoverPursuit(livingEntity);
                this.ticksUntilNextPathRecalculation = 8;
            }
            if (spacing && this.ticksUntilNextPathRecalculation <= 0) {
                this.ticksUntilNextPathRecalculation = 8;
            }
            if (this.mob instanceof Human human) {
                if (!holdingPosition && !spacing
                        && (this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight(livingEntity))
                        && (this.mob.tickCount >= recoveryRouteUntilTick
                        || livingEntity.getId() != recoveryTargetId || this.mob.getNavigation().isDone()))
                    dev.felix.hostilehumans.navigation.PursuitNavigation.update(human, livingEntity, this.speedModifier);
            } else if (!holdingPosition && !spacing
                    && (this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight((Entity)livingEntity))
                    && this.ticksUntilNextPathRecalculation <= 0
                    && (this.mob.tickCount >= recoveryRouteUntilTick
                    || livingEntity.getId() != recoveryTargetId || this.mob.getNavigation().isDone())
                    && (this.mob.getNavigation().isDone()
                    || this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                    || livingEntity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                    || this.mob.getRandom().nextFloat() < 0.05f)) {
                this.pathedTargetX = livingEntity.getX();
                this.pathedTargetY = livingEntity.getY();
                this.pathedTargetZ = livingEntity.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);
                if (distance > 1024.0) {
                    this.ticksUntilNextPathRecalculation += 10;
                } else if (distance > 256.0) {
                    this.ticksUntilNextPathRecalculation += 5;
                }
                if (this.mob instanceof Human human
                        && !dev.felix.hostilehumans.navigation.BasicNavigationBudget.tryAcquire(human, 1)) {
                    this.ticksUntilNextPathRecalculation = 1;
                } else if (!this.mob.getNavigation().moveTo((Entity)livingEntity, this.speedModifier)) {
                    this.ticksUntilNextPathRecalculation += 15;
                }
                this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
            }
            if (!holdingPosition && this.mob instanceof Human human) {
                human.approachCombatTargetInWater(livingEntity,
                        Math.sqrt(this.getAttackReachSqr(livingEntity)), this.speedModifier);
            }
            this.tickAttackOnly(livingEntity);
        }
    }

    /** Reuse the same cooldown and committed hit checks without issuing pursuit movement. */
    protected final void tickAttackOnly(LivingEntity target) {
        this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
        this.checkAndPerformAttack(target);
    }

    protected void checkAndPerformAttack(LivingEntity livingEntity) {
        // Check before stopUsingItem(): an emergency weapon handoff must not
        // let the old melee goal lower the shield and create a swing packet.
        if (this.mob instanceof Human human && (human.isFleeing || !human.hhu$canMeleeAttack())) return;
        if (this.mob instanceof Human human && (human.hhu$hasPendingMeleeAttack()
                || dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.awaitingAttackWindow(human))) return;
        if (canStrike(livingEntity) && this.ticksUntilNextAttack <= 0) {
            HumanEntity humanEntity;
            if (this.mob.isBlocking()) {
                this.mob.stopUsingItem();
            }
            if ((humanEntity = this.mob) instanceof Human) {
                Human human = (Human)humanEntity;
                human.lastCombatTime = human.tickCount;
            }
            if (this.mob.doHurtTarget((Entity)livingEntity)) {
                this.resetAttackCooldown();
                if (this.mob instanceof Human human) dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.markEngaged(human);
            }
        }
    }

    /** Opens one guaranteed attack window after an adaptive shield block. */
    public boolean hhu$tryImmediateCounterattack(LivingEntity target) {
        if (this.mob instanceof Human human && (human.isFleeing || !human.hhu$canMeleeAttack()
                || human.hhu$shieldDefense().shouldSuppressRecovery())) return false;
        if (this.mob instanceof Human human && human.hhu$hasPendingMeleeAttack()) return false;
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isGunItem(this.mob.getMainHandItem())) return false;
        if (target == null || !target.isAlive() || !this.mob.canAttack(target)) {
            return false;
        }
        if (this.mob instanceof Human human) {
            dev.felix.hostilehumans.behavior.combat.melee.NpcDualWield.prepare(human);
            dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.tryStart(human, target, this.ticksUntilNextAttack);
            if (dev.felix.hostilehumans.behavior.combat.melee.PursuitJumpAttack.awaitingAttackWindow(human)) return false;
            MeleeCombatRange.faceTarget(human, target);
        }
        if (this.mob instanceof Human human ? !MeleeCombatRange.canStartAttack(human, target)
                : !canStrike(target)) return false;
        if (this.mob.isBlocking()) {
            this.mob.stopUsingItem();
        }
        if (this.mob instanceof Human human) {
            human.lastCombatTime = human.tickCount;
        }
        // Human.doHurtTarget owns the animation after the final shared validation.
        boolean accepted = this.mob.doHurtTarget(target);
        if (accepted) this.resetAttackCooldown();
        return accepted;
    }

    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.adjustedTickDelay(
                this.mob instanceof Human human
                        ? MeleeAttackTiming.nextCooldown(human)
                        : MeleeAttackTiming.nextCooldown(this.mob.getRandom())
        );
    }

    protected double getAttackReachSqr(LivingEntity livingEntity) {
        if (this.mob instanceof Human human) return MeleeCombatRange.reachSqr(human, livingEntity);
        return this.mob.getBbWidth() * 3.0f * this.mob.getBbWidth() * 3.0f + livingEntity.getBbWidth();
    }

    private boolean canStrike(LivingEntity target) {
        return this.mob instanceof Human human ? MeleeCombatRange.canStrike(human, target)
                : this.mob.distanceToSqr(target) <= getAttackReachSqr(target)
                && this.mob.getSensing().hasLineOfSight(target);
    }

    private void recoverPursuit(LivingEntity target) {
        if (this.mob instanceof Human human) {
            if (!dev.felix.hostilehumans.navigation.CombatTerrainNavigation.hasWork(human, target))
                dev.felix.hostilehumans.navigation.CombatTerrainNavigation.failed(human, target);
            dev.felix.hostilehumans.navigation.PursuitNavigation.update(human, target, this.speedModifier);
            return;
        }
        // One bounded flank path attempt per stall, not a scan or forced push.
        Path oldPath = this.mob.getNavigation().getPath();
        if (oldPath != null && !oldPath.isDone()
                && !oldPath.getNextNodePos().equals(this.mob.blockPosition())
                && this.mob.getNavigation() instanceof dev.felix.hostilehumans.navigation.HumanNavigation ground)
            ground.avoidWaypoint(oldPath.getNextNodePos(), this.mob.level().getGameTime() + 60L);
        Vec3 candidate = DefaultRandomPos.getPosTowards(this.mob, 6, 3, target.position(), Math.PI / 2.0D);
        if (candidate != null && this.mob.getNavigation().moveTo(candidate.x, candidate.y, candidate.z, this.speedModifier)) {
            recoveryRouteUntilTick = this.mob.tickCount + 30;
            recoveryTargetId = target.getId();
            return;
        }
        if (oldPath != null && !oldPath.isDone()) this.mob.getNavigation().moveTo(oldPath, this.speedModifier);
        if (this.mob.onGround() && this.mob.horizontalCollision && oldPath != null && !oldPath.isDone()
                && oldPath.getNextNodePos().getY() > this.mob.getY()
                && oldPath.getNextNodePos().getY() <= this.mob.getY() + 1.0D)
            this.mob.getJumpControl().jump();
    }
}

