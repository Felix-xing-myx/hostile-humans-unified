package dev.felix.hostilehumans.faction;

public final class StandingRewardTest {
    private static int checks;

    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        require(
                StandingReward.positiveDelta(499, 10, 60) == 1,
                "499 preview and actual mutation both grant one");
        require(
                StandingReward.positiveDelta(500, 100, 60) == 0,
                "cap consumes no quota without gain");
        require(StandingReward.positiveDelta(100, 100, 0) == 0, "exhausted daily quota");
        require(
                StandingReward.positiveDelta(-100, 200, 150) == 150,
                "one event uses its starting-tier quota");
        require(
                StandingReward.positiveDelta(149, 100, 100) == 100,
                "promotion does not retroactively shrink event");
        require(
                StandingReward.positiveDelta(-500, Integer.MAX_VALUE, Integer.MAX_VALUE) == 1000,
                "large nominal cannot overflow cap");
        for (int score = -500; score <= 500; score++)
            for (int nominal : new int[] {0, 1, 10, 100, 1000, Integer.MAX_VALUE})
                for (int remaining : new int[] {0, 1, 30, 60, 100, 150}) {
                    int actual = StandingReward.positiveDelta(score, nominal, remaining);
                    require(
                            actual >= 0
                                    && actual <= nominal
                                    && actual <= remaining
                                    && score + actual <= 500,
                            "bounded preview/actual invariant");
                }
        System.out.println(
                "StandingRewardTest: " + checks + " capped actual/preview checks passed");
    }
}
