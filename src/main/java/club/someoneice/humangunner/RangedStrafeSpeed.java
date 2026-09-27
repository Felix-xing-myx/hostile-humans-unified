package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/** Weapon-specific temporary movement boost while firing and strafing. */
public final class RangedStrafeSpeed {
    private static final UUID MODIFIER_ID =
            UUID.fromString("80e8306b-15a5-49de-9771-b4dd99bc0862");
    // The previous bow multiplier was 1.75. Reducing the complete strafing
    // speed by 30% gives 1.75 * 0.70 = 1.225, not a 45% attribute bonus.
    private static final double BOW_BONUS = 0.225D;
    private static final double CROSSBOW_BONUS = 0.05D;

    private RangedStrafeSpeed() {
    }

    public static void setBow(Human human, boolean orbiting) {
        set(human, orbiting, BOW_BONUS);
    }

    public static void setCrossbow(Human human, boolean orbiting) {
        set(human, orbiting, CROSSBOW_BONUS);
    }

    private static void set(Human human, boolean orbiting, double bonus) {
        AttributeInstance speed = human.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        AttributeModifier current = speed.getModifier(MODIFIER_ID);
        if (orbiting) {
            if (current == null || current.getAmount() != bonus) {
                if (current != null) speed.removeModifier(MODIFIER_ID);
                speed.addTransientModifier(new AttributeModifier(MODIFIER_ID,
                        "Ranged firing strafe", bonus,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else if (current != null) {
            speed.removeModifier(MODIFIER_ID);
        }
    }
}
