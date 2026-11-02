package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.entity.Human;
import java.lang.reflect.Field;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.util.UUID;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Read-only physical parameters at the same boundary as the authoritative position. */
final class MonitorPhysicsSnapshot {
    private static final Field JUMP_COOLDOWN = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20954_");
    private MonitorPhysicsSnapshot() {}
    static int jumpCooldown(Human h) {
        try { return JUMP_COOLDOWN.getInt(h); }
        catch (IllegalAccessException e) { throw new IllegalStateException("Cannot read native jump cooldown", e); }
    }
    private static int amplifier(Human h, MobEffect effect) { var e = h.getEffect(effect); return e == null ? -1 : e.getAmplifier(); }
    // Vanilla 1.20.1 transient modifiers, removed only on a detached attribute copy.
    private static final UUID SLOW_FALL = UUID.fromString("A5B6CF2A-2F7C-31EF-9022-7C3E7D5E6ABA");
    private static double without(Human h, Attribute attribute, UUID modifier) {
        var copy = new AttributeInstance(attribute, ignored -> {});
        copy.replaceFrom(h.getAttribute(attribute));
        copy.removeModifier(modifier);
        return copy.getValue();
    }
    static MonitorMotionSnapshot.Physics capture(Human h, float travelSpeed) {
        // MOVEMENT_SPEED can already contain next-tick native gun modifiers at publication time.
        // This value is the speed handed to native travel. Remove only vanilla sprint so the
        // detached prediction body can reapply sprint intent exactly once.
        float walkingSpeed = MonitorTravelSpeed.walking(travelSpeed, h.isSprinting());
        return new MonitorMotionSnapshot.Physics(walkingSpeed, h.isSprinting(),
                h.getBbWidth(), h.getBbHeight(), h.getEyeHeight(), h.getStepHeight(),
                without(h, ForgeMod.ENTITY_GRAVITY.get(), SLOW_FALL), h.getAttributeValue(ForgeMod.SWIM_SPEED.get()),
                h.isNoGravity(), amplifier(h, MobEffects.JUMP), amplifier(h, MobEffects.LEVITATION),
                h.hasEffect(MobEffects.SLOW_FALLING), h.hasEffect(MobEffects.DOLPHINS_GRACE), EnchantmentHelper.getDepthStrider(h),
                !h.noPhysics && !h.isFallFlying() && !h.isPassenger() && !h.isSleeping());
    }
}
