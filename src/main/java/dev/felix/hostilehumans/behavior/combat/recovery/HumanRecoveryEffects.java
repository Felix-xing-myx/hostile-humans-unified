package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Human-only vanilla effect replacement. Direct medical healing remains independently owned. */
public final class HumanRecoveryEffects {
    private HumanRecoveryEffects() {}
    private static boolean humanServer(LivingEntity target) {
        return target instanceof Human && !target.level().isClientSide && target.isAlive();
    }
    public static boolean replaceTick(MobEffect effect, LivingEntity target) {
        if (!humanServer(target)) return false;
        // RecoverySupplies owns total regeneration on its own clock; never add a native pulse.
        if (effect == MobEffects.REGENERATION) return true;
        return replaceInstant(effect, target, 1.0D);
    }
    public static boolean replaceInstant(MobEffect effect, LivingEntity target, double exposure) {
        if (!humanServer(target) || effect != MobEffects.HEAL || target.isInvertedHealAndHarm()) return false;
        int amount = PotionRecoveryPolicy.instantHealingAmount(exposure);
        if (amount > 0) target.heal(amount);
        return true;
    }
}
