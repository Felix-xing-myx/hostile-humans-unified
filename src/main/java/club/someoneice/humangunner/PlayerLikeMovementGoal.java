package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * A flag-free terrain companion which can run beside the weapon goals. It may
 * open a blocked passage or step-jump along an existing path, but never creates
 * pursuit paths, forces a sprint charge, or performs combat jumps of its own.
 */
public final class PlayerLikeMovementGoal extends Goal {
    private final Human human;
    private final CombatAiConfig config;
    private int nextTerrainJumpTick;
    private int nextLongRangeJumpTick;
    private int stagnantTicks;
    private Vec3 lastPosition;

    public PlayerLikeMovementGoal(Human human) {
        this.human = human;
        this.config = CombatAiConfig.get();
        this.lastPosition = human.position();
    }

    @Override
    public boolean canUse() {
        LivingEntity target = human.getTarget();
        return config.enabled()
                && target != null
                && target.isAlive()
                && !SoldierOrder.isHoldingPosition(human);
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
        scheduleLongRangeJump();
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
        LivingEntity target = human.getTarget();
        if (target == null) {
            return;
        }
        // AdaptiveCombatGoal owns the defensive stance. This flag-free helper
        // must not restart navigation, sprinting or a weapon switch while the
        // shield is actively raised.
        if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
            MovementSpeedController.combat(human, false);
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
            // Sprint only on a genuine long-range approach path. In
            // particular, a bow/crossbow orbit or retreat cannot become a
            // sprint charge merely because its target is far away.
            boolean longRangeApproach = !human.isUsingItem()
                    && human.distanceToSqr(target) >= 18.0D * 18.0D
                    && pathHeadsToward(target)
                    && !GunSupport.get().isGun(human.getMainHandItem());
            MovementSpeedController.combat(human, longRangeApproach);
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

        if (pathSuggestsStepUp() && human.tickCount >= nextTerrainJumpTick) {
            human.getJumpControl().jump();
            nextTerrainJumpTick = human.tickCount + 7;
            scheduleLongRangeJump();
            return;
        }
        if (human.tickCount >= nextLongRangeJumpTick) {
            boolean usingGun = GunSupport.get().isGun(human.getMainHandItem());
            boolean pursuingAtLongRange = !human.isFleeing
                    && (!human.isUsingItem() || (usingGun && GunSupport.get().isGun(human.getUseItem())))
                    && human.distanceToSqr(target) >= (usingGun ? 26.0D * 26.0D : 18.0D * 18.0D)
                    && pathHeadsToward(target)
                    && movingToward(target);
            boolean retreatingAlongPath = human.isFleeing
                    && !human.isUsingItem()
                    && pathHeadsAwayFrom(target)
                    && movingAwayFrom(target);
            if (pursuingAtLongRange || retreatingAlongPath) {
                human.getJumpControl().jump();
                scheduleLongRangeJump();
            } else {
                // Recheck soon after a turn or a temporary stop. Missing one
                // eligible tick should not suppress jumping for another minute.
                nextLongRangeJumpTick = human.tickCount + 8;
            }
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
        Vec3 next = Vec3.atCenterOf(path.getNextNodePos());
        return path.getNextNodePos().getY() > Mth.floor(human.getY() + 0.1D)
                && human.distanceToSqr(next) <= 12.25D;
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

    private void scheduleLongRangeJump() {
        nextLongRangeJumpTick = human.tickCount + 35 + human.getRandom().nextInt(26);
    }

}
