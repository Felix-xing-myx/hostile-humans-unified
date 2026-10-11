package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

/** One cooldown-query entry; never schedules attacks or modifies health/shared hurt timers. */
public final class HumanAttackCooldownPolicy {
    private HumanAttackCooldownPolicy() { }

    public static boolean bypassesCooldown(LivingEntity victim, DamageSource source) {
        if (victim.level().isClientSide || UnifiedConfig.get().vanillaMode()) return false;
        boolean humanAttacker = source.getEntity() instanceof Human;
        boolean direct = humanAttacker && source.getDirectEntity() == source.getEntity();
        boolean meleeType = source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
        if (humanAttacker && direct && meleeType && allowsMelee(false, humanAttacker, direct, meleeType,
                source.is(DamageTypeTags.IS_PROJECTILE) || GunSupport.get().isBullet(source),
                source.is(DamageTypeTags.IS_EXPLOSION))) return true;
        // Existing projectile rules retain their distinct victim/player scope.
        return ProjectileCooldownPolicy.bypassesCooldown(victim, source);
    }

    static boolean allowsMelee(boolean vanillaMode, boolean humanAttacker, boolean direct,
                                boolean meleeType, boolean projectile, boolean explosion) {
        return !vanillaMode && humanAttacker && direct && meleeType && !projectile && !explosion;
    }
}
