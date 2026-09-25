package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Tier-aware angular spread for firearms and ballistic projectiles. */
public final class RangedAccuracy {
    private RangedAccuracy() {}

    public static double gunSpreadDegrees(Human human, ItemStack weapon) {
        return TierAttributes.of(human).gunSpreadDegrees(GunSupport.get().gunType(weapon));
    }

    public static double projectileSpreadDegrees(Human human, boolean crossbow) {
        return projectileSpreadDegrees(human, crossbow, false);
    }

    public static double projectileSpreadDegrees(Human human, boolean crossbow, boolean trident) {
        return TierAttributes.of(human).projectileSpreadDegrees(
                trident ? "trident" : crossbow ? "crossbow" : "bow");
    }

    public static Vec3 applyAngularSpread(Vec3 direction, double degrees, RandomSource random) {
        if (degrees <= 0.0D || direction.lengthSqr() < 1.0E-12D) return direction;
        double horizontal = Math.hypot(direction.x, direction.z);
        double yaw = Math.atan2(direction.z, direction.x) - Math.PI / 2.0D;
        double pitch = -Math.atan2(direction.y, horizontal);
        double limit = degrees * 2.5D;
        yaw += Math.toRadians(Mth.clamp(random.nextGaussian() * degrees, -limit, limit));
        pitch += Math.toRadians(Mth.clamp(random.nextGaussian() * degrees, -limit, limit));
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch),
                Math.cos(yaw) * cosPitch);
    }
}
