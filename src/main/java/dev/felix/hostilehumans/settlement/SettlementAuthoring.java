package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All author commits validate a replacement first. Failed/stale drafts never replace the last valid
 * revision.
 */
public final class SettlementAuthoring {
    private SettlementAuthoring() {}

    public static SettlementInformationBlockEntity information(
            ServerPlayer player, BlockPos position) {
        if (!SettlementAccess.author(player)
                || player.distanceToSqr(
                                position.getX() + .5, position.getY() + .5, position.getZ() + .5)
                        > 4096
                || !player.serverLevel().hasChunkAt(position)) return null;
        return player.serverLevel().getBlockEntity(position)
                        instanceof SettlementInformationBlockEntity info
                ? info
                : null;
    }

    public static String save(
            ServerPlayer player, BlockPos position, int expected, CompoundTag payload) {
        var info = information(player, position);
        if (info == null) return "author permission, distance or loaded block failed";
        return commit(info, expected, payload);
    }

    private static String commit(
            SettlementInformationBlockEntity info, int expected, CompoundTag payload) {
        if (info.revision() != expected) return "author revision changed";
        if (!info.author().isEmpty() && info.author().getInt("schema") != SettlementTemplate.SCHEMA)
            return "unknown existing author schema; preserve payload";
        try {
            var value = SettlementCodec.template(payload).revision(Math.addExact(expected, 1));
            info.author(SettlementCodec.template(value));
            return "saved author revision " + value.revision();
        } catch (RuntimeException bad) {
            return "rejected: " + bad.getMessage();
        }
    }

    public static void initialize(ServerPlayer player, BlockPos position) {
        var info = information(player, position);
        if (info == null || !info.author().isEmpty()) return;
        var value =
                new SettlementTemplate(
                        "hostile_humans_unified:untitled",
                        "main",
                        SettlementTemplate.Kind.ROOT,
                        "hostile_humans_unified:crimson",
                        1,
                        1,
                        new TemplateTransform.Cell(16, 16, 16),
                        new TemplateTransform.Cell(0, 0, 0),
                        List.of(
                                new SettlementRegion(
                                        "outer",
                                        "Outer",
                                        SettlementRegion.Layer.OUTER,
                                        List.of(new RegionBox(0, 0, 0, 15, 15, 15)),
                                        List.of())),
                        List.of());
        info.author(SettlementCodec.template(value));
    }

    public static TemplateTransform explicitTransform(
            SettlementTemplate author, BlockPos info, CompoundTag options) {
        int turns = options.getInt("turns");
        if (turns < 0 || turns > 3)
            throw new IllegalArgumentException("rotation must be zero to three quarter turns");
        var mirror =
                TemplateTransform.Mirror.valueOf(
                        options.contains("mirror") ? options.getString("mirror") : "NONE");
        var pivot =
                options.contains("pivot")
                        ? SettlementCodec.cell(options, "pivot")
                        : new TemplateTransform.Cell(0, 0, 0);
        var zero = new TemplateTransform(new TemplateTransform.Cell(0, 0, 0), pivot, mirror, turns);
        var offset = author.originOffset();
        var transformedInformation =
                zero.apply(
                        new TemplateTransform.Cell(
                                Math.negateExact(offset.x()),
                                Math.negateExact(offset.y()),
                                Math.negateExact(offset.z())));
        return new TemplateTransform(
                new TemplateTransform.Cell(
                        Math.subtractExact(info.getX(), transformedInformation.x()),
                        Math.subtractExact(info.getY(), transformedInformation.y()),
                        Math.subtractExact(info.getZ(), transformedInformation.z())),
                pivot,
                mirror,
                turns);
    }

    public static SettlementIndex.Result importNew(
            ServerPlayer player, BlockPos position, int authorRevision, CompoundTag options) {
        var info = information(player, position);
        if (info == null || info.revision() != authorRevision)
            return new SettlementIndex.Result(false, "missing or stale author", null);
        try {
            var author = SettlementCodec.template(info.author());
            if (author.kind() != SettlementTemplate.Kind.ROOT)
                return new SettlementIndex.Result(
                        false, "only a protected ROOT creates an instance", null);
            var index = SettlementIndex.get(player.serverLevel());
            UUID id = UUID.randomUUID();
            String key = UUID.randomUUID().toString();
            var old = info.instance() == null ? null : index.find(info.instance());
            if (old != null) {
                var same =
                        old.modules().stream()
                                .filter(
                                        m ->
                                                m.key().equals(info.module())
                                                        && m.informationBlock().equals(position))
                                .findFirst()
                                .orElse(null);
                if (same != null)
                    return new SettlementIndex.Result(
                            false,
                            "already linked; explicitly update or export this instance",
                            old);
            }
            var transform = explicitTransform(author, position, options);
            String name = options.getString("name");
            if (name.isBlank())
                name =
                        author.faction().substring(author.faction().indexOf(':') + 1)
                                + " "
                                + id.toString().substring(0, 8);
            var result =
                    index.importModule(
                            id, name, new SettlementModule(key, author, transform, position), 0);
            if (result.instance() != null) info.bind(result.instance().id(), key);
            return result;
        } catch (RuntimeException bad) {
            return new SettlementIndex.Result(false, "rejected: " + bad.getMessage(), null);
        }
    }

