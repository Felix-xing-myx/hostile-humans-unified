package club.someoneice.humangunner.compat;

/** Set by bytecode capability inspection before gun AI loads; no optional classes are referenced. */
public final class NativeAimCompatibility {
    private static volatile boolean scgunsShared;
    private NativeAimCompatibility() {}
    public static boolean scgunsSharedPrediction() { return scgunsShared; }
    static void scgunsSharedPrediction(boolean supported) { scgunsShared = supported; }
}
