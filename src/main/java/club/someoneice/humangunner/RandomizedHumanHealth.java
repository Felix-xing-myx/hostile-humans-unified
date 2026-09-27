package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
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
     * Repairs a late restore of the original 50/60-health base, but does not
     * overwrite a different base supplied by another mod. Health decisions
     * read getMaxHealth(), so an external base change must remain effective.
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
                || (Math.abs(maximumHealth.getBaseValue() - rolled) > 0.001D
                && MaxHealthCompatibilityPolicy.shouldApplyRoll(
                        maximumHealth.getBaseValue(), rolled, originalBase(human)))) {
            applyAndRepair(human);
        }
    }

    private static void applyAndRepair(Human human) {
        AttributeInstance maximumHealth = human.getAttribute(Attributes.MAX_HEALTH);
        if (maximumHealth == null) {
            return;
        }

        int rolled = human.getPersistentData().getInt(ROLLED_HEALTH);
        int previousRoll = rolled;
        HealthRange range = rangeFor(human);
        double previousMaximum = Math.max(1.0D, human.getMaxHealth());
        double currentRatio = Math.max(0.0D, Math.min(1.0D, human.getHealth() / previousMaximum));
        if (!human.getPersistentData().getBoolean(TIER_RANGE_SCHEMA)
                || rolled < range.minimum() || rolled > range.maximum()) {
            rolled = human.getRandom().nextInt(range.minimum(), range.maximum() + 1);
            human.getPersistentData().putInt(ROLLED_HEALTH, rolled);
            human.getPersistentData().putBoolean(TIER_RANGE_SCHEMA, true);
        }

        if (Math.abs(maximumHealth.getBaseValue() - rolled) <= 0.001D
                || !MaxHealthCompatibilityPolicy.shouldApplyRoll(
                        maximumHealth.getBaseValue(), previousRoll, originalBase(human))) {
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

    private static double originalBase(Human human) {
        return TierThreeHuman.isTierThree(human) ? 50.0D : 60.0D;
    }

    private record HealthRange(int minimum, int maximum) {
    }
}
