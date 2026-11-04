package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;

import java.util.List;
import java.util.UUID;

/** Actual NBT/index mutation tests without chunks, entity scans or network. */
public final class SettlementPersistenceTest {
    private static int checks;

    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }

    private static TemplateTransform transform(int x) {
        return new TemplateTransform(
                new TemplateTransform.Cell(x, 64, 100),
                new TemplateTransform.Cell(0, 0, 0),
                TemplateTransform.Mirror.NONE,
                0);
    }

    private static SettlementTemplate author(
            SettlementTemplate.Kind kind, String faction, int revision) {
        return new SettlementTemplate(
                "hostile_humans_unified:test",
                "module",
                kind,
                faction,
                2,
                revision,
                new TemplateTransform.Cell(16, 16, 16),
                new TemplateTransform.Cell(0, 0, 0),
                List.of(
                        new SettlementRegion(
                                "outer",
                                "Outer",
                                SettlementRegion.Layer.OUTER,
                                List.of(new RegionBox(-2, 0, -2, 15, 15, 15)),
                                List.of(new RegionBox(5, 0, 5, 6, 3, 6))),
                        new SettlementRegion(
                                "inner",
                                "Inner",
                                SettlementRegion.Layer.INNER,
                                List.of(new RegionBox(8, 0, 8, 15, 8, 15)),
                                List.of())),
                kind == SettlementTemplate.Kind.ROOT
                        ? List.of(
                                new SettlementFacility(
                                        "control",
                                        SettlementFacility.Type.CONTROL,
                                        "inner",
                                        new TemplateTransform.Cell(9, 0, 9)))
                        : List.of());
    }

    private static SettlementModule module(
            String key, SettlementTemplate.Kind kind, String faction, int x, int revision) {
        return new SettlementModule(
                key, author(kind, faction, revision), transform(x), new BlockPos(x, 64, 100));
    }

    private static void buildingPackRoundTrip(SettlementTemplate original) throws Exception {
        var rules = new SettlementGenerationRules(true, "test:group", 3, "minecraft:overworld",
                List.of("#minecraft:is_forest"), List.of("minecraft:dark_forest"),
                SettlementGenerationRules.Placement.SURFACE, 48, 96, 4,
                SettlementGenerationRules.Water.DRY, SettlementGenerationRules.Terrain.BEARD_THIN,
                5, 24, 7);
        var legacy = rules.write();
        legacy.remove("spacing");
        legacy.remove("separation");
        check(SettlementGenerationRules.read(legacy).spacing() == 32, "legacy rule spacing default");
        var author = new SettlementTemplate(original.template(), original.module(), original.kind(),
                original.faction(), original.tier(), original.revision(), original.size(),
                original.originOffset(), original.regions(), original.facilities(), rules);
        check(SettlementCodec.template(SettlementCodec.template(author)).equals(author),
                "authored generation rules round trip");
        check(author.revision(8).generation().equals(rules), "author revision preserves generation");

        var data = new CompoundTag();
        var size = new ListTag();
        for (int axis : new int[] {16, 16, 16}) size.add(IntTag.valueOf(axis));
        data.put("size", size);
        var palette = new ListTag();
        var state = new CompoundTag();
        state.putString("Name", "hostile_humans_unified:settlement_information");
        palette.add(state);
        data.put("palette", palette);
        var coordinates = new ListTag();
        for (int i = 0; i < 3; i++) coordinates.add(IntTag.valueOf(0));
        var carrier = new CompoundTag();
        carrier.putString("id", "hostile_humans_unified:settlement_information");
        carrier.put(SettlementInformationBlockEntity.AUTHOR, SettlementCodec.template(author));
        var block = new CompoundTag();
        block.put("pos", coordinates);
        block.putInt("state", 0);
        block.put("nbt", carrier);
        var blocks = new ListTag();
        blocks.add(block);
        data.put("blocks", blocks);

        var compressed = new java.io.ByteArrayOutputStream();
        NbtIo.writeCompressed(NbtUtils.addCurrentDataVersion(data), compressed);
        byte[] template = compressed.toByteArray();
        var digest = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(template));
        var manifest = new SettlementBuildingPack.Manifest(1, author.template(), digest);
        var generation = new SettlementBuildingPack.Generation(1, rules,
                new SettlementBuildingPack.Group(24, 7, 123));
        var json = new com.google.gson.Gson();
        var archive = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(archive)) {
            for (var file : java.util.Map.of(
                    "manifest.json", json.toJson(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "generation.json", json.toJson(generation).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    "structure.nbt", template).entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        var pack = SettlementBuildingPack.read(new java.io.ByteArrayInputStream(archive.toByteArray()));
        check(pack.generation().equals(generation), "ZIP generation rules and group round trip");
        check(java.util.Arrays.equals(template, pack.template()), "ZIP preserves exact NBT bytes");
        var id = new net.minecraft.resources.ResourceLocation(author.template());
        var catalog = java.util.Map.of(id, pack);
        var enabled = SettlementWorldgenResources.compile(catalog, java.util.Set.of(id));
        var disabled = SettlementWorldgenResources.compile(catalog, java.util.Set.of());
        check(enabled.size() == 3 && disabled.size() == 2, "disabling only removes generation selection");
        var path = "data/hostile_humans_unified/worldgen/structure_set/building_pack/test/group.json";
        var set = com.google.gson.JsonParser.parseString(new String(enabled.get(path),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        check(set.getAsJsonObject("placement").get("spacing").getAsInt() == 24,
                "compiled structure set uses authored spacing");
        check(set.getAsJsonArray("structures").get(0).getAsJsonObject().get("weight").getAsInt() == 3,
                "compiled structure set uses authored weight");
        SettlementTemplateFiles.checkSize(new TemplateTransform.Cell(128, 128, 64));
        try {
            SettlementTemplateFiles.checkSize(new TemplateTransform.Cell(128, 128, 65));
            throw new AssertionError("configured default volume must reject oversized templates");
        } catch (IllegalArgumentException expected) {
            check(true, "oversized template rejected");
        }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var flag = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        flag.setAccessible(true);
        flag.setBoolean(null, true);
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
        var a = author(SettlementTemplate.Kind.ROOT, "crimson", 1);
        buildingPackRoundTrip(a);
        check(a.faction().equals("hostile_humans_unified:crimson"), "canonical faction ID");
        check(
                SettlementCodec.template(SettlementCodec.template(a)).equals(a),
                "template codec round trip");
        var unknown = SettlementCodec.template(a);
        unknown.putInt("schema", 99);
        boolean rejected = false;
        try {
            SettlementCodec.template(unknown);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected && unknown.getInt("schema") == 99, "unknown author schema not rewritten");
        var index = new SettlementIndex();
        UUID id = UUID.randomUUID();
        var first = module("root", SettlementTemplate.Kind.ROOT, "crimson", 100, 1);
        check(index.importModule(id, "Site", first, 0).changed(), "create root");
        check(
                !index.importModule(id, "Ignored", first, 1).changed()
                        && index.find(id).revision() == 1,
                "repeat import idempotent");
        check(
                index.query(109.5, 64, 109.5).hits().stream()
                        .allMatch(h -> h.layer() == SettlementRegion.Layer.INNER),
                "inner overrides overlapping outer");
        check(index.query(105.5, 64, 105.5).hits().isEmpty(), "exclusion removes road volume");
        check(index.query(98.5, 64, 98.5).hits().size() == 1, "negative local region retained");
        check(index.query(116, 64, 100).hits().isEmpty(), "half-open edge excludes adjacent cell");
        check(
                !index.importModule(
                                id,
                                "Site",
                                module(
                                        "side",
                                        SettlementTemplate.Kind.ATTACHMENT,
                                        "crimson",
                                        132,
                                        1),
                                0)
                        .changed(),
                "stale expected revision rejected");
        check(
                index.importModule(
                                id,
                                "Site",
                                module(
                                        "side",
                                        SettlementTemplate.Kind.ATTACHMENT,
                                        "crimson",
                                        132,
                                        1),
                                1)
                        .changed(),
                "join attachment to actual root");
        check(
                !index.importModule(
                                id,
                                "Site",
                                module(
                                        "second_root",
                                        SettlementTemplate.Kind.ROOT,
                                        "crimson",
                                        164,
                                        1),
                                2)
                        .changed(),
                "conflicting main blocks cannot merge");
        UUID overlap = UUID.randomUUID();
        check(
                index.importModule(
                                overlap,
                                "Other",
                                module("other", SettlementTemplate.Kind.ROOT, "azure", 100, 1),
                                0)
                        .changed(),
                "second site can overlap explicitly");
        check(
                index.query(100.5, 64, 100.5).hits().stream()
                                .map(SettlementIndex.Hit::instance)
                                .distinct()
                                .count()
                        == 2,
                "overlap returns every instance");
        CompoundTag saved = index.save(new CompoundTag());
        var loaded = SettlementIndex.load(saved);
        check(
                loaded.save(new CompoundTag()).equals(saved),
                "complete SavedData round trip without loading chunks");
        check(loaded.rename(id, 2, "Renamed").changed(), "rename uses expected index revision");
        CompoundTag broken = loaded.save(new CompoundTag());
        var list = broken.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
            if (list.getCompound(i).getUUID("id").equals(id))
                list.getCompound(i).putInt("schema", 99);
        var repaired = SettlementIndex.load(broken);
        check(
                repaired.find(id) != null && repaired.find(id).name().equals("Site"),
                "bad revision retains previous valid index");
        check(
                repaired.query(109.5, 64, 109.5).unknown()
                        && repaired.query(109.5, 64, 109.5).hits().stream()
                                .anyMatch(h -> h.layer() == SettlementRegion.Layer.INNER),
                "unknown does not downgrade an inner region");
        check(
                !repaired.save(new CompoundTag())
                        .getList("quarantined", Tag.TAG_COMPOUND)
                        .isEmpty(),
                "bad raw payload preserved for repair");
        var unknownRoot = saved.copy();
        unknownRoot.putInt("schema", 99);
        unknownRoot.putString("future", "retain");
        var future = SettlementIndex.load(unknownRoot);
        check(
                future.save(new CompoundTag()).equals(unknownRoot),
                "unknown SavedData schema round trips unchanged");
        check(
                !future.importModule(UUID.randomUUID(), "New", first, 0).changed(),
                "unknown index refuses write");
        var generated = new SettlementIndex();
        UUID root = UUID.randomUUID();
        check(
                generated
                                .generated(
                                        root,
                                        module(
                                                "side",
                                                SettlementTemplate.Kind.ATTACHMENT,
                                                "crimson",
                                                132,
                                                1))
                                .instance()
                        == null,
                "out-of-order attachment waits for actual root");
        generated = SettlementIndex.load(generated.save(new CompoundTag()));
        check(
                generated.generated(root, first).instance() != null
                        && generated.find(root).modules().size() == 2,
                "pending attachment survives save and joins correct root");
        check(
                !generated
                                .generated(
                                        root,
                                        module(
                                                "root",
                                                SettlementTemplate.Kind.ROOT,
                                                "crimson",
                                                100,
                                                99))
                                .changed()
                        && generated.find(root).modules().get(0).author().revision() == 1,
                "data-pack replacement does not rewrite old generated instance");
        int revision = generated.find(root).revision();
        check(generated.delete(root, revision).changed(), "explicit unregister");
        check(
                !generated
                                .generated(
                                        root,
                                        module(
                                                "root",
                                                SettlementTemplate.Kind.ROOT,
                                                "crimson",
                                                100,
                                                1))
                                .changed()
                        && generated.find(root) == null,
                "late worldgen cannot resurrect explicit unregister");
        check(
                generated.restorePrevious(root, 0).changed()
                        && generated.find(root).revision() > revision,
                "restore preserves monotonic revisions");
        System.out.println(
                "SettlementPersistenceTest: "
                        + checks
                        + " checks passed; server/network/game acceptance remains separate");
    }
}
