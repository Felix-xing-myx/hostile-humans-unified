package dev.felix.hostilehumans.client.ui.framework;

/** Boundary regressions for scaled windows, focus reveal and long-life bars. */
public final class PanelGeometryTest {
    public static void main(String[] args) {
        check(PanelGeometry.frame(PanelGeometry.Size.M, 1920, 1080).width() == 600, "M width");
        UiRect tiny = PanelGeometry.frame(PanelGeometry.Size.L, 12, 9);
        check(tiny.width() > 0 && tiny.height() > 0, "tiny window positive");
        check(PanelGeometry.clampScroll(999, 300, 100) == 200, "clamp after resize");
        check(PanelGeometry.reveal(0, 250, 275, 100) == 175, "keyboard reveals row");
        check(PanelGeometry.reveal(0, 250, 500, 100) == 250, "large disclosure reveals heading");
        double filled = 0;
        for (int i = 0; i < 40; i++) filled += PanelGeometry.healthTick(250, 300, i);
        check(Math.abs(filled / 40 - 250.0 / 300) < 1e-12, "250/300 does not paint full");
        check(PanelGeometry.healthCells(10000) == 8, "long life has bounded ticks");
        check(PanelGeometry.healthTick(Double.NaN, 300, 0) == 0, "invalid health safe");
        check(PanelGeometry.healthTick(0, 300, 0) == 0, "dead health empty");
        System.out.println("PanelGeometryTest: all checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
