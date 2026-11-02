package dev.felix.hostilehumans.client.ui.framework;

import java.util.List;

public final class UiLayoutTest {
    public static void main(String[] args) {
        UiRect[] bounds = new UiRect[2];
        UiLayout.Node a = UiLayout.leaf(w -> 20, b -> bounds[0] = b),
                b = UiLayout.leaf(w -> 30, x -> bounds[1] = x);
        UiLayout.Node row = UiLayout.row(8, List.of(a, b));
        check(row.measure(300) == 30, "wide row has tallest child height");
        row.place(new UiRect(10, 20, 300, 30));
        check(
                bounds[0].y() == bounds[1].y() && bounds[0].right() + 8 == bounds[1].x(),
                "wide siblings share baseline and spacing");
        check(row.measure(180) == 58, "narrow row stacks without scaling");
        row.place(new UiRect(0, 0, 180, 58));
        check(bounds[1].y() == 28 && bounds[0].width() == 180, "stacked full width");
        UiLayout.Node empty = UiLayout.column(9, List.of());
        check(empty.measure(100) == 0, "empty group has no phantom gap");
        System.out.println("UiLayoutTest: all checks passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
