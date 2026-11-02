package dev.felix.hostilehumans.faction;

public final class StandingQuotaTest {
    public static void main(String[] args) {
        var quota = StandingQuota.defaults();
        for (int s : new int[] {-500, -300, -100}) require(quota.cap(s) == 150, "hostile quota");
        for (int s : new int[] {-99, 0, 49, 50, 149})
            require(quota.cap(s) == 100, "cautious and neutral quota");
        for (int s : new int[] {150, 249, 250, 399, 400, 500})
            require(quota.cap(s) == 60, "friendly quota");
        var fixed = new StandingQuota(false, 150, 100, 60, 200);
        require(
                fixed.cap(-500) == 200 && fixed.cap(500) == 200,
                "explicit fixed compatibility mode");
        try {
            new StandingQuota(true, -1, 100, 60, 30);
            throw new AssertionError("negative accepted");
        } catch (IllegalArgumentException expected) {
        }
        System.out.println("StandingQuotaTest: tier edges and explicit fixed mode passed");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
