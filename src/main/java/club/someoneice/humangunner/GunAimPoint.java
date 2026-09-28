package club.someoneice.humangunner;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lower high gun aim points without raising the aim on low-eyed targets. */
final class GunAimPoint {
    private GunAimPoint() {}

    static Vec3 forTarget(LivingEntity target) {
        AABB bounds = target.getBoundingBox();
        Vec3 eyes = target.getEyePosition();
        return new Vec3(eyes.x, height(bounds.minY, bounds.maxY, eyes.y), eyes.z);
    }

    static double height(double bottom, double top, double eyeY) {
        return Math.min(eyeY, bottom + (top - bottom) * 0.70D);
    }
}
