package dev.felix.hostilehumans.client.ui.framework;

public final class InputValueTest {
    public static void main(String[] args) {
        var coordinate = InputValue.integer(0, -30000000, 30000000);
        coordinate.set("-");
        check(
                !coordinate.valid() && coordinate.text().equals("-"),
                "partial negative draft retained");
        coordinate.set("-128");
        check(coordinate.value().orElseThrow() == -128, "negative local coordinates accepted");
        coordinate.set("30000001");
        check(!coordinate.valid(), "coordinate bound enforced");
        var amount = InputValue.integer(1, 1, 4096);
        amount.set("0");
        check(!amount.valid(), "zero material cannot submit");
        amount.set("4096");
        check(amount.valid() && amount.dirty(), "valid changed amount");
        amount.accepted();
        check(!amount.dirty(), "acceptance resets draft baseline");
        System.out.println("InputValueTest: all checks passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
