package club.someoneice.humangunner;

/** Pure numerical rules shared by all equipment and provision selectors. */
final class FactionEquipmentPolicy {
    private FactionEquipmentPolicy() { }

    static int band(double value, double[] thresholds, boolean lowerExclusive) {
        int index = 0;
        while (index < thresholds.length && (lowerExclusive ? value >= thresholds[index] : value > thresholds[index])) index++;
        return index;
    }

    static int choose(double[] weights, double unit) {
        double total = 0;
        for (double weight : weights) if (Double.isFinite(weight) && weight > 0) total += weight;
        if (!(total > 0)) return -1;
        double roll = Math.min(Math.nextDown(1.0), Math.max(0, unit)) * total;
        for (int i = 0; i < weights.length; i++) if (Double.isFinite(weights[i]) && weights[i] > 0) {
            if ((roll -= weights[i]) < 0) return i;
        }
        return -1;
    }

    static int nearest(int requested, boolean[] available) {
        for (int distance = 0; distance < available.length; distance++) {
            int lower = requested - distance, upper = requested + distance;
            if (lower >= 0 && lower < available.length && available[lower]) return lower;
            if (upper >= 0 && upper < available.length && available[upper]) return upper;
        }
        return -1;
    }

    static int scaledCount(int base, double multiplier) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(base * multiplier)));
    }

    static int enchantmentLevel(int rank, int maximum, int remaining, int cost, double fullRoll, double levelRoll) {
        if (cost <= 0 || maximum <= 0 || remaining < cost) return 0;
        int affordable = Math.min(maximum, remaining / cost);
        double fullChance = switch (rank) { case 1 -> .15; case 2 -> .35; case 3 -> .65; default -> 0; };
        if (fullRoll < fullChance) return affordable;
        int minimum = rank == 3 ? Math.max(1, Math.min(affordable, maximum - 1)) : 1;
        return minimum + (int) (Math.max(0, Math.min(Math.nextDown(1.0), levelRoll)) * (affordable - minimum + 1));
    }
    /** Growth may reduce quality, but its replacements and vanilla fallbacks obey the same final candidate gate. */
    static <T> T allowedReplacement(T original,T replacement,java.util.function.Predicate<T> allowed) {
        return replacement==original || allowed.test(replacement) ? replacement : original;
    }

}
