package club.someoneice.humangunner;

import java.util.Locale;

/** Firing reach is independent of the distance a gunner tries to maintain. */
final class GunRangePolicy {
    record Band(double minimum, double maximum, double retreatResume, double fireRange) {}

    private GunRangePolicy() {}

    static Band forType(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "sniper", "snipers_rifle", "sniper_rifle" -> new Band(32.0D, 40.0D, 36.0D, 128.0D);
            case "shotgun" -> new Band(6.0D, 12.0D, 9.0D, 64.0D);
            case "pistol", "smg" -> new Band(12.0D, 20.0D, 16.0D, 64.0D);
            case "mg", "machine_gun" -> new Band(16.0D, 24.0D, 20.0D, 64.0D);
            default -> new Band(16.0D, 24.0D, 20.0D, 64.0D);
        };
    }
}
