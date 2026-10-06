package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A snapshot used by both ground selection and pickup commit. No slots or items are manufactured. */
final class SupplyInventoryPolicy {
    static final int FOOD_SLOTS = 3;
    enum Kind { EMPTY, FOOD, MEDICINE, MELEE, HEAD, CHEST, LEGS, FEET, OTHER }
    record Slot(Kind kind, double value, int count, boolean fixed) {
        double supplyValue() { return value * count; }
    }

    private final List<Slot> slots;
    private final boolean[] reserved;
    private final int foods;
    private final int empty;

    SupplyInventoryPolicy(List<Slot> slots) {
        this.slots = List.copyOf(slots);
        reserved = new boolean[slots.size()];
        List<Integer> food = new ArrayList<>();
        int emptyCount = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).kind() == Kind.FOOD) food.add(i);
            if (slots.get(i).kind() == Kind.EMPTY) emptyCount++;
        }
        foods = food.size();
        empty = emptyCount;
        food.sort(Comparator.<Integer>comparingDouble(i -> slots.get(i).supplyValue()).reversed());
        food.stream().limit(FOOD_SLOTS).forEach(i -> reserved[i] = true);
        for (Kind kind : List.of(Kind.MEDICINE, Kind.MELEE, Kind.HEAD, Kind.CHEST, Kind.LEGS, Kind.FEET)) {
            int best = -1;
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i).kind() == kind && (best < 0 || slots.get(i).value() > slots.get(best).value())) best = i;
            }
            if (best >= 0) reserved[best] = true;
        }
    }

    boolean isReserved(int index) { return reserved[index]; }

    int availableEmptySlots(Slot incoming) {
        return incoming.kind() == Kind.FOOD ? empty : Math.max(0, empty - Math.max(0, FOOD_SLOTS - foods));
    }

    boolean canReplace(int index, Slot incoming) {
        Slot stored = slots.get(index);
        if (stored.fixed() || stored.kind() == Kind.EMPTY) return false;
        if (reserved[index]) {
            if (incoming.kind() != stored.kind()) return false;
            if (stored.kind() == Kind.FOOD) return incoming.supplyValue() > stored.supplyValue() + 1.0D;
        }
        // An old ammo-filled inventory must be able to recover its missing food slots.
        if (incoming.kind() == Kind.FOOD && foods < FOOD_SLOTS && !reserved[index]) return true;
        return incoming.value() > stored.value() + 1.0D;
    }

    static double ammoValue(boolean magazine, boolean ownsGun, boolean ownsBow) {
        if (ownsGun) return magazine ? 180.0D : 140.0D;
        if (ownsBow) return magazine ? 140.0D : 180.0D;
        return 160.0D;
    }
}
