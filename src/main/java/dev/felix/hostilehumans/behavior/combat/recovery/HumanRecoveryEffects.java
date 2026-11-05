package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.progression.EffectiveAttributes;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Human-only vanilla effect replacement. Direct medical healing remains independently owned. */
public final class HumanRecoveryEffects {
    private HumanRecoveryEffects() {}
    private static boolean humanServer(LivingEntity target) {
        return target instanceof Human && !target.level().isClientSide && target.isAlive();
    }
    public static boolean replaceTick(MobEffect effect, LivingEntity target, int amplifier) {
        if (!humanServer(target)) return false;
        // RecoverySupplies owns total regeneration on its own clock; never add a native pulse.
        if (effect == MobEffects.REGENERATION) return true;
        return replaceInstant(effect, target, amplifier, 1.0D);
    }
    static float instantHealingAmount(Human human, int amplifier, double exposure) {
        return PotionRecoveryPolicy.instantHealingAmount(amplifier, human.getMaxHealth(), exposure,
                EffectiveAttributes.regenerationMultiplier(human));
    }

    public static boolean replaceInstant(MobEffect effect, LivingEntity target, int amplifier, double exposure) {
        if (!humanServer(target) || effect != MobEffects.HEAL || target.isInvertedHealAndHarm()) return false;
        Human human = (Human) target;
        float amount = instantHealingAmount(human, amplifier, exposure);
        if (amount > 0) human.hhu$healResolvedAmount(amount);
        return true;
    }
}
