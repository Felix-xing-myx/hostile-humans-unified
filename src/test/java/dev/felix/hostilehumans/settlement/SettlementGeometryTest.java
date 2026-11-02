package dev.felix.hostilehumans.settlement;

import java.util.List;

public final class SettlementGeometryTest {
    public static void main(String[] args) {
        var outer =
                new SettlementRegion(
                        "courtyard",
                        "L院落",
                        SettlementRegion.Layer.OUTER,
                        List.of(new RegionBox(0, 0, 0, 9, 4, 2), new RegionBox(0, 0, 0, 2, 4, 9)),
                        List.of(new RegionBox(1, 0, 1, 1, 4, 1)));
        require(
                outer.contains(8.5, 0, 1.5) && outer.contains(1.5, 0, 8.5),
                "both L segments included");
        require(!outer.contains(8, 0, 8), "L concavity is not filled");
        require(
                !outer.contains(1, 0, 1) && outer.contains(2, 0, 1),
                "exclusion is half-open, not a global hole in other regions");
        require(
                !outer.contains(10, 0, 1) && outer.contains(9.999, 0, 1),
                "inclusive final block cell with half-open position query");
        require(
                !outer.contains(1, 5, 2) && !outer.contains(Double.NaN, 0, 0),
                "height and finite coordinates");
        int cases = 0;
        for (var mirror : TemplateTransform.Mirror.values())
            for (int turn = 0; turn < 4; turn++) {
                var transform =
                        new TemplateTransform(
                                new TemplateTransform.Cell(-115, 63, 221),
                                new TemplateTransform.Cell(3, 0, -2),
                                mirror,
                                turn);
                var world = outer.transform(transform);
                for (int x = -12; x <= 12; x++)
                    for (int z = -12; z <= 12; z++) {
                        var local = new TemplateTransform.Cell(x, 1, z);
                        var actual = transform.apply(local);
                        require(
                                transform.unapply(actual).equals(local),
                                "nonzero-pivot transform roundtrip");
                        require(
                                world.contains(actual.x() + .5, actual.y() + .5, actual.z() + .5)
                                        == outer.contains(x + .5, 1.5, z + .5),
                                "geometry transforms cells without max+1 off-by-one");
                        cases++;
                    }
            }
        var negative = new RegionBox(-8, -4, -2, -1, 2, 3);
        require(
                negative.contains(-.001, 0, 0) && !negative.contains(0, 0, 0),
                "negative local coordinates retained");
        System.out.println(
                "SettlementGeometryTest: " + cases + " placement and region cases passed");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
