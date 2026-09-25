package club.someoneice.humangunner;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;

/** Shared distance and trajectory for hand-held and inventory splash potions. */
public final class PotionThrowing {
    public static final double MIN_DISTANCE_SQR = 4.0D * 4.0D;
    public static final double MAX_DISTANCE_SQR = 12.0D * 12.0D;
    private static final float THROW_SPEED = 1.35F;

    private PotionThrowing() {
    }

    public static boolean inRange(LivingEntity thrower, LivingEntity target) {
        double distanceSqr = thrower.distanceToSqr(target);
        return distanceSqr >= MIN_DISTANCE_SQR && distanceSqr <= MAX_DISTANCE_SQR;
    }

    public static void shoot(ThrownPotion projectile, LivingEntity target) {
        double dx = target.getX() - projectile.getX();
        double dz = target.getZ() - projectile.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double travelTicks = horizontalDistance / THROW_SPEED;
        // Splash potions have appreciable gravity. A distance-dependent lift
        // avoids short throws sailing high while keeping long throws airborne.
        double dy = target.getY(0.45D) - projectile.getY() + 0.032D * travelTicks * travelTicks;
        projectile.shoot(dx, dy, dz, THROW_SPEED, 3.0F);
    }
}
