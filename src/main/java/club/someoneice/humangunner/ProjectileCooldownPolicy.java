package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/** Only changes the cooldown gate; the original damage source and damage pipeline remain intact. */
public final class ProjectileCooldownPolicy {
    private ProjectileCooldownPolicy() {}

    public static boolean bypassesCooldown(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || UnifiedConfig.get().vanillaMode()
                || victim instanceof Player || source.is(DamageTypeTags.IS_EXPLOSION)) return false;
        Entity attacker = source.getEntity();
        // Some modded projectiles omit the causing entity. An explicit cause takes precedence
        // over the projectile's owner (for example after a reflected projectile).
        if (attacker == null && source.getDirectEntity() instanceof Projectile projectile)
            attacker = projectile.getOwner();
        boolean humanVictim = victim instanceof Human;
        boolean humanShooter = attacker instanceof Human;
        if (!humanVictim && !humanShooter) return false;
        return allows(false, false, humanVictim, humanShooter,
                source.is(DamageTypeTags.IS_PROJECTILE) || GunSupport.get().isBullet(source), false);
    }

    static boolean allows(boolean vanillaMode, boolean playerVictim, boolean humanVictim,
                          boolean humanShooter, boolean projectileDamage, boolean explosion) {
        return !vanillaMode && !playerVictim && !explosion && projectileDamage
                && (humanVictim || humanShooter);
    }
}