    public static SettlementIndex.Result join(
            ServerPlayer player,
            BlockPos position,
            int authorRevision,
            UUID target,
            int expected,
            CompoundTag options) {
        var info = information(player, position);
        if (info == null || info.revision() != authorRevision)
            return new SettlementIndex.Result(false, "missing or stale author", null);
        try {
            var author = SettlementCodec.template(info.author());
            if (author.kind() != SettlementTemplate.Kind.ATTACHMENT)
                return new SettlementIndex.Result(
                        false, "only ATTACHMENT may join an existing root", null);
            var transform = explicitTransform(author, position, options);
            var index = SettlementIndex.get(player.serverLevel());
            if (info.instance() != null && !info.instance().equals(target)) {
                var prior = index.find(info.instance());
                if (prior != null
                        && prior.modules().stream()
                                .anyMatch(
                                        m ->
                                                m.key().equals(info.module())
                                                        && m.informationBlock().equals(position)))
                    return new SettlementIndex.Result(
                            false,
                            "already belongs to another instance; explicit migration is required",
                            prior);
            }
            var targetInstance = index.find(target);
            boolean same =
                    info.instance() != null
                            && info.instance().equals(target)
                            && targetInstance != null
                            && targetInstance.modules().stream()
                                    .anyMatch(
                                            m ->
                                                    m.key().equals(info.module())
                                                            && m.informationBlock()
                                                                    .equals(position));
            String key = same ? info.module() : UUID.randomUUID().toString();
            var result =
                    index.importModule(
                            target,
                            "",
                            new SettlementModule(key, author, transform, position),
                            expected);
            if (result.instance() != null
                    && (result.changed() || result.reason().equals("already imported")))
                info.bind(target, key);
            return result;
        } catch (RuntimeException bad) {
            return new SettlementIndex.Result(false, "rejected: " + bad.getMessage(), null);
        }
    }

    public static String export(
            ServerPlayer player, BlockPos position, int authorRevision, int expected) {
        var info = information(player, position);
        if (info == null || info.revision() != authorRevision || info.instance() == null)
            return "missing or stale author link";
        var instance = SettlementIndex.get(player.serverLevel()).find(info.instance());
        if (instance == null || instance.revision() != expected) return "instance revision changed";
        if (!info.author().isEmpty() && info.author().getInt("schema") != SettlementTemplate.SCHEMA)
            return "unknown author schema retained; do not overwrite";
        var module =
                instance.modules().stream()
                        .filter(
                                m ->
                                        m.key().equals(info.module())
                                                && m.informationBlock().equals(position))
                        .findFirst()
                        .orElse(null);
        if (module == null) return "module reference is not at this information block";
        // Local author geometry already carries the actual transform; rebuild its origin offset for
        // this carrier.
        var local =
                module.transform()
                        .unapply(
                                new TemplateTransform.Cell(
                                        position.getX(), position.getY(), position.getZ()));
        var a = module.author();
        var author =
                new SettlementTemplate(
                        a.template(),
                        a.module(),
                        a.kind(),
                        a.faction(),
                        a.tier(),
                        Math.addExact(authorRevision, 1),
                        a.size(),
                        new TemplateTransform.Cell(
                                Math.negateExact(local.x()),
                                Math.negateExact(local.y()),
                                Math.negateExact(local.z())),
                        a.regions(),
                        a.facilities());
        info.author(SettlementCodec.template(author));
        return "refreshed author data from authoritative index";
    }

    public static SettlementIndex.Result update(
            ServerPlayer player,
            BlockPos position,
            int authorRevision,
            int expected,
            CompoundTag options) {
        var info = information(player, position);
        if (info == null || info.instance() == null || info.revision() != authorRevision)
            return new SettlementIndex.Result(false, "missing or stale author link", null);
        try {
            var index = SettlementIndex.get(player.serverLevel());
            var old = index.find(info.instance());
            if (old == null
                    || old.modules().stream()
                            .noneMatch(
                                    m ->
                                            m.key().equals(info.module())
                                                    && m.informationBlock().equals(position)))
                return new SettlementIndex.Result(false, "module reference mismatch", old);
            var author = SettlementCodec.template(info.author());
            if (!options.contains("turns")) {
                var prior =
                        old.modules().stream()
                                .filter(m -> m.key().equals(info.module()))
                                .findFirst()
                                .orElseThrow()
                                .transform();
                options = options.copy();
                options.putInt("turns", prior.clockwiseQuarterTurns());
                options.putString("mirror", prior.mirror().name());
                SettlementCodec.cell(options, "pivot", prior.pivot());
            }
            return index.importModule(
                    old.id(),
                    old.name(),
                    new SettlementModule(
                            info.module(),
                            author,
                            explicitTransform(author, position, options),
                            position),
                    expected);
        } catch (RuntimeException bad) {
            return new SettlementIndex.Result(false, "rejected: " + bad.getMessage(), null);
        }
    }

