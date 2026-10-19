package club.someoneice.humangunner;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Players move through packets; server deltaMovement need not equal their observed displacement. */
final class GunTargetMotion {
    private record Sample(long tick, Vec3 position, Vec3 velocity) {}
    private static final Map<LivingEntity, Sample> SAMPLES = new WeakHashMap<>();
    private GunTargetMotion() {}
    static Vec3 velocity(LivingEntity target) {
        Vec3 motion = target.getDeltaMovement();
        if (target instanceof Player) {
            long now = target.level().getGameTime();
            Sample previous = SAMPLES.get(target);
            if (previous != null && previous.tick() == now) return previous.velocity();
            Vec3 observed = target.position().subtract(target.xo, target.yo, target.zo);
            // Sampling across observer ticks also works when Player.tick already reset xo/yo/zo.
            if (previous != null && now > previous.tick() && now - previous.tick() <= 2)
                observed = target.position().subtract(previous.position()).scale(1.0 / (now - previous.tick()));
            // Reject teleports/large packet corrections rather than predicting them for a second.
            motion = sane(observed);
            SAMPLES.put(target, new Sample(now, target.position(), motion));
        }
        return sane(motion);
    }
    private static Vec3 sane(Vec3 motion) {
        return Double.isFinite(motion.lengthSqr()) && motion.lengthSqr() <= 16 ? motion : Vec3.ZERO;
    }
}
