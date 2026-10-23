package club.someoneice.humangunner;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** One cooldown-query entry; never schedules attacks or modifies health/shared hurt timers. */
public final class HumanAttackCooldownPolicy {
    private HumanAttackCooldownPolicy() { }

    public static boolean bypassesCooldown(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || UnifiedConfig.get().vanillaMode()) return false;
        if (HumanMeleeDamage.isDirectAttack(source)) return true;
        // Existing projectile rules retain their distinct victim/player scope.
        return ProjectileCooldownPolicy.bypassesCooldown(victim, source);
    }

}
