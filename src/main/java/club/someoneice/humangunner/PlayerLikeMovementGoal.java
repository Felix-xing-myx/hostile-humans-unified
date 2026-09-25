package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.HumanUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * A flag-free terrain companion which can run beside the weapon goals. It may
 * open a blocked passage, sprint along an existing melee pursuit path, or
 * repeatedly hop during purposeful pursuit and retreat. It does not create
 * paths and suppresses hops while a gunner is aiming/firing.
 */
public final class PlayerLikeMovementGoal extends Goal {
    private final Human human;
    private final CombatAiConfig config;
    private int nextTerrainJumpTick;
    private int nextCombatJumpTick;
    private int stagnantTicks;
    private Vec3 lastPosition;

    public PlayerLikeMovementGoal(Human human) {
        this.human = human;
        this.config = CombatAiConfig.get();
        this.lastPosition = human.position();
    }

    @Override
    public boolean canUse() {
        return config.enabled()
                && movementThreat() != null
                && (!SoldierOrder.isHoldingPosition(human) || human.isFleeing);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        lastPosition = human.position();
        stagnantTicks = 0;
        scheduleCombatJump();
    }

    @Override
    public void stop() {
        if (human.getTarget() == null) {
            MovementSpeedController.normal(human);
        } else {
            MovementSpeedController.combat(human, false);
        }
    }

    @Override
    public void tick() {
        LivingEntity target = movementThreat();
        if (target == null) {
            return;
        }
        // AdaptiveCombatGoal owns the defensive stance. This flag-free helper
        // must not restart navigation or a weapon switch while the shield is
        // raised. A melee soldier may still sprint along its existing attack
        // path so a block does not turn into a stationary hit-reaction loop.
        if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
            MovementSpeedController.combat(human, shouldSprintToMeleeTarget(target));
            return;
        }
        assistExistingNavigation();
        boolean travelling = human.distanceToSqr(target) > 9.0D
                || human.isFleeing
                || human.horizontalCollision;
        boolean retreatBackpedaling = human.getPersistentData().getBoolean("humangunner:retreat_backpedaling");
        if (human.isFleeing) {
            MovementSpeedController.retreat(human, travelling && !retreatBackpedaling);
        } else {
            // Sprint on every genuine melee approach path, including the last
            // few blocks of a close fight. The old six-block distance gate
            // disabled sprint exactly when a target was pressing the Human.
            boolean pursuingTarget = !human.isUsingItem() && shouldSprintToMeleeTarget(target);
            MovementSpeedController.combat(human, pursuingTarget);
        }
        if (retreatBackpedaling) {
            return;
        }
        if (!travelling
                || !human.onGround()
                || human.isInWater()
                || human.isInLava()
                || human.isPassenger()) {
            return;
        }

