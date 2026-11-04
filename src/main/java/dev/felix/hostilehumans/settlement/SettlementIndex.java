package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Dimension SavedData authority. Only the server thread mutates; queries never load chunks or
 * entities.
 */
public final class SettlementIndex extends SavedData {
    public static final String FILE = "hostile_humans_unified_settlements";
    private static final int SCHEMA = 1, MAX_INDEX_CHUNKS = 8192;
    private final Map<UUID, SettlementInstance> instances = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> previous = new LinkedHashMap<>();
    private final List<CompoundTag> quarantined = new ArrayList<>();
    private final Map<Long, LinkedHashSet<UUID>> chunks = new HashMap<>();
    private final Map<UUID, Set<Long>> memberships = new HashMap<>();
    private CompoundTag unknownRoot;
    private final Map<UUID, List<CompoundTag>> pendingGeneration = new LinkedHashMap<>();

    public record Hit(
            UUID instance,
            int revision,
            String module,
            String region,
            SettlementRegion.Layer layer) {}

    public record Query(List<Hit> hits, boolean unknown) {
        public Query {
            hits = List.copyOf(hits);
        }
    }

    public record Located(
            SettlementInstance instance, SettlementModule module, SettlementFacility facility) {}

    public record Result(boolean changed, String reason, SettlementInstance instance) {}

    public static SettlementIndex get(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("settlement index requires server thread");
        return level.getDataStorage()
                .computeIfAbsent(SettlementIndex::load, SettlementIndex::new, FILE);
    }

    public SettlementInstance find(UUID id) {
        return instances.get(id);
    }

    public boolean uncertain() {
        return unknownRoot != null || !quarantined.isEmpty();
    }

    public List<SettlementInstance> all() {
        return List.copyOf(instances.values());
    }

    private static Set<Long> covered(SettlementInstance value) {
        Set<Long> result = new HashSet<>();
        for (var module : value.modules())
            for (var region : module.regions()) {
                var box = region.envelope();
                for (int x = box.minX() >> 4; x <= (box.maxX() >> 4); x++)
                    for (int z = box.minZ() >> 4; z <= (box.maxZ() >> 4); z++) {
                        result.add(ChunkPos.asLong(x, z));
                        if (result.size() > MAX_INDEX_CHUNKS)
                            throw new IllegalArgumentException("instance spatial index limit");
                    }
            }
        return result;
    }

