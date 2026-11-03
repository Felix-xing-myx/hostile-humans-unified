package dev.felix.hostilehumans.settlement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/** Vanilla template files; dimension-local settlement registration is a separate operation. */
final class SettlementTemplateFiles {
    private static final long MAX_VOLUME = 262_144;
    private static final int PREVIEW_TICKS = 600;
    private static final Map<ServerPlayer, Placement> PENDING = new WeakHashMap<>();

    private record Placement(ServerLevel level, ResourceLocation id, BlockPos origin,
            StructurePlaceSettings settings, StructureTemplate template, RegionBox bounds, int entityEntries, long expires) {}

    private SettlementTemplateFiles() {}

    static void checkSize(TemplateTransform.Cell size) {
        if (size.x() < 1 || size.y() < 1 || size.z() < 1
                || size.x() > 512 || size.y() > 512 || size.z() > 512
                || (long) size.x() * size.y() * size.z() > MAX_VOLUME)
            throw new IllegalArgumentException("template limit: 512 per axis, 262144 cells total");
    }

    private static void checkArea(ServerLevel level, RegionBox bounds) {
        if (bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ())))
            throw new IllegalArgumentException("template exceeds world limits");
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
                if (!level.hasChunkAt(new BlockPos(x << 4, bounds.minY(), z << 4)))
                    throw new IllegalArgumentException("all template chunks must already be loaded");
    }

    static ListTag list(ServerLevel level) {
        var manager = level.getStructureManager();
        var result = new ListTag();
        try (var templates = manager.listTemplates()) {
            templates.distinct().filter(id -> id.toString().length() <= 256).map(id -> {
                var row = new CompoundTag();
                row.putString("id", id.toString());
                row.putBoolean("world", Files.isRegularFile(manager.getPathToGeneratedStructure(id, ".nbt")));
                return row;
            }).filter(row -> row.getBoolean("world")
                    || row.getString("id").startsWith("hostile_humans_unified:"))
                    .sorted(java.util.Comparator.<CompoundTag, Boolean>comparing(row -> !row.getBoolean("world"))
                            .thenComparing(row -> row.getString("id")))
                    .limit(32).forEach(result::add);
        }
        return result;
    }

    static String save(ServerPlayer player, BlockPos position, int revision, boolean entities,
            boolean overwrite) {
        if (!player.canUseGameMasterBlocks()) return "template files require creative mode and operator permission";
        var info = SettlementAuthoring.information(player, position);
        if (info == null || info.revision() != revision) return "author unavailable or stale";
        var level = player.serverLevel();
        var author = SettlementCodec.template(info.author());
        author.validateForPublication();
        var transform = SettlementAuthoring.currentTransform(player, info);
        if (transform.clockwiseQuarterTurns() != 0 || transform.mirror() != TemplateTransform.Mirror.NONE)
            return "export from an unrotated author copy";
        var size = author.size();
        checkSize(size);
        var bounds = new RegionBox(0, 0, 0, size.x() - 1, size.y() - 1, size.z() - 1).transform(transform);
        checkArea(level, bounds);
        var id = new ResourceLocation(author.template());
        if (id.getPath().equals("untitled")) return "set a template ID and save the author draft first";
        var manager = level.getStructureManager();
        var path = manager.getPathToGeneratedStructure(id, ".nbt");
        if (!overwrite && (Files.exists(path) || manager.get(id).isPresent()))
            return "template ID already exists; choose a new ID or explicitly replace the world file";
        if (overwrite && !Files.isRegularFile(path)) return "only an existing world file can be replaced";
        var template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new Vec3i(size.x(), size.y(), size.z()), entities, Blocks.STRUCTURE_VOID);
        template.setAuthor(player.getGameProfile().getName());
        var data = template.save(new CompoundTag());
        sanitize(data, size, Rotation.NONE, Mirror.NONE);
        SettlementHumanTemplates.prepareFile(data, new Vec3(bounds.minX(), bounds.minY(), bounds.minZ()),
                level.dimension().location());
        try {
            Files.createDirectories(path.getParent());
            var staging = Files.createTempFile(path.getParent(), ".hhu-template-", ".tmp");
            try {
                NbtIo.writeCompressed(data, staging.toFile());
                if (overwrite)
                    Files.move(staging, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(staging, path);
            } finally {
                Files.deleteIfExists(staging);
            }
            manager.remove(id);
            int count = entityEntries(data);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    entities ? "gui.hostile_humans_unified.settlement.export_entity_count"
                            : "gui.hostile_humans_unified.settlement.export_entities_skipped", id.toString(), count));
            return "saved template " + id;
        } catch (IOException error) {
            dev.felix.hostilehumans.bootstrap.HumanRuntime.LOGGER.warn("Could not save settlement template {}", id, error);
            return "could not write template file";
        }
    }

    static String preview(ServerPlayer player, ResourceLocation id, BlockPos corner,
            Rotation rotation, Mirror mirror, boolean entities) {
        return preview(player, id, corner, rotation, mirror, entities, PREVIEW_TICKS);
    }

    static String preview(ServerPlayer player, ResourceLocation id, BlockPos corner,
            Rotation rotation, Mirror mirror, boolean entities, int previewTicks) {
        if (!player.canUseGameMasterBlocks()) throw new IllegalArgumentException("creative operator required");
        var level = player.serverLevel();
        var manager = level.getStructureManager();
        var original = manager.get(id).orElseThrow(() -> new IllegalArgumentException("template not found: " + id));
        var size = new TemplateTransform.Cell(original.getSize().getX(),
                original.getSize().getY(), original.getSize().getZ());
        checkSize(size);
        var settings = new StructurePlaceSettings().setRotation(rotation).setMirror(mirror).setIgnoreEntities(!entities);
        var zero = original.getBoundingBox(settings, BlockPos.ZERO);
        var origin = corner.offset(-zero.minX(), -zero.minY(), -zero.minZ());
        var box = original.getBoundingBox(settings, origin);
        var bounds = new RegionBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        checkDestination(player, corner, bounds);
        var data = original.save(new CompoundTag());
        sanitize(data, size, rotation, mirror);
        SettlementHumanTemplates.prepareFile(data, null, null);
        // Pin the same palette for preview and confirmed placement.
        var palettes = data.getList("palettes", Tag.TAG_LIST);
        if (!palettes.isEmpty()) {
            data.put("palette", palettes.getList(0).copy());
            data.remove("palettes");
        }
        var copy = manager.readStructure(data);
        var ghosts = previewBlocks(level, data, origin, settings);
        int count = entityEntries(data);
        PENDING.put(player, new Placement(level, id, origin, settings, copy, bounds, count,
                level.getGameTime() + previewTicks));
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                entities ? "gui.hostile_humans_unified.settlement.preview_entity_count"
                        : "gui.hostile_humans_unified.settlement.preview_entities_off", count));
        SettlementNetwork.preview(player, new SettlementPreview(level.dimension().location().toString(),
                List.of(new SettlementPreview.Box(bounds, 0xffffff, id.toString().substring(0,
                        Math.min(id.toString().length(), 192)))), null, null, false, ghosts, "place", id.toString().substring(0,
                        Math.min(id.toString().length(), 256))));
        return "Preview " + id + ": /hh settlement template confirm replaces blocks inside the box; "
                + "/hh settlement template cancel aborts. Preview expires after " + previewTicks / 20 + " seconds."
                + (ghosts.isEmpty() ? " Bounds only: model preview is empty or exceeds 4096 blocks." : "");
    }

    private static int entityEntries(CompoundTag data) {
        return data.getList("entities", Tag.TAG_COMPOUND).size();
    }

    private static List<SettlementPreview.Ghost> previewBlocks(ServerLevel level, CompoundTag data,
            BlockPos origin, StructurePlaceSettings settings) {
        var registry = level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
        var palette = data.getList("palette", Tag.TAG_COMPOUND).stream()
                .map(tag -> NbtUtils.readBlockState(registry, (CompoundTag) tag))
                .map(state -> state.mirror(settings.getMirror()).rotate(settings.getRotation()))
                .toList();
        var blocks = data.getList("blocks", Tag.TAG_COMPOUND);
        var ghosts = new java.util.ArrayList<SettlementPreview.Ghost>();
        for (int i = 0; i < blocks.size(); i++) {
            var entry = blocks.getCompound(i);
            int stateId = entry.getInt("state");
            if (stateId < 0 || stateId >= palette.size()) continue;
            var state = palette.get(stateId);
            if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) continue;
            if (ghosts.size() == SettlementPreview.MAX_GHOSTS) return List.of();
            var pos = entry.getList("pos", Tag.TAG_INT);
            var local = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            ghosts.add(new SettlementPreview.Ghost(
                    StructureTemplate.calculateRelativePosition(settings, local).offset(origin), Block.getId(state)));
        }
        return ghosts;
    }

    private static void checkDestination(ServerPlayer player, BlockPos corner, RegionBox bounds) {
        if (player.distanceToSqr(Vec3.atCenterOf(corner)) > 4096)
            throw new IllegalArgumentException("destination corner must be within 64 blocks");
        checkArea(player.serverLevel(), bounds);
    }

    private static void checkExistingInformationBlocks(ServerLevel level, RegionBox bounds) {
        var chunks = level.getChunkSource();
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                var chunk = chunks.getChunkNow(x, z);
                if (chunk == null) throw new IllegalArgumentException("all template chunks must already be loaded");
                // The chunk index includes positions with pending block-entity data.
                for (BlockPos position : chunk.getBlockEntitiesPos()) {
                    if (bounds.contains(position.getX(), position.getY(), position.getZ())
                            && level.getBlockEntity(position) instanceof SettlementInformationBlockEntity)
                        throw new IllegalArgumentException("destination contains an existing information block");
                }
            }
        }
    }

    static String confirm(ServerPlayer player) {
        var pending = PENDING.get(player);
        if (!player.canUseGameMasterBlocks() || pending == null || pending.level() != player.serverLevel()
                || pending.expires() <= pending.level().getGameTime())
            throw new IllegalArgumentException("no valid template placement preview");
        var bounds = pending.bounds();
        var min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        checkDestination(player, min, bounds);
        checkExistingInformationBlocks(pending.level(), bounds);
        // Placement can mutate the world before it fails. Never retry the same preview.
        cancel(player);
        if (!pending.template().placeInWorld(pending.level(), pending.origin(), pending.origin(),
                pending.settings(), pending.level().getRandom(), Block.UPDATE_ALL))
            throw new IllegalArgumentException("template placement failed; create a new preview before retrying");
        if (!pending.settings().isIgnoreEntities())
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.hostile_humans_unified.settlement.placement_entity_count", pending.entityEntries()));
        return "placed " + pending.id() + "; open its information block to register the new site";
    }

    static void keepPreview(ServerPlayer player, int ticks) {
        var current = PENDING.get(player);
        if (current != null && current.expires() > current.level().getGameTime())
            PENDING.put(player, new Placement(current.level(), current.id(), current.origin(), current.settings(),
                    current.template(), current.bounds(), current.entityEntries(), current.level().getGameTime() + ticks));
    }

    static boolean hasPreview(ServerPlayer player) {
        return PENDING.containsKey(player);
    }

    static void cancel(ServerPlayer player) {
        if (PENDING.remove(player) != null) SettlementNetwork.preview(player, SettlementPreview.EMPTY_PLACEMENT);
    }

    static void tick(ServerPlayer player) {
        var pending = PENDING.get(player);
        if (pending != null && (pending.level() != player.serverLevel() || !player.canUseGameMasterBlocks()
                || pending.expires() <= pending.level().getGameTime())) cancel(player);
    }

    /** Clean copied carriers and normalize authored geometry to the placed structure's minimum corner. */
    private static void sanitize(CompoundTag data, TemplateTransform.Cell size,
            Rotation rotation, Mirror mirror) {
        var localBounds = new RegionBox(0, 0, 0, size.x() - 1, size.y() - 1, size.z() - 1);
        var zero = SettlementAuthoring.cell(BlockPos.ZERO);
        var orientation = new TemplateTransform(zero, zero, TemplateTransform.Mirror.valueOf(mirror.name()),
                switch (rotation) {
                    case NONE -> 0;
                    case CLOCKWISE_90 -> 1;
                    case CLOCKWISE_180 -> 2;
                    case COUNTERCLOCKWISE_90 -> 3;
                });
        var oriented = localBounds.transform(orientation);
        var shift = new TemplateTransform(new TemplateTransform.Cell(-oriented.minX(), -oriented.minY(), -oriented.minZ()),
                zero, orientation.mirror(), orientation.clockwiseQuarterTurns());
        var dimensions = new TemplateTransform.Cell((int) oriented.sizeX(), (int) oriented.sizeY(), (int) oriented.sizeZ());
        var blocks = data.getList("blocks", Tag.TAG_COMPOUND);
        int carriers = 0;
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.getCompound(i);
            var tag = block.getCompound("nbt");
            var coordinates = block.getList("pos", Tag.TAG_INT);
            if (coordinates.size() != 3 || !localBounds.contains(
                    coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2)))
                throw new IllegalArgumentException("template has a block outside its declared size");
            if (!tag.getString("id").equals("hostile_humans_unified:settlement_information")) continue;
            carriers++;
            var author = SettlementCodec.template(tag.getCompound(SettlementInformationBlockEntity.AUTHOR));
            author.validateForPublication();
            var source = new TemplateTransform.Cell(coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2));
            var offset = author.originOffset();
            if (offset.x() != -source.x() || offset.y() != -source.y() || offset.z() != -source.z())
                throw new IllegalArgumentException("information block and template origin do not match");
            var carrier = shift.apply(source);
            var normalized = new SettlementTemplate(author.template(), author.module(), author.kind(),
                    author.faction(), author.tier(), author.revision(), dimensions,
                    new TemplateTransform.Cell(-carrier.x(), -carrier.y(), -carrier.z()),
                    author.regions().stream().map(r -> r.transform(shift)).toList(),
                    author.facilities().stream().map(f -> f.transform(shift)).toList());
            tag.put(SettlementInformationBlockEntity.AUTHOR, SettlementCodec.template(normalized));
            tag.remove("settlement_instance");
            tag.remove("settlement_module");
        }
        if (carriers != 1) throw new IllegalArgumentException("author templates require exactly one information block");
    }
}
