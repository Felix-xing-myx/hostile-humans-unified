package dev.felix.hostilehumans.damage;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import dev.felix.hostilehumans.behavior.combat.defense.GunnerShieldProtection;
import dev.felix.hostilehumans.behavior.combat.defense.ShieldDurability;
import dev.felix.hostilehumans.behavior.combat.gun.GunSpecialization;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.progression.EffectiveAttributes;
import dev.felix.hostilehumans.progression.HumanGrowth;

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
    private record HeavyHit(long tick, java.util.Map<Integer, Boolean> rolls, java.util.Set<Integer> announced) { }
    private static final java.util.Map<LivingEntity, HeavyHit> HEAVY_HITS = new java.util.WeakHashMap<>();
    private HumanDamagePipeline() { }

    /** One roll per contact, shared by the native normal and piercing segments. */
    public static float factionAttackDamage(Human attacker, LivingEntity target, DamageSource source, float amount) {
        if (attacker.level().isClientSide || !(amount > 0) || source.getDirectEntity() == null || !humanAttack(source)
                || !HumanCombatDirector.strategies(attacker).heavyStrike()) return amount;
        long tick = attacker.level().getGameTime();
        HeavyHit hit = HEAVY_HITS.get(target);
        if (hit == null || hit.tick() != tick) {
            hit = new HeavyHit(tick, new java.util.HashMap<>(), new java.util.HashSet<>());
            HEAVY_HITS.put(target, hit);
        }
        return hit.rolls().computeIfAbsent(source.getDirectEntity().getId(), ignored -> attacker.getRandom().nextFloat() < .25F)
                ? amount * 1.35F : amount;
    }

    /** Called for damage that reaches final settlement, after the normal shield/armor path. */
    public static void factionAttackEffects(Human attacker, LivingEntity target, DamageSource source) {
        if (attacker.level().isClientSide || source.getDirectEntity() == null || !humanAttack(source)) return;
        if (source.getDirectEntity() == attacker && HumanCombatDirector.mobileCloseDefense(attacker)
                && ((DamagePipelineState.Access) attacker).hostileHumansUnified$damageState()
                        .tryStartMobileWeakness(attacker.level().getGameTime())) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60), attacker);
        }
        HeavyHit hit = HEAVY_HITS.get(target);
        int direct = source.getDirectEntity().getId();
        if (hit != null && hit.tick() == attacker.level().getGameTime()
                && Boolean.TRUE.equals(hit.rolls().get(direct)) && hit.announced().add(direct)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), attacker);
            target.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1, 1);
        }
    }

    /** Record exact sources only after an optional backend has really applied its active-shield reduction. */
    public static void markCustomShield(LivingEntity target, DamageSource normal, DamageSource piercing, float normalMultiplier) {
        ((DamagePipelineState.Access) target).hostileHumansUnified$damageState()
                .markCustomShield(normal, piercing, target.tickCount, normalMultiplier);
    }

    public static float beginDamage(LivingEntity target, DamageSource source, float amount, DamagePipelineState state) {
        state.begin();
        float normalMultiplier = state.beginCustomShield(source, target.tickCount);
        if (!state.customBlock()) return amount;
        double fraction = piercingFraction(source);
        double remaining = normalMultiplier * (1 - fraction) + .5 * fraction;
        if (remaining > 0) state.record(source, target.tickCount, .5 * fraction / remaining);
        return (float) (amount * remaining);
    }

    public static float scaleGunDamage(Human attacker, ItemStack weapon, float amount, double backendMultiplier) {
        return HumanGrowth.scaleGunDamage(attacker, amount, backendMultiplier)
                * (float) (weapon.isEmpty() ? 1 : GunSpecialization.damageMultiplier(attacker, weapon));
    }

    public static boolean physical(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING)
                || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH)
                || source.is(DamageTypes.THORNS)) return false;
        return nativeBullet(source) || source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.getDirectEntity() instanceof Projectile
                || source.getDirectEntity() instanceof LivingEntity;
    }

    public static boolean nativeBullet(DamageSource source) {
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
                .customBlock()) {
            // The native backend already applied the configured percentage and durability once.
            event.setBlockedDamage(0);
            event.setShieldTakesDamage(false);
            return event;
        }
        if (!(event.getBlockedDamage() > 0)) return event;
        double fraction = piercingFraction(source);
        float blocked = event.getBlockedDamage();
        if (fraction > 0) {
            double piercing = amount * fraction;
            double piercingBlocked = target instanceof Human ? piercing * .5 : 0;
            blocked = (float) (Math.min(blocked, amount - piercing) + piercingBlocked);
            event.setBlockedDamage(blocked);
            ((DamagePipelineState.Access) target).hostileHumansUnified$damageState().record(source,
                    target.tickCount, DamagePartition.afterBlockFraction(amount, fraction, blocked, piercingBlocked));
        }
        if (blocked > 0 && target instanceof Human human)
            human.hhu$shieldDefense().observeAttack(source, true, blocked);
        return event;
    }

    /** Runs after LivingHurt scaling and before protection, resistance, absorption and health loss. */
    public static float armor(LivingEntity target, DamageSource source, float amount, DoubleUnaryOperator nativeArmor) {
        if (target.level().isClientSide || !Float.isFinite(amount) || amount <= 0)
            return (float) nativeArmor.applyAsDouble(amount);
        DamagePipelineState state = ((DamagePipelineState.Access) target).hostileHumansUnified$damageState();
        // Consume before invoking foreign/native armor callbacks: recursive hurt cannot steal this receipt.
        double fraction = state.consume(source, target.tickCount, piercingFraction(source));
        var backShield = target instanceof Human human && fraction < 1
                ? GunnerShieldProtection.backShield(human, source) : null;
        double passive = backShield != null ? GunnerShieldProtection.BACKPACK_MULTIPLIER
                : GunnerShieldProtection.multiplier(target, source);
        try {
            float result = DamagePartition.armor(amount, fraction, passive,
                    UnifiedConfig.get().damageFlag("passive_shield_before_armor", true), nativeArmor);
            if (passive < 1 && target instanceof Human human && fraction < 1) {
                float normal = (float) (amount * (1 - fraction));
                if (backShield != null) ShieldDurability.damageBackShield(human, backShield, normal);
                else ShieldDurability.damagePassiveOffhandShield(human, normal);
            }
            return result;
        } finally {
            state.clear();
        }
    }
}
