package dev.felix.hostilehumans.compat;

/** Set by bytecode capability checks before optional null-target NPC bridges are installed. */
public final class NativeMonitorGunCompatibility {
    private static boolean scguns, jeg;
    private NativeMonitorGunCompatibility() {}
    public static void scguns(boolean supported) { scguns = supported; }
    public static void jeg(boolean supported) { jeg = supported; }
    public static boolean available(String backend) {
        return switch (backend) {
            case "tacz" -> true;
            case "scguns" -> scguns;
            case "jeg" -> jeg;
            default -> false;
        };
    }
}
