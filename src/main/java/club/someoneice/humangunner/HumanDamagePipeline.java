package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import java.util.function.DoubleUnaryOperator;

/** The shared damage seam for every living recipient, independent of optional gun classes. */
public final class HumanDamagePipeline {
    private static final TagKey<DamageType> TACZ_BULLETS = TagKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("tacz", "bullets"));
    private static final ResourceKey<DamageType> SCGUNS_BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("scguns", "bullet"));
    private static final ResourceKey<DamageType> JEG_BULLET = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation("jeg", "bullet"));
    private HumanDamagePipeline() { }

    /** Record exact sources only after an optional backend has really applied its active-shield reduction. */
    public static void markCustomShield(LivingEntity target, DamageSource normal, DamageSource piercing) {
        ((DamagePipelineState.Access) target).hostileHumansUnified$damageState()
                .markCustomShield(normal, piercing, target.tickCount);
    }

    public static float scaleGunDamage(Human attacker, ItemStack weapon, float amount, double backendMultiplier) {
        return HumanGrowth.scaleGunDamage(attacker, amount, backendMultiplier)
                * (float) (weapon.isEmpty() ? 1 : GunSpecialization.damageMultiplier(attacker, weapon));
    }

    static boolean physical(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH)
                || source.is(DamageTypes.THORNS)) return false;
        return nativeBullet(source) || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.getDirectEntity() instanceof Projectile
                || source.getDirectEntity() instanceof LivingEntity;
    }

    private static boolean nativeBullet(DamageSource source) {
        // SC/JEG projectiles extend Entity, not vanilla Projectile, and ship no projectile tag.
        return source.is(TACZ_BULLETS) || source.is(SCGUNS_BULLET) || source.is(JEG_BULLET);
    }

    static double piercingFraction(DamageSource source) {
        // Existing native AP is already converted; never apply human AP to it again.
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return 1;
        if (!(source.getEntity() instanceof Human attacker) || !humanAttack(source)
                || !UnifiedConfig.get().damageFlag("armor_penetration_enabled", true)) return 0;
        return DamagePartition.fraction(EffectiveAttributes.armorPenetration(attacker),
                UnifiedConfig.get().damage("armor_penetration_curve", 1));
    }

    private static boolean humanAttack(DamageSource source) {
        // Environmental/DoT attribution does not opt into the new conversion.
        return source.getEntity() instanceof Human attacker && physical(source)
                && (source.getDirectEntity() == attacker || source.getDirectEntity() instanceof Projectile
                || source.is(DamageTypeTags.IS_PROJECTILE) || nativeBullet(source));
    }

    /** Called around Forge's existing shield hook, after other listeners have decided their block. */
    public static ShieldBlockEvent shieldBlock(LivingEntity target, DamageSource source, float amount,
                                                ShieldBlockEvent event) {
        if (target.level().isClientSide || event.isCanceled() || !(amount > 0)) return event;
        if (target instanceof Human && ((DamagePipelineState.Access) target).hostileHumansUnified$damageState()
                .consumeCustomShield(source, target.tickCount)) {
            // The native backend already applied the configured percentage and durability once.
            event.setBlockedDamage(0);
            event.setShieldTakesDamage(false);
            return event;
        }
        if (!humanAttack(source)) return event;
        double fraction = piercingFraction(source);
        if (!(fraction > 0)) return event;
        float blocked = Math.min(event.getBlockedDamage(), (float) (amount * (1 - fraction)));
        event.setBlockedDamage(blocked);
        ((DamagePipelineState.Access) target).hostileHumansUnified$damageState().record(source,
                target.tickCount, DamagePartition.afterBlockFraction(amount, fraction, blocked));
        return event;
    }

    /** Runs after LivingHurt scaling and before protection, resistance, absorption and health loss. */
    public static float armor(LivingEntity target, DamageSource source, float amount, DoubleUnaryOperator nativeArmor) {
        if (target.level().isClientSide || !Float.isFinite(amount) || amount <= 0)
            return (float) nativeArmor.applyAsDouble(amount);
        DamagePipelineState state = ((DamagePipelineState.Access) target).hostileHumansUnified$damageState();
        // Consume before invoking foreign/native armor callbacks: recursive hurt cannot steal this receipt.
        double fraction = state.consume(source, target.tickCount, piercingFraction(source));
        double passive = GunnerShieldProtection.multiplier(target, source);
        try {
            float result = DamagePartition.armor(amount, fraction, passive,
                    UnifiedConfig.get().damageFlag("passive_shield_before_armor", true), nativeArmor);
            if (passive < 1 && target instanceof Human human && fraction < 1)
                ShieldDurability.damagePassiveOffhandShield(human, (float) (amount * (1 - fraction)));
            return result;
        } finally {
            state.clear();
        }
    }
}
