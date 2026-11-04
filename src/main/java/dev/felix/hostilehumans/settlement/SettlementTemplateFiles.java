package dev.felix.hostilehumans.settlement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private static final long LARGE_TEMPLATE_THRESHOLD = 262_144;
    private static final Map<ServerPlayer, CaptureJob> CAPTURES = new WeakHashMap<>();
    private record CaptureJob(ServerLevel level, BlockPos anchor, int revision, boolean entities,
            boolean overwrite, ResourceLocation id, RegionBox bounds, TemplateTransform.Cell size,
            SettlementTemplateCapture capture) {}
    private static final int PREVIEW_TICKS = 600;
    private static final Map<ServerPlayer, PlacementJob> PLACEMENTS = new WeakHashMap<>();
    private record PlacementJob(Placement preview, SettlementTemplatePlacement work) {}
    private static final Map<ServerPlayer, Placement> PENDING = new WeakHashMap<>();

    private record Placement(ServerLevel level, UUID wandToken, ResourceLocation id, BlockPos origin,
            StructurePlaceSettings settings, StructureTemplate template, CompoundTag largeData, RegionBox bounds, int entityEntries, boolean authorCarrier, long expires) {}

    private SettlementTemplateFiles() {}

    static void checkSize(TemplateTransform.Cell size) {
        if (size.x() < 1 || size.y() < 1 || size.z() < 1
                || size.x() > 512 || size.y() > 512 || size.z() > 512
                || volume(size) > SettlementLimits.value(SettlementLimits.TEMPLATE_VOLUME))
            throw new IllegalArgumentException("template limit: 512 per axis, "
                    + SettlementLimits.value(SettlementLimits.TEMPLATE_VOLUME) + " cells total");
    }

    private static long volume(TemplateTransform.Cell size) {
        return (long) size.x() * size.y() * size.z();
    }

    static void checkArea(ServerLevel level, RegionBox bounds) {
        if (bounds.minY() < level.getMinBuildHeight() || bounds.maxY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()))
                || !level.getWorldBorder().isWithinBounds(new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ())))
            throw new IllegalArgumentException("template exceeds world limits");
        for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++)
            for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++)
                if (!level.hasChunkAt(new BlockPos(x << 4, bounds.minY(), z << 4)))
                    throw new IllegalArgumentException("all template chunks must already be loaded");
    }

    record TemplateSource(String name, java.nio.file.Path path, SettlementBuildingPack.Contents pack) {
        boolean authorRequired() {
            return name.equals("instance") || pack != null;
        }

        StructureTemplate read(ServerLevel level, ResourceLocation id) {
            var manager = level.getStructureManager();
            try {
                if (path != null) return manager.readStructure(SettlementTemplateLibrary.read(path));
                if (pack != null) return manager.readStructure(SettlementTemplateLibrary.read(pack.template()));
                return manager.get(id).orElseThrow(() -> new IllegalArgumentException("template not found: " + id));
            } catch (IOException error) {
                throw new IllegalArgumentException("template could not be read: " + id, error);
            }
        }
    }

    static TemplateSource resolve(ServerLevel level, ResourceLocation id) {
        var instance = SettlementTemplateLibrary.path(id);
        if (Files.isRegularFile(instance)) return new TemplateSource("instance", instance, null);
        var manager = level.getStructureManager();
        var world = manager.getPathToGeneratedStructure(id, ".nbt");
        if (Files.isRegularFile(world)) return new TemplateSource("world", world, null);
        var pack = SettlementBuildingCatalog.entries(level.getServer()).get(id);
        if (pack != null) return new TemplateSource("building_pack", null, pack);
        return manager.get(id).isPresent() ? new TemplateSource("pack", null, null) : null;
    }

    static ListTag list(ServerLevel level) {
        var manager = level.getStructureManager();
        var result = new ListTag();
        var catalog = new java.util.TreeMap<ResourceLocation, String>();
        var installed = SettlementBuildingCatalog.entries(level.getServer());
        for (var id : installed.keySet()) catalog.put(id, "building_pack");
        try (var templates = manager.listTemplates()) {
            templates.distinct().filter(id -> id.toString().length() <= 256).forEach(id -> {
                if (id.getNamespace().equals("hostile_humans_unified") && id.getPath().startsWith("building_pack/")) return;
                boolean world = Files.isRegularFile(manager.getPathToGeneratedStructure(id, ".nbt"));
                if (world || id.getNamespace().equals("hostile_humans_unified"))
                    if (world) catalog.put(id, "world");
                    else catalog.putIfAbsent(id, "pack");
            });
        }
        try {
            for (var id : SettlementTemplateLibrary.list()) catalog.put(id, "instance");
        } catch (IOException error) {
            dev.felix.hostilehumans.bootstrap.HumanRuntime.LOGGER.warn("Could not read the settlement template library", error);
        }
        catalog.entrySet().stream()
                .sorted(java.util.Comparator.comparingInt(entry -> switch (entry.getValue()) {
                    case "instance" -> 0;
                    case "world" -> 1;
                    case "building_pack" -> 2;
                    default -> 3;
                }))
                .limit(32)
                .forEach(entry -> {
                    var row = new CompoundTag();
                    row.putString("id", entry.getKey().toString());
                    row.putBoolean("world", entry.getValue().equals("world") || entry.getValue().equals("instance"));
                    row.putBoolean("generation", entry.getValue().equals("building_pack")
                            && SettlementBuildingCatalog.generationEnabled(level.getServer(), entry.getKey()));
                    row.putString("source", entry.getValue());
                    result.add(row);
                });
        return result;
    }

    static String save(ServerPlayer player, BlockPos position, int revision, boolean entities,
            boolean overwrite) {
        if (!player.canUseGameMasterBlocks()) return "template files require creative mode and operator permission";
        if (CAPTURES.containsKey(player)) return "a template capture is already in progress";
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
        for (var facility : author.facilities()) {
            if (facility.type() == SettlementFacility.Type.ENTRANCE || facility.type() == SettlementFacility.Type.POST) continue;
            var cell = transform.apply(facility.position());
            if (!bounds.contains(cell.x() + .5, cell.y(), cell.z() + .5))
                return "facility outside structure capture: " + facility.id();
        }
        checkArea(level, bounds);
        var id = new ResourceLocation(author.template());
        if (id.getPath().equals("untitled")) return "set a template ID and save the author draft first";
        var manager = level.getStructureManager();
        var path = SettlementTemplateLibrary.path(id);
        if (!overwrite && (Files.exists(path) || manager.get(id).isPresent()
                || SettlementBuildingCatalog.entries(level.getServer()).containsKey(id)))
            return "template ID already exists; choose a new ID or explicitly replace the instance file";
        if (overwrite && !Files.isRegularFile(path)) return "only an existing instance file can be replaced";
        if (volume(size) > LARGE_TEMPLATE_THRESHOLD) {
            if (!CAPTURES.isEmpty()) return "another large template capture is in progress";
            CAPTURES.put(player, new CaptureJob(level, position.immutable(), revision, entities, overwrite,
                    id, bounds, size, new SettlementTemplateCapture(level, bounds, size, entities)));
            return "large template capture started: " + id;
        }
        var template = new StructureTemplate();
        template.fillFromWorld(level, new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new Vec3i(size.x(), size.y(), size.z()), entities, Blocks.STRUCTURE_VOID);
        template.setAuthor(player.getGameProfile().getName());
        return finishExport(player, id, bounds, size, entities, overwrite, template.save(new CompoundTag()));
    }

    private static String finishExport(ServerPlayer player, ResourceLocation id, RegionBox bounds,
            TemplateTransform.Cell size, boolean entities, boolean overwrite, CompoundTag data) {
        var path = SettlementTemplateLibrary.path(id);
        var level = player.serverLevel();
        sanitize(data, size, Rotation.NONE, Mirror.NONE);
        // Vanilla selects intersecting hitboxes. Export only entities whose
        // actual positions belong to the author's selected volume.
        data.getList("entities", Tag.TAG_COMPOUND)
                .removeIf(entry -> !validEntityPosition((CompoundTag) entry, size));
        SettlementHumanTemplates.prepareFile(data, new Vec3(bounds.minX(), bounds.minY(), bounds.minZ()),
                level.dimension().location());
        try {
            if (overwrite && !Files.isRegularFile(path)) return "instance template was removed during capture";
            Files.createDirectories(path.getParent());
            var staging = Files.createTempFile(path.getParent(), ".hhu-template-", ".tmp");
            try {
                NbtIo.writeCompressed(data, staging.toFile());
                if (Files.size(staging) > SettlementTemplateLibrary.MAX_COMPRESSED_BYTES)
                    return "template file too large";
                if (overwrite)
                    Files.move(staging, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(staging, path);
            } finally {
                Files.deleteIfExists(staging);
            }
            if (!player.getServer().isSingleplayerOwner(player.getGameProfile())) {
                player.sendSystemMessage(SettlementTemplateTransfer.queue(player, id));
            }
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
        return preview(player, id, corner, rotation, mirror, entities, PREVIEW_TICKS, null);
    }

    static String preview(ServerPlayer player, ResourceLocation id, BlockPos corner,
            Rotation rotation, Mirror mirror, boolean entities, int previewTicks, UUID wandToken) {
        if (!player.canUseGameMasterBlocks()) throw new IllegalArgumentException("creative operator required");
        var level = player.serverLevel();
        var manager = level.getStructureManager();
        var source = resolve(level, id);
        if (source == null) throw new IllegalArgumentException("template not found: " + id);
        var original = source.read(level, id);
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
        // Pin the same palette for preview and confirmed placement.
        var palettes = data.getList("palettes", Tag.TAG_LIST);
        if (!palettes.isEmpty()) {
            data.put("palette", palettes.getList(0).copy());
            data.remove("palettes");
        }
        boolean authorCarrier = sanitize(data, size, rotation, mirror, source.authorRequired()) != null;
        if (entities) validateEntityPositions(data, size, origin, settings, bounds);
        SettlementHumanTemplates.prepareFile(data, null, null);
        boolean large = volume(size) > LARGE_TEMPLATE_THRESHOLD;
        var copy = large ? null : manager.readStructure(data);
        var ghosts = previewBlocks(level, data, origin, settings, large);
        int count = entityEntries(data);
        PENDING.put(player, new Placement(level, wandToken, id, origin, settings, copy, large ? data : null,
                bounds, count, authorCarrier,
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

    private static void validateEntityPositions(CompoundTag data, TemplateTransform.Cell size,
            BlockPos origin, StructurePlaceSettings settings, RegionBox bounds) {
        var entries = data.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            if (!validEntityPosition(entry, size))
                throw new IllegalArgumentException("template entity " + i + " has an invalid local position");
            var pos = entry.getList("pos", Tag.TAG_DOUBLE);
            Vec3 local = new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            BlockPos destination = BlockPos.containing(
                    StructureTemplate.transformedVec3d(settings, local).add(Vec3.atLowerCornerOf(origin)));
            if (!bounds.contains(destination.getX(), destination.getY(), destination.getZ()))
                throw new IllegalArgumentException("template entity " + i + " would spawn outside the placement bounds");
        }
    }

    static boolean validEntityPosition(CompoundTag entry, TemplateTransform.Cell size) {
        var position = entry.getList("pos", Tag.TAG_DOUBLE);
        return position.size() == 3 && entry.getList("blockPos", Tag.TAG_INT).size() == 3
                && insideTemplate(position.getDouble(0), size.x())
                && insideTemplate(position.getDouble(1), size.y())
                && insideTemplate(position.getDouble(2), size.z());
    }

    private static boolean insideTemplate(double coordinate, int size) {
        return coordinate >= 0 && coordinate < size;
    }

    private static int entityEntries(CompoundTag data) {
        return data.getList("entities", Tag.TAG_COMPOUND).size();
    }

    private static List<SettlementPreview.Ghost> previewBlocks(ServerLevel level, CompoundTag data,
            BlockPos origin, StructurePlaceSettings settings, boolean sampled) {
        var registry = level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
        var palette = data.getList("palette", Tag.TAG_COMPOUND).stream()
                .map(tag -> NbtUtils.readBlockState(registry, (CompoundTag) tag))
                .map(state -> state.mirror(settings.getMirror()).rotate(settings.getRotation()))
                .toList();
        var blocks = data.getList("blocks", Tag.TAG_COMPOUND);
        var ghosts = new java.util.ArrayList<SettlementPreview.Ghost>();
        int stride = sampled ? Math.max(1, (blocks.size() + 511) / 512) : 1;
        for (int i = 0; i < blocks.size(); i += stride) {
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
        return confirm(player, null);
    }

    static String confirm(ServerPlayer player, UUID wandToken) {
        if (!PLACEMENTS.isEmpty()) throw new IllegalArgumentException("a large template placement is already in progress");
        var pending = ownedPreview(player, wandToken);
        if (pending == null)
            throw new IllegalArgumentException("no valid template placement preview");
        var bounds = pending.bounds();
        var min = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        checkDestination(player, min, bounds);
        checkExistingInformationBlocks(pending.level(), bounds);
        // Placement can mutate the world before it fails. Never retry the same preview.
        cancel(player);
        if (pending.largeData() != null) {
            PLACEMENTS.put(player, new PlacementJob(pending,
                    new SettlementTemplatePlacement(pending.largeData(), pending.level(), pending.origin(), pending.settings())));
            return "large template placement started: " + pending.id();
        }
        if (!pending.template().placeInWorld(pending.level(), pending.origin(), pending.origin(),
                pending.settings(), pending.level().getRandom(), Block.UPDATE_ALL))
            throw new IllegalArgumentException("template placement failed; create a new preview before retrying");
        return placed(player, pending);
    }

    private static String placed(ServerPlayer player, Placement pending) {
        if (!pending.settings().isIgnoreEntities())
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.hostile_humans_unified.settlement.placement_entity_count", pending.entityEntries()));
        return "placed " + pending.id() + (pending.authorCarrier()
                ? "; open its information block to register the new site" : "");
    }

    static void keepPreview(ServerPlayer player, int ticks, UUID wandToken) {
        var current = ownedPreview(player, wandToken);
        if (current != null)
            PENDING.put(player, new Placement(current.level(), current.wandToken(), current.id(), current.origin(), current.settings(),
                    current.template(), current.largeData(), current.bounds(), current.entityEntries(), current.authorCarrier(), current.level().getGameTime() + ticks));
    }

    static boolean hasPreview(ServerPlayer player, UUID wandToken) {
        return ownedPreview(player, wandToken) != null;
    }

    private static Placement ownedPreview(ServerPlayer player, UUID wandToken) {
        var pending = currentPreview(player);
        return pending != null && Objects.equals(pending.wandToken(), wandToken) ? pending : null;
    }

    static void cancelCommand(ServerPlayer player) {
        if (ownedPreview(player, null) != null) cancel(player);
        cancelCapture(player);
        cancelPlacement(player, true);
    }

    static void cancelWand(ServerPlayer player, UUID wandToken) {
        var pending = PENDING.get(player);
        if (pending == null || wandToken.equals(pending.wandToken())) {
            PENDING.remove(player);
            SettlementNetwork.preview(player, SettlementPreview.EMPTY_PLACEMENT);
        }
    }

    static void cancel(ServerPlayer player) {
        if (PENDING.remove(player) != null) SettlementNetwork.preview(player, SettlementPreview.EMPTY_PLACEMENT);
    }

    static void cancelPlacement(ServerPlayer player, boolean notify) {
        var job = PLACEMENTS.remove(player);
        if (job == null) return;
        if (notify) captureMessage(player, job.work().started() ? "placement_partial" : "placement_cancelled",
                job.preview().id());
        if (job.work().started())
            dev.felix.hostilehumans.bootstrap.HumanRuntime.LOGGER.warn("Interrupted large building placement {}", job.preview().id());
    }

    static void tickPlacement(ServerPlayer player) {
        var job = PLACEMENTS.get(player);
        if (job == null) return;
        var preview = job.preview();
        var bounds = preview.bounds();
        var corner = new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ());
        if (player.serverLevel() != preview.level() || !player.canUseGameMasterBlocks()
                || player.distanceToSqr(Vec3.atCenterOf(corner)) > 4096) {
            cancelPlacement(player, true);
            return;
        }
        try {
            if (job.work().advance()) {
                PLACEMENTS.remove(player);
                captureMessage(player, "placement_result", placed(player, preview));
            } else if (player.tickCount % 20 == 0) {
                captureMessage(player, "placement_progress", preview.id(), job.work().completed(), job.work().total());
            }
        } catch (RuntimeException failure) {
            cancelPlacement(player, true);
            dev.felix.hostilehumans.bootstrap.HumanRuntime.LOGGER.warn("Could not place building template {}", preview.id(), failure);
        }
    }

    static void cancelCapture(ServerPlayer player) {
        CAPTURES.remove(player);
    }

    static void tickCapture(ServerPlayer player) {
        var job = CAPTURES.get(player);
        if (job == null) return;
        var info = SettlementAuthoring.information(player, job.anchor());
        if (player.serverLevel() != job.level() || info == null || info.revision() != job.revision()) {
            cancelCapture(player);
            captureMessage(player, "capture_cancelled", job.id());
            return;
        }
        try {
            var capture = job.capture();
            for (int i = 0; i < 4 && capture.completed() < capture.total(); i++) capture.captureTile();
            if (capture.completed() == capture.total()) {
                cancelCapture(player);
                String result = finishExport(player, job.id(), job.bounds(), job.size(), job.entities(), job.overwrite(),
                        capture.finish(player.getGameProfile().getName()));
                captureMessage(player, "capture_result", result);
            } else if (player.tickCount % 20 == 0) {
                captureMessage(player, "capture_progress", job.id(), capture.completed(), capture.total());
            }
        } catch (RuntimeException invalid) {
            cancelCapture(player);
            captureMessage(player, "capture_failed", job.id(), invalid.getMessage());
        }
    }

    private static void captureMessage(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "gui.hostile_humans_unified.settlement." + key, args));
    }

    static void tick(ServerPlayer player) {
        currentPreview(player);
    }

    private static Placement currentPreview(ServerPlayer player) {
        var pending = PENDING.get(player);
        if (pending != null && (pending.level() != player.serverLevel() || !player.canUseGameMasterBlocks()
                || pending.expires() <= pending.level().getGameTime())) {
            cancel(player);
            return null;
        }
        return pending;
    }

    static SettlementTemplate validatePackedTemplate(CompoundTag data) {
        var size = data.getList("size", Tag.TAG_INT);
        if (size.size() != 3) throw new IllegalArgumentException("missing template size");
        var dimensions = new TemplateTransform.Cell(size.getInt(0), size.getInt(1), size.getInt(2));
        checkSize(dimensions);
        return sanitize(data, dimensions, Rotation.NONE, Mirror.NONE);
    }

    /** Clean copied carriers and normalize authored geometry to the placed structure's minimum corner. */
    private static SettlementTemplate sanitize(CompoundTag data, TemplateTransform.Cell size,
            Rotation rotation, Mirror mirror) {
        return sanitize(data, size, rotation, mirror, true);
    }

    private static SettlementTemplate sanitize(CompoundTag data, TemplateTransform.Cell size,
            Rotation rotation, Mirror mirror, boolean requireCarrier) {
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
        var palette = data.getList("palette", Tag.TAG_COMPOUND);
        String informationId = "hostile_humans_unified:settlement_information";
        int carriers = 0;
        SettlementTemplate carrierAuthor = null;
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.getCompound(i);
            var tag = block.getCompound("nbt");
            var coordinates = block.getList("pos", Tag.TAG_INT);
            if (coordinates.size() != 3 || !localBounds.contains(
                    coordinates.getInt(0), coordinates.getInt(1), coordinates.getInt(2)))
                throw new IllegalArgumentException("template has a block outside its declared size");
            int state = block.getInt("state");
            if (!block.contains("state", Tag.TAG_INT) || state < 0 || state >= palette.size())
                throw new IllegalArgumentException("template has a block state outside its palette");
            boolean informationBlock = informationId.equals(palette.getCompound(state).getString("Name"));
            boolean informationData = informationId.equals(tag.getString("id"));
            if (informationBlock != informationData)
                throw new IllegalArgumentException("information block and its author data do not match");
            if (!informationBlock) continue;
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
                    author.facilities().stream().map(f -> f.transform(shift)).toList(), author.generation());
            carrierAuthor = normalized;
            tag.put(SettlementInformationBlockEntity.AUTHOR, SettlementCodec.template(normalized));
            tag.remove("selection_identity");
            tag.remove("settlement_instance");
            tag.remove("settlement_module");
        }
        if (carriers > 1 || requireCarrier && carriers == 0)
            throw new IllegalArgumentException("author templates require exactly one information block");
        return carrierAuthor;
    }
}
