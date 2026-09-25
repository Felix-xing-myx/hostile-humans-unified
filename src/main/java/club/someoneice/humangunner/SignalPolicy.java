package club.someoneice.humangunner;

final class SignalPolicy {
    static final long SUPPORT_LIFETIME_TICKS = 4800L;

    private SignalPolicy() {}

    static String cooldownKey(boolean hostile, int tier) {
        if (tier < 0 || tier > 3) throw new IllegalArgumentException("tier");
        return "humangunner:" + (hostile ? "hostile_signal_ready_" : "friendly_signal_ready_") + tier;
    }

    static long cooldownTicks(boolean hostile) {
        return hostile ? 40L : 9600L;
    }

    static int minimumDistance(boolean hostile) {
        return hostile ? 48 : 2;
    }

    static int maximumDistance(boolean hostile) {
        return hostile ? 60 : 6;
    }
}