    public static String restore(
            ServerPlayer player, BlockPos position, UUID target, String moduleKey, int expected) {
        var info = information(player, position);
        if (info == null)
            return "place an information block at the original loaded carrier position";
        if (!info.author().isEmpty() && info.author().getInt("schema") != SettlementTemplate.SCHEMA)
            return "unknown author payload retained";
        var instance = SettlementIndex.get(player.serverLevel()).find(target);
        if (instance == null || instance.revision() != expected) return "instance revision changed";
        var module =
                instance.modules().stream()
                        .filter(
                                m ->
                                        m.key().equals(moduleKey)
                                                && m.informationBlock().equals(position))
                        .findFirst()
                        .orElse(null);
        if (module == null) return "module does not belong at this carrier position";
        var author = module.author().revision(Math.addExact(info.revision(), 1));
        info.author(SettlementCodec.template(author));
        info.bind(target, moduleKey);
        return "restored author carrier from index; template export may now proceed";
    }

    /**
     * Preserve actual placement orientation; authored origin edits remain anchored to this
     * information block.
     */
    static TemplateTransform currentTransform(
            ServerPlayer player, SettlementInformationBlockEntity info) {
        var author = SettlementCodec.template(info.author());
        if (info.instance() == null)
            return explicitTransform(author, info.getBlockPos(), new CompoundTag());
        var instance = SettlementIndex.get(player.serverLevel()).find(info.instance());
        if (instance == null)
            throw new IllegalArgumentException(
                    "bound instance unavailable; restore it or use a fresh author block");
        var module =
                instance.modules().stream()
                        .filter(
                                m ->
                                        m.key().equals(info.module())
                                                && m.informationBlock().equals(info.getBlockPos()))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("bound module unavailable"));
        return explicitTransform(
                author, info.getBlockPos(), SettlementCodec.transform(module.transform()));
    }

    static TemplateTransform.Cell cell(BlockPos point) {
        return new TemplateTransform.Cell(point.getX(), point.getY(), point.getZ());
    }

    static String setBounds(ServerPlayer player, SettlementInformationBlockEntity info,
            BlockPos a, BlockPos b) {
        var author = SettlementCodec.template(info.author());
        var placement = currentTransform(player, info);
        if (placement.clockwiseQuarterTurns() != 0 || placement.mirror() != TemplateTransform.Mirror.NONE)
            throw new IllegalArgumentException("use an unrotated author copy to change capture bounds");
        var box = RegionBox.between(cell(a), cell(b));
        var origin = new BlockPos(box.minX(), box.minY(), box.minZ());
        var carrier = info.getBlockPos();
        if (!box.contains(carrier.getX(), carrier.getY(), carrier.getZ()))
            throw new IllegalArgumentException("capture bounds must include the information block");
        var size = new TemplateTransform.Cell((int) box.sizeX(), (int) box.sizeY(), (int) box.sizeZ());
        SettlementTemplateFiles.checkSize(size);
        var shift = new TemplateTransform(
                new TemplateTransform.Cell(placement.origin().x() - origin.getX(),
                        placement.origin().y() - origin.getY(), placement.origin().z() - origin.getZ()),
                cell(BlockPos.ZERO), TemplateTransform.Mirror.NONE, 0);
        var updated = new SettlementTemplate(author.template(), author.module(), author.kind(),
                author.faction(), author.tier(), author.revision(), size, cell(origin.subtract(carrier)),
                author.regions().stream().map(r -> r.transform(shift)).toList(),
                author.facilities().stream().map(f -> f.transform(shift)).toList());
        return commit(info, info.revision(), SettlementCodec.template(updated));
    }

    static String bindFacility(ServerPlayer player, SettlementInformationBlockEntity info,
            BlockPos point, String preferredRegion, int facilityIndex) {
        var author = SettlementCodec.template(info.author());
        if (facilityIndex >= author.facilities().size())
            throw new IllegalArgumentException("selected facility missing");
        var selected = facilityIndex < 0 ? null : author.facilities().get(facilityIndex);
        var block = player.serverLevel().getBlockState(point).getBlock();
        SettlementFacility.Type type;
        if (block instanceof SettlementControlBlock control) {
            type = SettlementFacility.Type.CONTROL;
            var faction = author.faction();
            if (!faction.equals(dev.felix.hostilehumans.bootstrap.HumanRuntime.MOD_ID + ":" + control.faction()))
                throw new IllegalArgumentException("control block faction does not match the template");
        } else if (block == dev.felix.hostilehumans.faction.FactionContent.TRIBUTE_CHEST.get()) {
            type = SettlementFacility.Type.SUPPLY;
        } else if (block == dev.felix.hostilehumans.economy.EconomyContent.TRADER.get()) {
            type = SettlementFacility.Type.TRADER;
        } else if (block == dev.felix.hostilehumans.cultivation.CultivationContent.TRAINING_BLOCK.get()) {
            type = SettlementFacility.Type.TRAINING;
        } else if (selected != null && (selected.type() == SettlementFacility.Type.ENTRANCE
                || selected.type() == SettlementFacility.Type.POST)) {
            type = selected.type();
        } else {
            throw new IllegalArgumentException("select a control, supply, trader or training block");
        }
        if (selected != null && selected.type() != type)
            throw new IllegalArgumentException("selected block does not match this facility type");
        var local = currentTransform(player, info).unapply(cell(point));
        var candidates = author.regions().stream()
                .filter(r -> r.contains(local.x() + .5, local.y(), local.z() + .5))
                .filter(r -> switch (type) {
                    case SUPPLY, TRADER -> r.layer() == SettlementRegion.Layer.OUTER;
                    case TRAINING -> r.layer() == SettlementRegion.Layer.INNER && author.tier() >= 2;
                    default -> true;
                }).toList();
        var region = candidates.stream().filter(r -> r.id().equals(preferredRegion)).findFirst()
                .orElse(candidates.size() == 1 ? candidates.get(0) : null);
        if (region == null) throw new IllegalArgumentException(candidates.isEmpty()
                ? "no eligible region at this block"
                : "select the intended region first: " + candidates.stream().map(SettlementRegion::id).toList());
        var facilities = new ArrayList<>(author.facilities());
        var existing = facilities.stream().filter(f -> f.type() == type
                && (type == SettlementFacility.Type.CONTROL || f.position().equals(local))).findFirst();
        if (selected != null) {
            if (existing.isPresent() && !existing.get().id().equals(selected.id()))
                throw new IllegalArgumentException("another facility already uses this position");
            existing = java.util.Optional.of(selected);
        }
        String id = existing.map(SettlementFacility::id).orElseGet(
                () -> "facility_" + UUID.randomUUID().toString().replace("-", ""));
        existing.ifPresent(facilities::remove);
        facilities.add(new SettlementFacility(id, type, region.id(), local));
        var updated = new SettlementTemplate(author.template(), author.module(), author.kind(),
                author.faction(), author.tier(), author.revision(), author.size(), author.originOffset(),
                author.regions(), facilities);
        return commit(info, info.revision(), SettlementCodec.template(updated));
    }

    public static String addBox(
            ServerPlayer player,
            BlockPos position,
            String regionId,
            boolean exclude,
            boolean replace,
            int boxIndex,
            BlockPos a,
            BlockPos b) {
        var info = SettlementSelectionTool.boundInformation(player, position);
        if (info == null) return "author unavailable";
        try {
            var author = SettlementCodec.template(info.author());
            // Editing takes place in the author world. Imported geometry is refreshed before
            // rebinding here.
            var placement = currentTransform(player, info);
            var box =
                    RegionBox.between(
                            placement.unapply(
                                    new TemplateTransform.Cell(a.getX(), a.getY(), a.getZ())),
                            placement.unapply(
                                    new TemplateTransform.Cell(b.getX(), b.getY(), b.getZ())));
            var regions = new ArrayList<>(author.regions());
            int at = -1;
            for (int i = 0; i < regions.size(); i++)
                if (regions.get(i).id().equals(regionId)) {
                    at = i;
                    break;
                }
            if (at < 0) return "selected region missing";
            var r = regions.get(at);
            var includes = new ArrayList<>(r.includes());
            var excludes = new ArrayList<>(r.excludes());
            var target = exclude ? excludes : includes;
            if (boxIndex >= 0) {
                if (boxIndex >= target.size()) return "selected box missing";
                target.set(boxIndex, box);
            } else {
                if (replace) target.clear();
                target.add(box);
            }
            regions.set(at, new SettlementRegion(r.id(), r.name(), r.layer(), includes, excludes));
            var updated =
                    new SettlementTemplate(
                            author.template(),
                            author.module(),
                            author.kind(),
                            author.faction(),
                            author.tier(),
                            author.revision(),
                            author.size(),
                            author.originOffset(),
                            regions,
                            author.facilities());
            return commit(info, info.revision(), SettlementCodec.template(updated));
        } catch (RuntimeException bad) {
            return "rejected: " + bad.getMessage();
        }
    }
}
