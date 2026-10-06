package club.someoneice.humangunner;

import java.util.Random;
import java.util.function.DoubleSupplier;

/** Probability and small-stack regression checks without a running game. */
public final class HostileBeaconFoodPolicyTest {
    public static void main(String[] args) {
        DoubleSupplier unused = () -> { throw new AssertionError("unchanged/forbidden items must not roll"); };
        check(HostileBeaconFoodPolicy.retainedCount(64, false, false, unused) == 64,
                "non-food preserves count and random stream");
        check(HostileBeaconFoodPolicy.retainedCount(64, true, true, unused) == 0,
                "enchanted apples never survive");
        check(HostileBeaconFoodPolicy.retainedCount(1, true, false, () -> 0.199999D) == 1,
                "single food can survive instead of being rounded down");
        check(HostileBeaconFoodPolicy.retainedCount(1, true, false, () -> 0.20D) == 0,
                "retention threshold is exclusive");
        check(HostileBeaconFoodPolicy.retainedCount(0, true, false, unused) == 0,
                "empty drops do not roll");
        Random random = new Random(20261006L);
        int original = 0;
        int retained = 0;
        // Keep the pre-existing supply admission independent of the new final filter.
        for (int unit = 0; unit < 200000; unit++) {
            if (random.nextDouble() < 0.20D) {
                original++;
                retained += HostileBeaconFoodPolicy.retainedCount(1, true, false, random::nextDouble);
            }
        }
        check(Math.abs(retained / (double) original - 0.20D) < 0.01D,
                "final food is 20% of the already reduced baseline");
        check(Math.abs(retained / 200000.0D - 0.04D) < 0.005D,
                "two independent reductions compose to 4% of carried supply");
        System.out.println("PASS: hostile beacon food retains 20% of baseline drops");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
