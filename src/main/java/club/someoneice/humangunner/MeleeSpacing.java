package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A short, path-checked spacing correction; never owns fleeing or water combat. */
public final class MeleeSpacing {
    private MeleeSpacing() {}

    /** Returns true when ordinary pursuit should yield to melee spacing. */
    public static boolean control(Human human, LivingEntity target) {
        if (human.isFleeing || SoldierOrder.isHoldingPosition(human)
                || human.isInWater() || human.isInLava() || human.isUsingItem()
                || !human.getSensing().hasLineOfSight(target)) return false;

        double reach = MeleeCombatRange.reach(human, target);
        double distance = human.distanceTo(target);
        double preferred = Math.max(1.35D, reach * 0.82D);
        double tooClose = Math.min(preferred - 0.35D, Math.max(1.0D, reach * 0.55D));
        if (distance > preferred) return false;
        if (distance >= tooClose) {
            human.getNavigation().stop();
            return true;
        }
        Vec3 away = human.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        human.getNavigation().stop();
        if (away.lengthSqr() < 1.0E-4D) {
            return true;
        }
        // Check the next step, not a path behind the mob: a retreat path would
        // rotate the whole body away from its opponent. STRAFE keeps the torso
        // and the ordinary walking legs facing the fight while stepping back.
        Vec3 step = away.normalize().scale(0.8D);
        BlockPos floor = BlockPos.containing(human.position().add(step)).below();
        if (human.level().getBlockState(floor).isFaceSturdy(human.level(), floor, Direction.UP)
                && human.level().getFluidState(floor.above()).isEmpty()
                && human.level().noCollision(human, human.getBoundingBox().move(step))) {
            human.getMoveControl().strafe(-0.6F, 0.0F);
            human.markRangedFacing(target);
        }
        return true;
    }
}
