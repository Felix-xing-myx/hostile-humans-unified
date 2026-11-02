package dev.felix.hostilehumans.behavior.combat.defense;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.Vec3;

/** Optional gun integrations register only projectile types with a verified physical model.
 * Unknown/hitscan entities never acquire fictitious arrow gravity. */
public final class ShieldProjectileBackend {
    public record Motion(double drag, double gravity, boolean shieldable, Vec3 acceleration, int horizon) {
        public Motion(double drag, double gravity, boolean shieldable) {
            this(drag, gravity, shieldable, Vec3.ZERO, ProjectileShieldPolicy.MAX_PREDICTION_TICKS);
        }
    }
    @FunctionalInterface public interface Adapter { Motion describe(Projectile projectile); }
    private static final List<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private ShieldProjectileBackend() { }
    public static void register(Adapter adapter) { ADAPTERS.add(java.util.Objects.requireNonNull(adapter)); }
    static Motion describe(Projectile projectile) {
        for (Adapter adapter : ADAPTERS) {
            Motion result = adapter.describe(projectile);
            if (result != null) return result;
        }
        if (projectile instanceof AbstractArrow) return new Motion(projectile.isInWater() && !(projectile instanceof ThrownTrident) ? .6 : .99,
                projectile.isNoGravity() ? 0 : .05, true);
        if (projectile instanceof LlamaSpit) return new Motion(.99, projectile.isNoGravity() ? 0 : .06, true);
        if (projectile instanceof AbstractHurtingProjectile fireball) return new Motion(
                fireball.isInWater() ? .8 : .95, 0, true,
                new Vec3(fireball.xPower, fireball.yPower, fireball.zPower), 12);
        // A homing bullet's current tick is predictable; later steering is not observed yet.
        if (projectile instanceof ShulkerBullet) return new Motion(1, 0, true, Vec3.ZERO, 1);
        // Harmful splash potions are visible threats, but bypass shields: do not promise protection.
        return null;
    }
}
