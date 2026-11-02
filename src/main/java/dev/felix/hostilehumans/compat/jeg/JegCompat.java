package dev.felix.hostilehumans.compat.jeg;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.fml.ModList;

/** Core boundary contains no JEG classes or descriptors. */
public final class JegCompat {
    private JegCompat() {}
    public static boolean enabled() { return ModList.get().isLoaded("jeg") && UnifiedConfig.get().scguns().enabled(); }
    /** Optional native projectiles may extend Entity rather than vanilla Projectile. */
    public static net.minecraft.world.entity.LivingEntity projectileOwner(net.minecraft.world.entity.Entity direct) {
        return ModList.get().isLoaded("jeg") ? JegIntegration.projectileOwner(direct) : null;
    }
    public static boolean isHumanBullet(DamageSource source) {
        return enabled() && source.getEntity() instanceof Human && JegIntegration.isNativeBullet(source);
    }
}
