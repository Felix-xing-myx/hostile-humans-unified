package dev.felix.hostilehumans.settlement;

public final class SettlementPlacementIdentityTest {
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        var root = SettlementPlacementIdentity.root("minecraft:overworld", "test:fortress", 12345);
        check(
                root.equals(
                        SettlementPlacementIdentity.root(
                                "minecraft:overworld", "test:fortress", 12345)),
                "chunk retries retain actual root instance");
        check(
                !root.equals(
                        SettlementPlacementIdentity.root(
                                "minecraft:overworld", "test:fortress", 12346)),
                "same template in another root is independent");
        check(
                !root.equals(
                        SettlementPlacementIdentity.root(
                                "minecraft:the_nether", "test:fortress", 12345)),
                "dimension is part of root identity");
        check(
                !root.equals(
                        SettlementPlacementIdentity.root(
                                "minecraft:overworld", "test:house", 12345)),
                "same starting chunk does not merge different structures");
        var transform =
                new TemplateTransform(
                        new TemplateTransform.Cell(100, 64, 200),
                        new TemplateTransform.Cell(3, 0, 9),
                        TemplateTransform.Mirror.LEFT_RIGHT,
                        3);
        String first = SettlementPlacementIdentity.module("test:wall", "wall", transform);
        check(
                first.equals(SettlementPlacementIdentity.module("test:wall", "wall", transform)),
                "module placement retries deduplicate");
        check(
                !first.equals(
                        SettlementPlacementIdentity.module(
                                "test:wall",
                                "wall",
                                new TemplateTransform(
                                        new TemplateTransform.Cell(101, 64, 200),
                                        transform.pivot(),
                                        transform.mirror(),
                                        3))),
                "same template at another position remains separate module");
        System.out.println("SettlementPlacementIdentityTest: 6 identity cases passed");
    }
}
