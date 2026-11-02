package dev.felix.hostilehumans.behavior;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.item.ItemStack;

/** Immutable execution-owned observation. Negative one means unknown, never a guessed countdown. */
public record ActionProgressSnapshot(String kind, String item, String location, String state, long sampledTick,
        double work, double totalWork, double workPerTick, long elapsedTicks, long remainingTicks, String detail) {
    public static final int KIND_LENGTH = 24, ITEM_LENGTH = 96, LOCATION_LENGTH = 32,
            STATE_LENGTH = 32, DETAIL_LENGTH = 160;
    public ActionProgressSnapshot {
        kind = bounded(kind, KIND_LENGTH); item = bounded(item, ITEM_LENGTH);
        location = bounded(location, LOCATION_LENGTH); state = bounded(state, STATE_LENGTH);
        detail = bounded(detail, DETAIL_LENGTH);
        if (!amount(work) || !amount(totalWork) || !amount(workPerTick) || elapsedTicks < -1 || remainingTicks < -1)
            throw new IllegalArgumentException("Invalid execution progress");
    }
    public ActionProgressSnapshot(String kind, String item, long elapsedTicks, long remainingTicks, String detail) {
        this(kind, item, "", "", -1, -1, -1, -1, elapsedTicks, remainingTicks, detail);
    }
    private static boolean amount(double value) { return Double.isFinite(value) && (value >= 0 || value == -1); }
    private static String bounded(String value, int limit) {
        if (value == null || value.length() > limit) throw new IllegalArgumentException("Oversized progress text");
        return value;
    }
    public static String clip(String value, int limit) {
        if (value == null) return "";
        int end = Math.min(value.length(), limit);
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
    public static String item(ItemStack actual) {
        return actual == null || actual.isEmpty() ? "" : clip(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).toString(), ITEM_LENGTH);
    }
    /** Physical reference query only. Does not bind equipment, resolve handles or execute an exchange. */
    public static String location(Human human, ItemStack actual) {
        if (actual == null || actual.isEmpty()) return "missing";
        String found = "missing"; int references = 0;
        if (human.getMainHandItem() == actual) { found = "main_hand"; references++; }
        if (human.getOffhandItem() == actual) { found = "off_hand"; references++; }
        var data = human.getData();
        if (data != null) for (int i = 0; i < data.getInventoryItemsSize(); i++)
            if (data.getInventoryItem(i) == actual) { found = "backpack:" + i; references++; }
        return references > 1 ? "ambiguous" : found;
    }
    /** Estimate at the last actually executed rate; callers state the phase, not a promised wall-clock finish. */
    public static long remaining(double work, double total, double rate) {
        if (!Double.isFinite(work) || !Double.isFinite(total) || !Double.isFinite(rate)
                || work < 0 || total < 0 || rate <= 0) return -1;
        return (long) Math.ceil(Math.max(0, total - work) / rate);
    }
}
