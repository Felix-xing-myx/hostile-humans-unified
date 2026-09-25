package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Keep one walking base speed and let vanilla sprint and effect modifiers compose normally. */
public final class MovementSpeedController {
    private static final UUID SPRINT_COMPENSATION_ID =
            UUID.fromString("82624020-1000-4000-8000-000000000302");

    private MovementSpeedController() {
    }

    public static void normal(Human human) {
        apply(human, TierAttributes.of(human).baseMovementSpeed(), false);
    }

    public static void combat(Human human, boolean sprinting) {
        apply(human, TierAttributes.of(human).baseMovementSpeed(), sprinting);
    }

    public static void retreat(Human human, boolean sprinting) {
        apply(human, TierAttributes.of(human).baseMovementSpeed(), sprinting);
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
