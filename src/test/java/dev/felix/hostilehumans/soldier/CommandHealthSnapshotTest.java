package dev.felix.hostilehumans.soldier;

import java.util.UUID;

public final class CommandHealthSnapshotTest {
    public static void main(String[] args) {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var h = CommandHealthSnapshot.sample(first, "A", 250, 300);
        check(
                h.available() && h.current() == 250 && h.maximum() == 300,
                "actual long-life values preserved");
        var same = h.compare(CommandHealthSnapshot.sample(second, "B", 250, 300));
        check(
                !same.mixed() && same.human().equals(first),
                "equal readings keep the first representative");
        var mixed = h.compare(CommandHealthSnapshot.sample(second, "B", 120, 200));
        check(
                mixed.mixed() && mixed.current() == 250 && mixed.maximum() == 300,
                "different readings flagged, never summed/averaged");
        check(!CommandHealthSnapshot.empty().available(), "no loaded member has no fake bar");
        check(
                !CommandHealthSnapshot.sample(first, "A", Float.NaN, 30).available(),
                "bad server reading safely unavailable");
        boolean failed = false;
        try {
            new CommandHealthSnapshot(first, "A", 3, Float.NaN, false);
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        check(failed, "invalid wire reading rejected");
        System.out.println("CommandHealthSnapshotTest: all checks passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
