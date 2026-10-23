package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

/** Shared identity of a direct Human melee hit, independent of its weapon or victim.
 * Keep the source producer, damage-cooldown rule and forbidden-attack guard in agreement. */
public final class HumanMeleeDamage {
    public static final ResourceKey<DamageType> HUMAN = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("hostile_humans_unified", "human"));
    public static final ResourceKey<DamageType> NAMED_HUMAN = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("hostile_humans_unified", "human_with_name"));
    private HumanMeleeDamage() { }

    public static boolean isDirectAttack(DamageSource source) {
        boolean human = source.getEntity() instanceof Human;
        if (!human || source.getDirectEntity() != source.getEntity()) return false;
        return allows(human, true, isMeleeType(source.typeHolder().unwrapKey().orElse(null)),
                source.is(DamageTypeTags.IS_PROJECTILE) || GunSupport.get().isBullet(source),
                source.is(DamageTypeTags.IS_EXPLOSION));
    }

    static boolean isMeleeType(ResourceKey<DamageType> type) {
        return DamageTypes.MOB_ATTACK.equals(type) || DamageTypes.MOB_ATTACK_NO_AGGRO.equals(type)
                || DamageTypes.PLAYER_ATTACK.equals(type) || HUMAN.equals(type) || NAMED_HUMAN.equals(type);
    }

    static boolean allows(boolean human, boolean direct, boolean meleeType, boolean projectile, boolean explosion) {
        return human && direct && meleeType && !projectile && !explosion;
    }
}
