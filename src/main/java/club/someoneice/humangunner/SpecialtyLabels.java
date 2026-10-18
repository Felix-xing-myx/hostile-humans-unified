package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.*;

/** Labels describe saved allocation, never equipment, growth, boss baselines or runtime effects. */
public final class SpecialtyLabels {
    public record Match(String id, double excess, List<SpecialtyAttributes.Dimension> dimensions) { }
    private SpecialtyLabels() { }
    public static List<Match> of(Human human) {
        if (!SpecialtyConfig.get().enabled()) return List.of();
        List<Match> matches = new ArrayList<>();
        for (SpecialtyData.Label label : SpecialtyData.get().labels()) {
            double score = Double.POSITIVE_INFINITY;
            for (var condition : label.thresholds().entrySet()) {
                double ratio = SpecialtyAttributes.storedMultiplier(human, condition.getKey()) / condition.getValue() - 1;
                // NBT is float; exact authored thresholds such as 1.20 must still match.
                if (ratio < -1e-7) { score = -1; break; }
                score = Math.min(score, Math.max(0, ratio));
            }
            if (score >= 0) {
                List<SpecialtyAttributes.Dimension> dimensions = new ArrayList<>(label.thresholds().keySet());
                dimensions.sort(Comparator.naturalOrder());
                matches.add(new Match(label.id(), score, List.copyOf(dimensions)));
            }
        }
        // Relative excess; resilience takes its weaker condition. ID is the stable tie break.
        matches.sort(Comparator.comparingDouble(Match::excess).reversed().thenComparing(Match::id));
        return List.copyOf(matches.subList(0, Math.min(3, matches.size())));
    }
}
