package club.someoneice.humangunner;

import java.util.Locale;
import java.util.List;

/** Normalizes firearm type IDs and retains the old spread mapping for legacy configs. */
final class GunSpreadPolicy {
    private GunSpreadPolicy() {}

    static List<String> supportedTypes() {
        return List.of("sniper", "rifle", "mg", "pistol", "smg", "shotgun", "other");
    }

    static String category(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "sniper", "snipers_rifle", "sniper_rifle" -> "sniper";
            case "rifle", "assault_rifle" -> "rifle";
            case "mg", "machine_gun" -> "mg";
            case "pistol" -> "pistol";
            case "smg" -> "smg";
            case "shotgun" -> "shotgun";
            default -> "other";
        };
    }

    static double legacyAdjustedDegrees(double rifleSpread, String rawType) {
        double offset = switch (category(rawType)) {
            case "sniper" -> -0.5D;
            case "rifle" -> 0.0D;
            case "pistol", "smg" -> 1.0D;
            case "shotgun" -> 1.5D;
            default -> 0.5D;
        };
        return Math.max(0.0D, rifleSpread + offset);
    }

    static double legacyProjectileDegrees(double bowSpread, String type) {
        return Math.max(0.0D, bowSpread - ("crossbow".equals(type) ? 0.5D : 0.0D));
    }
}
