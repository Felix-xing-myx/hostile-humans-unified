package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;

/** Two navigator shore search uses at most four retreat-budget path requests. */
public final class RetreatShoreEscape {
    private int nextSearchTick;

    public Path tick(Human human, LivingEntity threat, double speed) {
        if (!human.isInWater()) return null;
        Path current = human.getNavigation().getPath();
        if (RetreatPathSafety.isSafe(human, threat, current, false)
                && !human.getNavigation().isStuck()
                && !RetreatRouteContinuity.needsExtension(human, threat)) return current;
        if (human.tickCount >= nextSearchTick) {
            int budget = RetreatRouteContinuity.claimSearch(human, 4);
            // A bank candidate may need both ground and water navigation.
            if (budget == 4) {
                Path shore = human.findNearestShorePath(null, threat);
                nextSearchTick = human.tickCount + 20;
                RetreatRouteContinuity.cancelSearch(human);
                if (RetreatPathSafety.isSafe(human, threat, shore, true)) {
                    boolean started = human.isSeekingShore()
                            ? human.continueSeekingShore(shore, speed) : human.startSeekingShore(shore, speed);
                    if (started) return shore;
                }
            } else nextSearchTick = human.tickCount + 1;
        }
        // No arbitrary distant bank steering: collision-checked horizontal escape only.
        human.continueWaterRetreatFallback(threat, speed);
        return null;
    }

    public void reset() { nextSearchTick = 0; }
}
