package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.TridentItem;

/**
 * Rank/growth scaling at the LivingHurt phase, after relationship authorization.
 * No health writes, hit scheduling or shield mitigation here. TaCZ owns its gun
 * scaling; PursuitJumpAttack owns the temporary melee critical attribute.
 */
final class HumanDamageScaling {
    // Classification remains valid when NPC firearm support is disabled.
    private static final TagKey<DamageType> TACZ_BULLET_DAMAGE =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullets"));

    private HumanDamageScaling() { }

    static float incoming(Human defender, float amount) {
        return amount * (float) TierAttributes.of(defender).incomingDamage();
    }

    static float outgoing(Human attacker, DamageSource source, float amount) {
        // Some gun packs report the shooter itself as the direct entity.
        // Classify bullets before melee, including when TaCZ NPC AI is disabled.
        if (source.is(TACZ_BULLET_DAMAGE) || GunSupport.get().isBullet(source)) return amount;
        float multiplier;
        if (source.getDirectEntity() == attacker) {
            if (UnifiedConfig.get().vanillaMode()) return amount;
            multiplier = (float) TierAttributes.of(attacker).meleeDamage();
            if (attacker.getMainHandItem().getItem() instanceof TridentItem)
                multiplier *= (float) UnifiedConfig.get().damage("trident_stab_damage_multiplier", .42);
        } else if (source.getDirectEntity() instanceof ThrownTrident) {
            multiplier = (float) TierAttributes.of(attacker).tridentDamage();
        } else if (source.getDirectEntity() instanceof AbstractArrow) {
            multiplier = (float) TierAttributes.of(attacker).bowDamage();
        } else {
            return amount;
        }
        return amount * multiplier * (float) HumanGrowth.multiplier(attacker);
    }
}
