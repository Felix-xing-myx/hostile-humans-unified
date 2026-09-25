package club.someoneice.humangunner;

import java.util.Locale;

/** Type offset from the configured rifle spread for this human tier. */
final class GunSpreadPolicy {
    private GunSpreadPolicy() {}

    static double degrees(double rifleSpread, String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        double offset = switch (type) {
            case "sniper", "snipers_rifle", "sniper_rifle" -> -0.5D;
            case "rifle", "assault_rifle" -> 0.0D;
            case "mg", "machine_gun" -> 0.5D;
            case "pistol", "smg" -> 1.0D;
            case "shotgun" -> 1.5D;
            default -> 0.5D;
        };
        return Math.max(0.0D, rifleSpread + offset);
    }
}
