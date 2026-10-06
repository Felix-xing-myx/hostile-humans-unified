package club.someoneice.humangunner;

/** Escape only while a live fuse is advancing; stop at a bounded safe distance. */
public final class CreeperSafetyPolicy {
    private CreeperSafetyPolicy() { }
    public static boolean imminent(boolean ignited, int swell, int direction) {
        return ignited || (swell > 0 && direction > 0);
    }
    public static double safeDistanceSquared(boolean powered) { return powered ? 81.0D : 36.0D; }
    public static boolean rescue(boolean bodyguard, boolean ignited, int swell, int direction,
                                 double ownerDistanceSquared, boolean powered) {
        return bodyguard && imminent(ignited, swell, direction)
                && ownerDistanceSquared <= safeDistanceSquared(powered);
    }
    public static boolean escape(boolean alive, boolean ignited, int swell, int direction,
                                 double distanceSquared, boolean powered, boolean rescuing) {
        return alive && !rescuing && imminent(ignited, swell, direction)
                && distanceSquared < safeDistanceSquared(powered);
    }
}
