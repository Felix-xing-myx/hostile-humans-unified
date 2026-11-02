package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Captures actual root and selected-palette placements; all SavedData work is handed to the server
 * thread.
 */
public final class SettlementGeneration {
    private record Root(UUID id, String dimension) {}

    private record Carrier(BlockPos position, CompoundTag author) {}

    private static final class Placement {
        final Root root;
        final WeakReference<ServerLevel> level;
        final StructureTemplate template;
        final TemplateTransform transform;
        final List<Carrier> carriers = new ArrayList<>();

        Placement(
                Root root,
                ServerLevel level,
                StructureTemplate template,
                TemplateTransform transform) {
            this.root = root;
            this.level = new WeakReference<>(level);
            this.template = template;
            this.transform = transform;
        }
    }

    private static final ThreadLocal<Root> ROOT = new ThreadLocal<>();
    private static final ThreadLocal<Placement> PLACEMENT = new ThreadLocal<>();

    private SettlementGeneration() {}

    public static void beginRoot(StructureStart start, WorldGenLevel level) {
        ROOT.remove();
        PLACEMENT.remove();
        var key =
                level.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE)
                        .getKey(start.getStructure());
        if (key == null) return;
        String dimension = level.getLevel().dimension().location().toString();
        UUID id =
                SettlementPlacementIdentity.root(
                        dimension, key.toString(), start.getChunkPos().toLong());
        ROOT.set(new Root(id, dimension));
    }

    public static void endRoot() {
        ROOT.remove();
        PLACEMENT.remove();
    }

    public static void beginTemplate(
            StructureTemplate template,
            ServerLevelAccessor level,
            BlockPos origin,
            StructurePlaceSettings settings) {
        PLACEMENT.remove();
        Root root = ROOT.get();
        if (root == null) return;
        int turns =
                switch (settings.getRotation()) {
                    case NONE -> 0;
                    case CLOCKWISE_90 -> 1;
                    case CLOCKWISE_180 -> 2;
                    case COUNTERCLOCKWISE_90 -> 3;
                };
        var pivot = settings.getRotationPivot();
        var transform =
                new TemplateTransform(
                        new TemplateTransform.Cell(origin.getX(), origin.getY(), origin.getZ()),
                        new TemplateTransform.Cell(pivot.getX(), pivot.getY(), pivot.getZ()),
                        TemplateTransform.Mirror.valueOf(settings.getMirror().name()),
                        turns);
        PLACEMENT.set(new Placement(root, level.getLevel(), template, transform));
    }

    public static void processed(
            StructureTemplate template, List<StructureTemplate.StructureBlockInfo> processed) {
        var placement = PLACEMENT.get();
        if (placement == null || placement.template != template) return;
        // This is the actual palette selected by placeInWorld, after processors. No second random
        // palette lookup.
        for (var block : processed)
            if (block.state().getBlock() instanceof SettlementInformationBlock
                    && block.nbt() != null) {
                if (placement.carriers.size() >= 128) {
                    HumanRuntime.LOGGER.warn("Settlement template exceeded carrier limit");
                    placement.carriers.clear();
                    return;
                }
                placement.carriers.add(
                        new Carrier(
                                block.pos().immutable(),
                                block.nbt()
                                        .getCompound(SettlementInformationBlockEntity.AUTHOR)
                                        .copy()));
            }
    }

    public static void endTemplate(boolean success) {
        Placement placement = PLACEMENT.get();
        PLACEMENT.remove();
        if (!success || placement == null || placement.carriers.isEmpty()) return;
        var level = placement.level.get();
        if (level == null) return;
        List<Carrier> carriers = List.copyOf(placement.carriers);
        var transform = placement.transform;
        Root root = placement.root;
        level.getServer()
                .execute(
                        () -> {
                            if (!level.dimension().location().toString().equals(root.dimension()))
                                return;
                            List<SettlementModule> modules = new ArrayList<>();
                            Set<String> keys = new HashSet<>();
                            try {
                                for (var carrier : carriers) {
                                    var author = SettlementCodec.template(carrier.author());
                                    if (author.kind() == SettlementTemplate.Kind.ORDINARY) continue;
                                    var offset = author.originOffset();
                                    var expected =
                                            transform.apply(
                                                    new TemplateTransform.Cell(
                                                            Math.negateExact(offset.x()),
                                                            Math.negateExact(offset.y()),
                                                            Math.negateExact(offset.z())));
                                    if (!expected.equals(
                                            new TemplateTransform.Cell(
                                                    carrier.position().getX(),
                                                    carrier.position().getY(),
                                                    carrier.position().getZ())))
                                        throw new IllegalArgumentException(
                                                "information block/template origin mismatch");
                                    String key =
                                            SettlementPlacementIdentity.module(
                                                    author.template(), author.module(), transform);
                                    if (!keys.add(key))
                                        throw new IllegalArgumentException(
                                                "duplicate main information carrier");
                                    modules.add(
                                            new SettlementModule(
                                                    key, author, transform, carrier.position()));
                                }
                                modules.sort(
                                        Comparator.comparingInt(
                                                m ->
                                                        m.author().kind()
                                                                        == SettlementTemplate.Kind
                                                                                .ROOT
                                                                ? 0
                                                                : 1));
                                var index = SettlementIndex.get(level);
                                for (var module : modules) {
                                    var result = index.generated(root.id(), module);
                                    boolean registered =
                                            result.instance() != null
                                                    && result.instance().modules().stream()
                                                            .anyMatch(
                                                                    m ->
                                                                            m.key()
                                                                                            .equals(
                                                                                                    module
                                                                                                            .key())
                                                                                    && m.informationBlock()
                                                                                            .equals(
                                                                                                    module
                                                                                                            .informationBlock()));
                                    if (!registered && !result.reason().contains("pending"))
                                        HumanRuntime.LOGGER.warn(
                                                "Settlement generation rejected {}: {}",
                                                root.id(),
                                                result.reason());
                                    if (registered
                                            && level.hasChunkAt(module.informationBlock())
                                            && level.getBlockEntity(module.informationBlock())
                                                    instanceof
                                                    SettlementInformationBlockEntity info)
                                        info.bind(root.id(), module.key());
                                }
                            } catch (RuntimeException invalid) {
                                HumanRuntime.LOGGER.warn(
                                        "Settlement metadata import rejected; previous index"
                                            + " retained: {}",
                                        invalid.getMessage());
                            }
                        });
    }
}
