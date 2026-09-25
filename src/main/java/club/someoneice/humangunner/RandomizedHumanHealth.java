package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Assigns every Human stage an individually persistent tier health roll. */
public final class RandomizedHumanHealth {
    private static final String ROLLED_HEALTH = HumanGunner.MOD_ID + ":rolled_max_health";
    private static final String TIER_RANGE_SCHEMA = HumanGunner.MOD_ID + ":tier_health_ranges_v2";

    private RandomizedHumanHealth() {
    }

    public static void applyOnce(Human human) {
        applyAndRepair(human);
    }

    /**
     * Repairs late attribute overwrites made after EntityJoinLevelEvent. C2ME's
     * entity loading path and old saved Hostile Humans can restore their former
     * 50/60-health base after the join callback, so the persistent roll alone
     * is not proof that the live attribute was applied.
     */
    public static void ensureApplied(Human human) {
        AttributeInstance maximumHealth = human.getAttribute(Attributes.MAX_HEALTH);
        if (maximumHealth == null) {
            return;
        }
        int rolled = human.getPersistentData().getInt(ROLLED_HEALTH);
        HealthRange range = rangeFor(human);
        if (!human.getPersistentData().getBoolean(TIER_RANGE_SCHEMA)
                || rolled < range.minimum() || rolled > range.maximum()
                || Math.abs(maximumHealth.getBaseValue() - rolled) > 0.001D) {
            applyAndRepair(human);
        }
    }

    private static void applyAndRepair(Human human) {
        AttributeInstance maximumHealth = human.getAttribute(Attributes.MAX_HEALTH);
        if (maximumHealth == null) {
            return;
        }

        int rolled = human.getPersistentData().getInt(ROLLED_HEALTH);
        HealthRange range = rangeFor(human);
        double previousMaximum = Math.max(1.0D, human.getMaxHealth());
        double currentRatio = Math.max(0.0D, Math.min(1.0D, human.getHealth() / previousMaximum));
        if (!human.getPersistentData().getBoolean(TIER_RANGE_SCHEMA)
                || rolled < range.minimum() || rolled > range.maximum()) {
            rolled = human.getRandom().nextInt(range.minimum(), range.maximum() + 1);
            human.getPersistentData().putInt(ROLLED_HEALTH, rolled);
            human.getPersistentData().putBoolean(TIER_RANGE_SCHEMA, true);
        }

        if (Math.abs(maximumHealth.getBaseValue() - rolled) <= 0.001D) {
            return;
        }
        maximumHealth.setBaseValue(rolled);
        // Preserve the percentage against the resulting value, not just the
        // rolled base, so optional max-health modifiers cannot turn a damaged
        // saved entity into a different health percentage on first migration.
        human.setHealth((float) Math.max(1.0D, human.getMaxHealth() * currentRatio));
    }

    private static HealthRange rangeFor(Human human) {
        var tier = TierAttributes.of(human);
        return new HealthRange(tier.healthMin(), tier.healthMax());
    }

    private record HealthRange(int minimum, int maximum) {
    }
}
