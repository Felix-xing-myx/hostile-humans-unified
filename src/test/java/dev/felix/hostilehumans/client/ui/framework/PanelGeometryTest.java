package dev.felix.hostilehumans.client.ui.framework;

/** Boundary regressions for scaled windows, focus reveal, panel chrome and long-life bars. */
public final class PanelGeometryTest {
    public static void main(String[] args) {
        check(PanelGeometry.frame(PanelGeometry.Size.M, 1920, 1080).width() == 600, "M width");
        UiRect tiny = PanelGeometry.frame(PanelGeometry.Size.L, 12, 9);
        check(tiny.width() > 0 && tiny.height() > 0, "tiny window positive");
        check(PanelGeometry.clampScroll(999, 300, 100) == 200, "clamp after resize");
        check(PanelGeometry.reveal(0, 250, 275, 100) == 175, "keyboard reveals row");
        check(PanelGeometry.reveal(0, 250, 500, 100) == 250, "large disclosure reveals heading");
        int ticks = PanelGeometry.healthTicks(300);
        double filled = 0;
        for (int i = 0; i < ticks; i++) filled += PanelGeometry.healthTick(250, 300, i);
        check(Math.abs(filled / ticks - 250.0 / 300) < 1e-12, "250/300 does not paint full");
        // An odd maximum must still fill the whole bar, which the fixed 25 point cell broke.
        int odd = PanelGeometry.healthTicks(111);
        double full = 0;
        for (int i = 0; i < odd; i++) full += PanelGeometry.healthTick(111, 111, i);
        check(Math.abs(full - odd) < 1e-12, "full health paints every tick at any maximum");
        check(odd == 40 && PanelGeometry.healthCells(111) == 5, "111 health uses five cells");
        check(PanelGeometry.TICKS_PER_CELL == 8, "eight fine ticks per cell");
        check(PanelGeometry.healthCells(10000) == 8, "long life has bounded ticks");
        check(PanelGeometry.healthTick(Double.NaN, 300, 0) == 0, "invalid health safe");
        check(PanelGeometry.healthTick(0, 300, 0) == 0, "dead health empty");

        // Panel chrome keeps the reference 12/14 margins and the fixed action bar.
        UiRect frame = new UiRect(0, 0, 400, 420);
        UiRect plain = PanelGeometry.body(frame, false);
        check(plain.x() == 12 && plain.width() == 374, "body margins match the reference");
        check(plain.y() == 33, "body starts below the header rule");
        check(plain.bottom() == frame.bottom() - 40, "action bar keeps 40px");
        UiRect banded = PanelGeometry.body(frame, true);
        check(banded.y() == 60, "filter band pushes the shelf down");
        check(banded.height() == 420 - 40 - 60, "banded shelf height");
        UiRect band = PanelGeometry.band(frame);
        check(band.y() == 32 && band.height() == 24, "filter band geometry");
        check(band.width() == 376, "filter band respects the margins");

        UiRect[] columns = PanelGeometry.split(plain, 280);
        check(columns[0].width() == 280 && columns[1].x() == 292, "split columns stay adjacent");
        check(columns[1].width() == 94, "split keeps the remainder");
        check(PanelGeometry.split(plain, 9999)[0].width() == 373, "split never fills the row");
        check(PanelGeometry.split(plain, 0)[0].width() == 1, "split keeps both columns alive");

        check(PanelGeometry.Size.S_PLUS.width() == 420, "S+ width");
        check(PanelGeometry.Size.S_PLUS.height() == 520, "S+ height");
        check(new UiRect(0, 0, 30, 20).inset(8, 6).equals(new UiRect(8, 6, 14, 8)), "independent padding");
        check(PanelGeometry.split(new UiRect(0, 0, 0, 20), 280)[0].width() == 0, "empty split stays empty");
        System.out.println("PanelGeometryTest: all checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}