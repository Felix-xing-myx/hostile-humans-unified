package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.world.entity.projectile.AbstractArrow;

/** The Human's actual server view supplies direction; tier precision remains independent of AI target leading. */
public final class ManualArrowBallistics {
    private ManualArrowBallistics() {}
    public static void aim(Human human, AbstractArrow arrow, float speed, boolean crossbow) {
        var direction = RangedAccuracy.applyAngularSpread(human.getViewVector(1),
                RangedAccuracy.projectileSpreadDegrees(human, crossbow), human.getRandom());
        arrow.shoot(direction.x, direction.y, direction.z, speed, 0);
        arrow.setOwner(human);
        arrow.getPersistentData().putBoolean(HumanRuntime.ADAPTIVE_BALLISTICS_APPLIED, true);
    }
}
