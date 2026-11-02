package dev.felix.hostilehumans.faction;

/** Same positive-delta calculation for authoritative mutation and typed quote previews. */
public final class StandingReward {
    private StandingReward() {}

    public static int positiveDelta(int standing, int nominal, int remaining) {
        if (standing < -500 || standing > 500 || nominal < 0 || remaining < 0)
            throw new IllegalArgumentException("standing reward inputs");
        return Math.min(nominal, Math.min(remaining, 500 - standing));
    }
}
