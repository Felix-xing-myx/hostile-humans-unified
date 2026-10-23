package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Keep one walking base speed and let vanilla sprint and effect modifiers compose normally. */
public final class MovementSpeedController {
    private static final UUID SPRINT_COMPENSATION_ID =
            UUID.fromString("82624020-1000-4000-8000-000000000302");

    public enum Limit { RANGED, FOOD, DEFENSE }
    private record Restriction(double multiplier, long expires) { }
    private static final java.util.Map<Human, java.util.EnumMap<Limit, Restriction>> LIMITS = new java.util.WeakHashMap<>();
    private static final UUID LIMIT_ID = UUID.fromString("82624020-1000-4000-8000-000000000303");

    /** Same source replaces its own restriction; independent admitted actions multiply once. */
    public static void limit(Human human, Limit source, double multiplier, boolean active) {
        if (human.level().isClientSide) return;
        var limits = LIMITS.computeIfAbsent(human, ignored -> new java.util.EnumMap<>(Limit.class));
        if (active && Double.isFinite(multiplier) && multiplier > 0 && multiplier != 1
                && (multiplier < 1 || source == Limit.RANGED))
            limits.put(source, new Restriction(multiplier, human.level().getGameTime() + 2));
        else limits.remove(source);
        publish(human, limits);
    }
    public static void tickLimits(Human human) {
        if (human.level().isClientSide) return;
        var limits = LIMITS.get(human);
        if (limits == null) return;
        long now = human.level().getGameTime();
        limits.values().removeIf(value -> value.expires() < now);
        publish(human, limits);
    }
    public static void clearLimits(Human human) {
        LIMITS.remove(human);
        AttributeInstance speed = human.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(LIMIT_ID);
    }
    private static void publish(Human human, java.util.EnumMap<Limit, Restriction> limits) {
        double product = 1;
        for (Restriction limit : limits.values()) product *= limit.multiplier();
        AttributeInstance speed = human.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            var old = speed.getModifier(LIMIT_ID);
            if (old != null && Math.abs(old.getAmount() - (product - 1)) < 1e-8) return;
            if (old != null) speed.removeModifier(LIMIT_ID);
            if (Math.abs(product - 1) > 1e-8) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    LIMIT_ID, "Human admitted action speed factors", product - 1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (limits.isEmpty()) LIMITS.remove(human);
    }

    private MovementSpeedController() {
    }

    public static void normal(Human human) {
        apply(human, EffectiveAttributes.speedBase(human), false);
    }

    public static void combat(Human human, boolean sprinting) {
        apply(human, EffectiveAttributes.speedBase(human), sprinting);
    }

    public static void retreat(Human human, boolean sprinting) {
        apply(human, EffectiveAttributes.speedBase(human), sprinting);
    }

    private static void apply(Human human, double absoluteSpeed, boolean sprinting) {
        AttributeInstance movement = human.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            human.setSprinting(sprinting);
            return;
        }
        if (Math.abs(movement.getBaseValue() - absoluteSpeed) > 1.0E-6D) {
            movement.setBaseValue(absoluteSpeed);
        }
        // Remove the legacy cancellation from already-saved entities before
        // toggling sprint; otherwise they retain a hidden -30% modifier.
        if (movement.getModifier(SPRINT_COMPENSATION_ID) != null) {
            movement.removeModifier(SPRINT_COMPENSATION_ID);
        }
        human.setSprinting(sprinting);
    }
}
