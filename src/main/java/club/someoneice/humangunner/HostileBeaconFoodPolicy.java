package club.someoneice.humangunner;

import java.util.function.DoubleSupplier;

/** Thins an already generated wild beacon drop; never replaces its base loot roll. */
final class HostileBeaconFoodPolicy {
    private HostileBeaconFoodPolicy() {}

    static int retainedCount(int count, boolean food, boolean forbiddenApple, DoubleSupplier random) {
        if (forbiddenApple) return 0;
        if (!food) return count;
        int retained = 0;
        for (int unit = 0; unit < count; unit++) {
            if (random.getAsDouble() < 0.20D) retained++;
        }
        return retained;
    }
}
