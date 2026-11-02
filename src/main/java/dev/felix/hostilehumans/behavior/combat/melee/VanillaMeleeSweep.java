package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.perception.HumanPerception;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Vanilla sword sweep geometry/damage, consumed only by Human's authoritative successful strike. */
public final class VanillaMeleeSweep {
    private static final UUID DAMAGE = UUID.fromString("fb7ba6d9-df53-4779-817d-aec926bf764d");
    private VanillaMeleeSweep() { }
    public static List<LivingEntity> targets(Human human, LivingEntity primary) {
        if (human.isFleeing || !human.hhu$canMeleeAttack() || !human.onGround() || human.isSprinting()
                || !(human.getMainHandItem().getItem() instanceof SwordItem)
                || BetterCombatMeleeCombat.hasWeaponProfile(human)) return List.of();
        return human.level().getEntitiesOfClass(LivingEntity.class, primary.getBoundingBox().inflate(1, .25, 1),
                target -> target != human && target != primary && target.isAlive() && human.canAttack(target)
                        && HumanPerception.canTarget(human, target) && human.distanceToSqr(target) < 9
                        && human.getSensing().hasLineOfSight(target) && MeleeCombatRange.canReachTarget(human, target));
    }
    public static boolean strike(Human human, BooleanSupplier actualStrike) {
        var damage = human.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null || !(damage.getValue() > 0)) return false;
        double base = damage.getValue();
        double total = 1 + EnchantmentHelper.getSweepingDamageRatio(human) * base;
        damage.addTransientModifier(new AttributeModifier(DAMAGE, "Vanilla secondary sweep", total / base - 1,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        try { return actualStrike.getAsBoolean(); }
        finally { damage.removeModifier(DAMAGE); }
    }
}
