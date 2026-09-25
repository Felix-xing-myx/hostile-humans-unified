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
        return GunSpreadPolicy.degrees(TierAttributes.of(human).gunSpreadDegrees(),
                GunSupport.get().gunType(weapon));
    }

    public static double projectileSpreadDegrees(Human human, boolean crossbow) {
        return projectileSpreadDegrees(TierAttributes.of(human).projectileSpreadDegrees(), crossbow);
    }

    static double projectileSpreadDegrees(double bowSpread, boolean crossbow) {
        return Math.max(0.0D, bowSpread - (crossbow ? 0.5D : 0.0D));
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