    private void install(SettlementInstance value, Set<Long> covered) {
        for (long key : memberships.getOrDefault(value.id(), Set.of())) {
            var bucket = chunks.get(key);
            if (bucket != null) {
                bucket.remove(value.id());
                if (bucket.isEmpty()) chunks.remove(key);
            }
        }
        instances.put(value.id(), value);
        memberships.put(value.id(), Set.copyOf(covered));
        for (long key : covered)
            chunks.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value.id());
    }

    public Query query(double x, double y, double z) {
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || Math.abs(x) > 30000000
                || Math.abs(z) > 30000000) return new Query(List.of(), true);
        var bucket =
                chunks.get(ChunkPos.asLong(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4));
        if (bucket == null) return new Query(List.of(), uncertain());
        int limit = SettlementLimits.value(SettlementLimits.QUERY_RESULTS);
        if (bucket.size() > limit) return new Query(List.of(), true);
        var result = new ArrayList<Hit>();
        for (UUID id : bucket) {
            var instance = instances.get(id);
            if (instance == null) continue;
            var local = new ArrayList<Hit>();
            boolean inner = false;
            for (var module : instance.modules())
                for (var region : module.regions())
                    if (region.contains(x, y, z)) {
                        inner |= region.layer() == SettlementRegion.Layer.INNER;
                        local.add(
                                new Hit(
                                        id,
                                        instance.revision(),
                                        module.key(),
                                        region.id(),
                                        region.layer()));
                    }
            boolean preferInner = inner;
            local.removeIf(hit -> preferInner && hit.layer() != SettlementRegion.Layer.INNER);
            result.addAll(local);
        }
        return new Query(result, uncertain());
    }

    /** Task boards inherit the unique registered outer courtyard at their block. */
    public SettlementInstance taskBoardSite(BlockPos anchor) {
        var query = query(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        if (query.unknown()) throw new IllegalArgumentException("unknown region");
        if (query.hits().isEmpty()) throw new IllegalArgumentException("task board has no registered outer courtyard");
        if (query.hits().stream().anyMatch(hit -> hit.layer() == SettlementRegion.Layer.INNER))
            throw new IllegalArgumentException("task board inside inner courtyard");
        UUID id = query.hits().get(0).instance();
        if (query.hits().stream().anyMatch(hit -> !hit.instance().equals(id)))
            throw new IllegalArgumentException("task board overlaps settlements");
        var site = find(id);
        if (site == null || site.incomplete()) throw new IllegalArgumentException("unknown task board instance");
        return site;
    }

    public Located facility(UUID id, String stableId) {
        SettlementInstance instance = find(id);
        if (instance == null || stableId == null) return null;
        for (var module : instance.modules())
            for (var facility : module.facilities())
                if ((module.key() + "/" + facility.id()).equals(stableId))
                    return new Located(instance, module, facility);
        return null;
    }

    /**
     * expected=0 creates; identical repeated module imports do not revise or duplicate the
     * instance.
     */
    public Result importModule(UUID id, String name, SettlementModule module, int expected) {
        if (unknownRoot != null)
            return new Result(false, "unknown index schema; retained read-only", null);
        if (module.author().kind() == SettlementTemplate.Kind.ORDINARY)
            return new Result(false, "ordinary building has no protected instance", null);
        SettlementInstance old = find(id);
        if (old == null
                && (expected != 0 || module.author().kind() != SettlementTemplate.Kind.ROOT))
            return new Result(false, "missing root instance", null);
        if (old != null) {
            var identical =
                    old.modules().stream()
                            .filter(m -> m.key().equals(module.key()))
                            .findFirst()
                            .orElse(null);
            if (identical != null
                    && SettlementCodec.template(identical.author())
                            .equals(SettlementCodec.template(module.author()))
                    && identical.transform().equals(module.transform()))
                return new Result(false, "already imported", old);
            if (old.revision() != expected)
                return new Result(false, "instance revision changed", old);
            if (module.author().kind() == SettlementTemplate.Kind.ROOT
                    && module.author().tier() != old.tier())
                return new Result(
                        false,
                        "instance tier is fixed; create a new explicit instance to change it",
                        old);
        }
        if (old == null && instances.size() >= SettlementLimits.value(SettlementLimits.INSTANCES))
            return new Result(false, "dimension instance limit", null);
        try {
            var modules =
                    new ArrayList<>(old == null ? List.<SettlementModule>of() : old.modules());
            modules.removeIf(m -> m.key().equals(module.key()));
            modules.add(module);
            var value =
                    new SettlementInstance(
                            id,
                            old == null ? name : old.name(),
                            old == null ? module.author().faction() : old.faction(),
                            old == null ? module.author().tier() : old.tier(),
                            old == null ? 1 : Math.addExact(old.revision(), 1),
                            modules,
                            old != null && old.incomplete());
            Set<Long> covered = covered(value);
            if (old != null) previous.put(id, writeInstance(old));
            install(value, covered);
            setDirty();
            return new Result(true, "registered", value);
        } catch (RuntimeException invalid) {
            return new Result(false, invalid.getMessage(), old);
        }
    }

    public Result rename(UUID id, int expected, String name) {
        SettlementInstance old = find(id);
        if (unknownRoot != null || old == null || old.revision() != expected)
            return new Result(false, "missing, unknown or stale instance", old);
        if (name == null
                || name.isBlank()
                || name.length() > 96
                || name.codePoints().anyMatch(Character::isISOControl))
            return new Result(false, "invalid display name", old);
        previous.put(id, writeInstance(old));
        var next = old.rename(name);
        instances.put(id, next);
        setDirty();
        return new Result(true, "renamed", next);
    }

    public Result delete(UUID id, int expected) {
        var old = find(id);
        if (unknownRoot != null || old == null || old.revision() != expected)
            return new Result(false, "missing, unknown or stale instance", old);
        previous.put(id, writeInstance(old));
        for (long key : memberships.getOrDefault(id, Set.of())) {
            var bucket = chunks.get(key);
            if (bucket != null) {
                bucket.remove(id);
                if (bucket.isEmpty()) chunks.remove(key);
            }
        }
        memberships.remove(id);
        instances.remove(id);
        pendingGeneration.remove(id);
        setDirty();
        return new Result(true, "deleted; previous revision retained", old);
    }

    public Result restorePrevious(UUID id, int expected) {
        var current = find(id);
        if (unknownRoot != null || (current == null ? 0 : current.revision()) != expected)
            return new Result(false, "unknown or stale index", current);
        var raw = previous.get(id);
        if (raw == null) return new Result(false, "no previous revision", current);
        try {
            var old = readInstance(raw);
            var restored =
                    new SettlementInstance(
                            id,
                            old.name(),
                            old.faction(),
                            old.tier(),
                            Math.addExact(Math.max(old.revision(), expected), 1),
                            old.modules(),
                            old.incomplete());
            var coverage = covered(restored);
            if (current != null) previous.put(id, writeInstance(current));
            install(restored, coverage);
            setDirty();
            return new Result(true, "previous revision restored", restored);
        } catch (RuntimeException bad) {
            return new Result(false, "previous revision invalid: " + bad.getMessage(), current);
        }
    }

    /**
     * Worldgen is additive and idempotent; replacing a data-pack template never updates old module
     * revisions.
     */
    public Result generated(UUID root, SettlementModule module) {
        if (unknownRoot != null) return new Result(false, "unknown index schema", null);
        var current = find(root);
        if (current == null && previous.containsKey(root))
            return new Result(
                    false, "explicitly unregistered root; automatic recreation refused", null);
        if (current != null
                && current.modules().stream().anyMatch(m -> m.key().equals(module.key())))
            return new Result(false, "existing generated module retained", current);
        if (current == null && module.author().kind() == SettlementTemplate.Kind.ATTACHMENT) {
            var raw = writeModule(module);
            var queued = pendingGeneration.computeIfAbsent(root, k -> new ArrayList<>());
            if (queued.stream().noneMatch(t -> t.getString("key").equals(module.key()))) {
                if (queued.size() >= SettlementLimits.value(SettlementLimits.MODULES)
                        || pendingGeneration.size()
                                > SettlementLimits.value(SettlementLimits.INSTANCES))
                    return new Result(false, "generated pending limit", null);
                queued.add(raw);
                setDirty();
            }
            return new Result(false, "attachment saved pending its real root", null);
        }
        var result =
                importModule(
                        root,
                        module.author()
                                        .faction()
                                        .substring(module.author().faction().indexOf(':') + 1)
                                + " "
                                + root.toString().substring(0, 8),
                        module,
                        current == null ? 0 : current.revision());
        if (result.instance() != null) {
            var pending = pendingGeneration.remove(root);
            if (pending != null) {
                for (var raw : pending)
                    try {
                        var attached = readModule(raw);
                        var active = find(root);
                        if (active != null
                                && active.modules().stream()
                                        .noneMatch(m -> m.key().equals(attached.key()))) {
                            var added =
                                    importModule(root, active.name(), attached, active.revision());
                            if (!added.changed()) quarantined.add(raw.copy());
                        }
                    } catch (RuntimeException invalid) {
                        quarantined.add(raw.copy());
                    }
                setDirty();
            }
        }
        return result;
    }

    public static CompoundTag writeModule(SettlementModule module) {
        var m = new CompoundTag();
        m.putString("key", module.key());
        m.put("author", SettlementCodec.template(module.author()));
        m.put("transform", SettlementCodec.transform(module.transform()));
        m.putLong("information", module.informationBlock().asLong());
        return m;
    }

    private static SettlementModule readModule(CompoundTag m) {
        return new SettlementModule(
                m.getString("key"),
                SettlementCodec.template(m.getCompound("author")),
                SettlementCodec.transform(m.getCompound("transform")),
                BlockPos.of(m.getLong("information")));
    }

    public static CompoundTag writeInstance(SettlementInstance value) {
        var t = new CompoundTag();
        t.putInt("schema", SCHEMA);
        t.putUUID("id", value.id());
        t.putString("name", value.name());
        t.putString("faction", value.faction());
        t.putInt("tier", value.tier());
        t.putInt("revision", value.revision());
        t.putBoolean("incomplete", value.incomplete());
        var modules = new ListTag();
        for (var module : value.modules()) modules.add(writeModule(module));
        t.put("modules", modules);
        return t;
    }

    private static SettlementInstance readInstance(CompoundTag t) {
        if (t.getInt("schema") != SCHEMA || !t.hasUUID("id"))
            throw new IllegalArgumentException("unknown instance schema");
        var list = t.getList("modules", Tag.TAG_COMPOUND);
        if (list.size() > 256) throw new IllegalArgumentException("module load limit");
        var modules = new ArrayList<SettlementModule>();
        for (Tag row : list) modules.add(readModule((CompoundTag) row));
        return new SettlementInstance(
                t.getUUID("id"),
                t.getString("name"),
                t.getString("faction"),
                t.getInt("tier"),
                t.getInt("revision"),
                modules,
                t.getBoolean("incomplete"));
    }

    public static SettlementIndex load(CompoundTag root) {
        var result = new SettlementIndex();
        if (root.getInt("schema") != SCHEMA) {
            result.unknownRoot = root.copy();
            return result;
        }
        var previous = root.getList("previous", Tag.TAG_COMPOUND);
        for (Tag row : previous) {
            var t = (CompoundTag) row;
            if (t.hasUUID("id")) result.previous.put(t.getUUID("id"), t.copy());
        }
        for (Tag row : root.getList("instances", Tag.TAG_COMPOUND)) {
            var raw = (CompoundTag) row;
            try {
                var value = readInstance(raw);
                if (result.instances.containsKey(value.id()))
                    throw new IllegalArgumentException("duplicate instance");
                result.install(value, covered(value));
            } catch (RuntimeException bad) {
                result.quarantined.add(raw.copy());
                var prior = raw.hasUUID("id") ? result.previous.get(raw.getUUID("id")) : null;
                if (prior != null)
                    try {
                        var value = readInstance(prior);
                        if (!result.instances.containsKey(value.id()))
                            result.install(value, covered(value));
                    } catch (RuntimeException ignored) {
                    }
            }
        }
        for (Tag raw : root.getList("quarantined", Tag.TAG_COMPOUND))
            result.quarantined.add(((CompoundTag) raw).copy());
        for (Tag raw : root.getList("pending_generation", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            if (t.hasUUID("root")) {
                var pending =
                        result.pendingGeneration.computeIfAbsent(
                                t.getUUID("root"), k -> new ArrayList<>());
                if (pending.size() < SettlementLimits.value(SettlementLimits.MODULES))
                    pending.add(t.getCompound("module").copy());
                else result.quarantined.add(t.copy());
            } else result.quarantined.add(t.copy());
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (unknownRoot != null) return unknownRoot.copy();
        root.putInt("schema", SCHEMA);
        var values = new ListTag();
        instances.values().forEach(i -> values.add(writeInstance(i)));
        root.put("instances", values);
        var old = new ListTag();
        previous.values().forEach(t -> old.add(t.copy()));
        root.put("previous", old);
        var bad = new ListTag();
        quarantined.forEach(t -> bad.add(t.copy()));
        root.put("quarantined", bad);
        var pending = new ListTag();
        pendingGeneration.forEach(
                (id, list) ->
                        list.forEach(
                                module -> {
                                    var t = new CompoundTag();
                                    t.putUUID("root", id);
                                    t.put("module", module.copy());
                                    pending.add(t);
                                }));
        root.put("pending_generation", pending);
        return root;
    }
}
