package club.someoneice.humangunner;

/** Full-condition shield value. Wear is applied once by the common equipment policy. */
final class ShieldQualityPolicy {
    private ShieldQualityPolicy() { }

    static double fullValue(int maximumDurability, int enchantmentLevels, boolean indestructible) {
        double enchantments = Math.max(0, enchantmentLevels) * 3.0D;
        return indestructible ? 10_000.0D + enchantments
                : 275.0D + Math.max(0, maximumDurability) * 0.25D + enchantments;
    }
}
