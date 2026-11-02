package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Random;

/** Compare the retained search geometry with the actual Minecraft placement oracle. */
public final class SettlementVanillaTransformTest {
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        var random = new Random(923817);
        int checks = 0;
        for (var mirror : Mirror.values())
            for (var rotation : Rotation.values())
                for (int n = 0; n < 200; n++) {
                    var point =
                            new BlockPos(
                                    random.nextInt(129) - 64,
                                    random.nextInt(129) - 64,
                                    random.nextInt(129) - 64);
                    var pivot =
                            new BlockPos(
                                    random.nextInt(33) - 16,
                                    random.nextInt(33) - 16,
                                    random.nextInt(33) - 16);
                    var origin =
                            new BlockPos(
                                    random.nextInt(10001) - 5000,
                                    random.nextInt(257) - 128,
                                    random.nextInt(10001) - 5000);
                    int turns =
                            switch (rotation) {
                                case NONE -> 0;
                                case CLOCKWISE_90 -> 1;
                                case CLOCKWISE_180 -> 2;
                                case COUNTERCLOCKWISE_90 -> 3;
                            };
                    var transform =
                            new TemplateTransform(
                                    cell(origin),
                                    cell(pivot),
                                    TemplateTransform.Mirror.valueOf(mirror.name()),
                                    turns);
                    var expected =
                            StructureTemplate.transform(point, mirror, rotation, pivot)
                                    .offset(origin);
                    if (!transform.apply(cell(point)).equals(cell(expected)))
                        throw new AssertionError(mirror + "/" + rotation + "/" + point);
                    checks++;
                }
        System.out.println(
                "SettlementVanillaTransformTest: "
                        + checks
                        + " actual vanilla transform comparisons passed");
    }

    private static TemplateTransform.Cell cell(BlockPos p) {
        return new TemplateTransform.Cell(p.getX(), p.getY(), p.getZ());
    }
}
