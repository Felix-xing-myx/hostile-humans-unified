package club.someoneice.humangunner;

import java.util.Locale;

/** Firing reach is independent of the distance a gunner tries to maintain. */
final class GunRangePolicy {
    record Band(double minimum, double maximum, double retreatResume, double fireRange) {}

    private GunRangePolicy() {}

    /** A clear shot at long range is not a reason to abandon a safe approach. */
    static boolean withinPreferredRange(double distance, Band band) {
        return Double.isFinite(distance) && distance >= band.minimum() && distance <= band.maximum();
    }

    static double firingReach(Band band, double followRange) {
        return band.fireRange() > 64.0D ? Math.max(band.fireRange(), followRange) : band.fireRange();
    }

    /** Some native flame/sonic projectiles expire before the generic weapon-type range. */
    static Band forWeapon(net.minecraft.world.item.ItemStack weapon) {
        var support = GunSupport.get();
        Band base = forType(support.gunType(weapon));
        return limited(base, support.maximumProjectileReach(weapon));
    }
    static Band limited(Band base, double nativeReach) {
        if (!Double.isFinite(nativeReach) || nativeReach <= 0) return base;
        double preferredMax = Math.min(base.maximum(), nativeReach * .85);
        double preferredMin = preferredMax >= base.maximum() ? base.minimum()
                : Math.min(base.minimum(), preferredMax * .5);
        return new Band(preferredMin, preferredMax,
                Math.min(base.retreatResume(), preferredMax), Math.min(base.fireRange(), nativeReach));
    }
    static double firingReach(net.minecraft.world.item.ItemStack weapon, double followRange) {
        return Math.min(firingReach(forWeapon(weapon), followRange), GunSupport.get().maximumProjectileReach(weapon));
    }
    static Band forType(String rawType) {
        String type = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        return switch (type) {
            case "sniper", "snipers_rifle", "sniper_rifle" -> new Band(32.0D, 40.0D, 36.0D, 128.0D);
            case "shotgun" -> new Band(1.0D, 6.0D, 4.0D, 64.0D);
            case "pistol" -> new Band(6.0D, 12.0D, 9.0D, 64.0D);
            case "smg" -> new Band(8.0D, 16.0D, 12.0D, 64.0D);
            case "mg", "machine_gun" -> new Band(16.0D, 24.0D, 20.0D, 64.0D);
            default -> new Band(16.0D, 24.0D, 20.0D, 64.0D);
        };
    }
}
