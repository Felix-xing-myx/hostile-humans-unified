package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import java.util.Arrays;

/** Read-only panel comparison: current faction/rank/identity centre, never a population mean.
 * Uses the actual constrained allocator once per requested snapshot, without random draws. */
final class AttributeReference {
    record Centre(double[] allocation, boolean known) { }
    private AttributeReference() { }

    static Centre of(Human human) {
        var config = SpecialtyConfig.get();
        if (!config.enabled()) return new Centre(SpecialtyAttributes.baseline(), true);
        double budget = HumanFactions.isBoss(human) ? HumanFactions.bossBudget(human)
                : ordinaryBudget(config, TierAttributes.key(human));
        var data = SpecialtyData.get();
        try {
            return new Centre(allocate(budget, SpecialtyAttributes.preference(human, HumanFactions.isBoss(human)),
                    data.costs(), data.caps(config.deviationCap()), config, HumanFactions.allocationMaximum(human)), true);
        } catch (IllegalArgumentException invalid) {
            // Bad custom boundaries must not crash inspection or manufacture a meaningful comparison.
            return new Centre(SpecialtyAttributes.baseline(), false);
        }
    }

    static double ordinaryBudget(SpecialtyConfig config, String tier) {
        return config.budgetMean() * config.tierBudgetMultiplier(tier);
    }

    static double[] allocate(double budget, double[] preferences, double[] costs, double[] caps,
            SpecialtyConfig config, double maximum) {
        double[] neutralTilts = new double[costs.length];
        Arrays.fill(neutralTilts, 1);
        return SpecialtyAllocation.allocate(budget, preferences, costs, caps, neutralTilts,
                config.capBudgetExponent(), config.multiplierMin(), maximum).multipliers();
    }

    /** Same native modifiers and sanitization, but replace the owned allocation's base input.
     * Never temporarily mutate the live attribute to obtain a hypothetical value. */
    static double physicalValue(AttributeInstance attribute, double replacementBase) {
        if (attribute == null) return replacementBase;
        return attribute.getAttribute().sanitizeValue(DefenseAttributes.projectRaw(attribute, replacementBase, null));
    }
}
