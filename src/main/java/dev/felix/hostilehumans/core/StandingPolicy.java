package dev.felix.hostilehumans.core;

/** Pure political arithmetic; perception, ownership and damage permission remain separate decisions. */
public final class StandingPolicy {
    public enum Attitude { NEMESIS, HOSTILE, WARY, NEUTRAL, FAVORABLE, FRIENDLY, ALLIED }
    private StandingPolicy() {}
    public static Attitude attitude(int standing) {
        if (standing <= -300) return Attitude.NEMESIS;
        if (standing <= -100) return Attitude.HOSTILE;
        if (standing < 50) return Attitude.WARY;
        if (standing < 150) return Attitude.NEUTRAL;
        if (standing < 250) return Attitude.FAVORABLE;
        if (standing < 400) return Attitude.FRIENDLY;
        return Attitude.ALLIED;
    }
    public static int bandModifier(boolean sameFaction, boolean enemies, int grade) {
        if (grade < 1 || grade > 4) return 0;
        return sameFaction ? new int[]{50, 75, 100, 150}[grade - 1]
                : enemies ? -new int[]{100, 150, 200, 250}[grade - 1] : 0;
    }
    public static int effective(int standing, int modifier) {
        return (int) Math.max(-500L, Math.min(500L, (long) standing + modifier));
    }
    public static boolean hostile(int standing) { return standing <= -100; }
    public static boolean mayHire(int standing, int tier) { return tier >= 1 && tier <= 3 && standing >= (tier == 3 ? 150 : 50); }
    public static int price(int base, int standing, double friendlyDiscount, double alliedDiscount) {
        double discount = standing >= 400 ? alliedDiscount : standing >= 250 ? friendlyDiscount : 1;
        if (base < 0 || !Double.isFinite(discount) || discount < 0 || discount > 1) throw new IllegalArgumentException("Invalid hiring price");
        return (int) Math.ceil(base * discount);
    }
}