        boolean gunAttackInProgress = GunAttackMovementState.isAttackActive(human);
        boolean movingWithCombatIntent = isPursuing(target) || isRetreatingFrom(target);
        if (!gunAttackInProgress && !human.isUsingItem()
                && movingWithCombatIntent && human.tickCount >= nextTerrainJumpTick) {
            nextTerrainJumpTick = human.tickCount + 3;
            if (pathSuggestsStepUp()) {
                human.getJumpControl().jump();
                nextTerrainJumpTick = human.tickCount + 7;
                scheduleCombatJump();
                return;
            }
        }
        if (gunAttackInProgress || human.isUsingItem()
                || human.tickCount < nextCombatJumpTick) {
            return;
        }
        if (movingWithCombatIntent && human.getRandom().nextDouble() < config.combatJumpChance()) {
            human.getJumpControl().jump();
            scheduleCombatJump();
        } else {
            // Keep checking shortly after a turn or a temporary stop, rather
            // than waiting out a full hop interval after movement resumes.
            nextCombatJumpTick = human.tickCount + 4 + human.getRandom().nextInt(5);
        }
    }

    private void assistExistingNavigation() {
        boolean navigating = !human.getNavigation().isDone();
        double movedSqr = human.position().distanceToSqr(lastPosition);
        if (navigating && movedSqr < 0.0064D) {
            stagnantTicks++;
        } else if (movedSqr >= 0.0064D || !navigating) {
            stagnantTicks = 0;
        }
        lastPosition = human.position();

        if (human.horizontalCollision || stagnantTicks >= 5) {
            if (NavigationSupport.openBlockingPassage(human)) {
                stagnantTicks = Math.max(0, stagnantTicks - 3);
            }
        }
    }

    private boolean pathSuggestsStepUp() {
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        BlockPos nextNode = path.getNextNodePos();
        Vec3 next = Vec3.atCenterOf(nextNode);
        Vec3 pathDirection = NavigationSupport.horizontalDirection(human.position(), next);
        int feetY = Mth.floor(human.getY() + 0.1D);
        double horizontalDistanceSqr = human.position().multiply(1.0D, 0.0D, 1.0D)
                .distanceToSqr(next.multiply(1.0D, 0.0D, 1.0D));
        boolean risingOneBlock = nextNode.getY() > feetY
                && nextNode.getY() <= feetY + 1
                && horizontalDistanceSqr <= 4.0D;
        return risingOneBlock
                || NavigationSupport.hasOneBlockObstacleAhead(human, pathDirection);
    }

    private boolean pathHeadsToward(LivingEntity target) {
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        Vec3 pathDirection = NavigationSupport.horizontalDirection(
                human.position(), Vec3.atCenterOf(path.getNextNodePos())
        );
        Vec3 targetDirection = NavigationSupport.horizontalDirection(human.position(), target.position());
        return pathDirection.lengthSqr() > 0.01D
                && targetDirection.lengthSqr() > 0.01D
                && pathDirection.dot(targetDirection) >= 0.60D;
    }

    private boolean shouldSprintToMeleeTarget(LivingEntity target) {
        if (human.isFleeing
                || SoldierOrder.isHoldingPosition(human)
                || GunCustody.hasOwnedGun(human)
                || GunSupport.get().isGun(human.getMainHandItem())
                || HumanUtil.isRangedWeapon(human.getMainHandItem())
                || HumanUtil.isTrident(human.getMainHandItem())
                || human.distanceToSqr(target) <= meleeReachSqr(target)) {
            return false;
        }
        return pathHeadsToward(target);
    }

    private double meleeReachSqr(LivingEntity target) {
        double reach = human.getBbWidth() * 3.0D;
        return reach * reach + target.getBbWidth();
    }

    private boolean movingToward(LivingEntity target) {
        Vec3 movement = human.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        Vec3 targetDirection = NavigationSupport.horizontalDirection(human.position(), target.position());
        return movement.lengthSqr() > 0.0025D
                && targetDirection.lengthSqr() > 0.01D
                && movement.normalize().dot(targetDirection) >= 0.55D;
    }

    private boolean pathHeadsAwayFrom(LivingEntity target) {
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        Vec3 pathDirection = NavigationSupport.horizontalDirection(
                human.position(), Vec3.atCenterOf(path.getNextNodePos())
        );
        Vec3 awayDirection = NavigationSupport.horizontalDirection(target.position(), human.position());
        return pathDirection.lengthSqr() > 0.01D
                && awayDirection.lengthSqr() > 0.01D
                && pathDirection.dot(awayDirection) >= 0.80D;
    }

    private boolean movingAwayFrom(LivingEntity target) {
        Vec3 movement = human.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        Vec3 awayDirection = NavigationSupport.horizontalDirection(target.position(), human.position());
        return movement.lengthSqr() > 0.0049D
                && awayDirection.lengthSqr() > 0.01D
                && movement.normalize().dot(awayDirection) >= 0.75D;
    }

    private boolean isPursuing(LivingEntity target) {
        return !human.isFleeing
                && pathHeadsToward(target)
                && movingToward(target);
    }

    private boolean isRetreatingFrom(LivingEntity threat) {
        return human.isFleeing
                && pathHeadsAwayFrom(threat)
                && movingAwayFrom(threat);
    }

    private LivingEntity movementThreat() {
        if (human.isFleeing && human.toAvoid != null && human.toAvoid.isAlive()) {
            return human.toAvoid;
        }
        LivingEntity target = human.getTarget();
        return target != null && target.isAlive() ? target : null;
    }

    private void scheduleCombatJump() {
        int minimum = config.combatJumpIntervalMin();
        int maximum = Math.max(minimum, config.combatJumpIntervalMax());
        nextCombatJumpTick = human.tickCount
                + minimum + human.getRandom().nextInt(maximum - minimum + 1);
    }

}
