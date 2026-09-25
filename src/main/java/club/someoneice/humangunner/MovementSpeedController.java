package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Keep one walking base speed and let vanilla sprint and effect modifiers compose normally. */
public final class MovementSpeedController {
    // Human#setSpeed supplies full movement input instead of Mob's attribute-
    // scaled input. Slightly below the player's 0.1 baseline, so unbuffed
    // walking and sprinting both remain a little slower than a player.
    private static final double HUMAN_WALK_SPEED = 0.09D;
    private static final UUID SPRINT_COMPENSATION_ID =
            UUID.fromString("82624020-1000-4000-8000-000000000302");

    private MovementSpeedController() {
    }

    public static void normal(Human human) {
        apply(human, HUMAN_WALK_SPEED, false);
    }

    public static void combat(Human human, boolean sprinting) {
        apply(human, HUMAN_WALK_SPEED, sprinting);
    }

    public static void retreat(Human human, boolean sprinting) {
        apply(human, HUMAN_WALK_SPEED, sprinting);
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
