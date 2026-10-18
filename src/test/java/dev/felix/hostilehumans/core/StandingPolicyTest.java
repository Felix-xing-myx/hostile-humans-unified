package dev.felix.hostilehumans.core;

public final class StandingPolicyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Standing check " + checks); }
    public static void main(String[] args) {
        int[] points = {-500, -300, -299, -100, -99, 49, 50, 149, 150, 249, 250, 399, 400, 500};
        int[] expected = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6};
        for (int i = 0; i < points.length; i++) check(StandingPolicy.attitude(points[i]).ordinal() == expected[i]);
        for (int value = -500; value <= 500; value++) {
            check(StandingPolicy.hostile(value) == (StandingPolicy.attitude(value).ordinal() < 2));
            check(StandingPolicy.mayHire(value, 1) == (value >= 50));
            check(StandingPolicy.mayHire(value, 2) == (value >= 50));
            check(StandingPolicy.mayHire(value, 3) == (value >= 150));
            check(!StandingPolicy.mayHire(value, 0));
            check(!StandingPolicy.mayHire(value, 4));
            check(StandingPolicy.effective(value, 0) == value);
        }
        check(StandingPolicy.effective(Integer.MAX_VALUE, Integer.MAX_VALUE) == 500);
        check(StandingPolicy.effective(Integer.MIN_VALUE, Integer.MIN_VALUE) == -500);
        check(StandingPolicy.bandModifier(true, false, 4) == 150);
        check(StandingPolicy.bandModifier(false, true, 4) == -250);
        check(StandingPolicy.bandModifier(false, false, 4) == 0);
        check(StandingPolicy.bandModifier(true, false, 0) == 0);
        check(StandingPolicy.price(8, 249, .9, .8) == 8);
        check(StandingPolicy.price(8, 250, .9, .8) == 8);
        check(StandingPolicy.price(8, 400, .9, .8) == 7);
        check(StandingPolicy.price(32, 400, .9, .8) == 26);
        System.out.println("Standing policy checks passed: " + checks);
    }
}
