package dev.felix.hostilehumans.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * One bounded NBT schema for templates and transformed module copies. No player privileges in
 * author payload.
 */
public final class SettlementCodec {
    private SettlementCodec() {}

    public static TemplateTransform.Cell cell(CompoundTag tag, String key) {
        int[] a = tag.getIntArray(key);
        if (a.length != 3) throw new IllegalArgumentException("missing cell " + key);
        return new TemplateTransform.Cell(a[0], a[1], a[2]);
    }

    public static void cell(CompoundTag tag, String key, TemplateTransform.Cell p) {
        tag.putIntArray(key, new int[] {p.x(), p.y(), p.z()});
    }

    public static RegionBox box(int[] a) {
        if (a.length != 6) throw new IllegalArgumentException("invalid box");
        return new RegionBox(a[0], a[1], a[2], a[3], a[4], a[5]);
    }

    private static List<RegionBox> boxes(ListTag list) {
        if (list.size() > 256) throw new IllegalArgumentException("box list limit");
        var result = new ArrayList<RegionBox>();
        for (Tag t : list) {
            if (!(t instanceof net.minecraft.nbt.IntArrayTag a))
                throw new IllegalArgumentException("box tag");
            result.add(box(a.getAsIntArray()));
        }
        return List.copyOf(result);
    }

    private static ListTag boxes(List<RegionBox> boxes) {
        var result = new ListTag();
        for (var b : boxes)
            result.add(
                    new net.minecraft.nbt.IntArrayTag(
                            new int[] {
                                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()
                            }));
        return result;
    }

    public static SettlementTemplate template(CompoundTag tag) {
        if (tag.getInt("schema") != SettlementTemplate.SCHEMA)
            throw new IllegalArgumentException("unknown author schema; payload retained");
        return new SettlementTemplate(
                tag.getString("template"),
                tag.getString("module"),
                SettlementTemplate.Kind.valueOf(tag.getString("kind")),
                tag.getString("faction"),
                tag.getInt("tier"),
                tag.getInt("revision"),
                cell(tag, "size"),
                cell(tag, "origin_offset"),
                regions(tag.getList("regions", Tag.TAG_COMPOUND)),
                facilities(tag.getList("facilities", Tag.TAG_COMPOUND)),
                SettlementGenerationRules.read(tag.getCompound("generation")),
                tag.getList("broker_factions", Tag.TAG_STRING).stream().map(Tag::getAsString).toList());
    }

    public static CompoundTag template(SettlementTemplate value) {
        var tag = new CompoundTag();
        tag.putInt("schema", SettlementTemplate.SCHEMA);
        tag.putString("template", value.template());
        tag.putString("module", value.module());
        tag.putString("kind", value.kind().name());
        tag.putString("faction", value.faction());
        tag.putInt("tier", value.tier());
        tag.putInt("revision", value.revision());
        cell(tag, "size", value.size());
        cell(tag, "origin_offset", value.originOffset());
        tag.put("regions", regions(value.regions()));
        tag.put("facilities", facilities(value.facilities()));
        if (!value.generation().equals(SettlementGenerationRules.DEFAULT))
            tag.put("generation", value.generation().write());
        if (!value.brokerFactions().isEmpty()) {
            var factions = new ListTag();
            for (String id : value.brokerFactions()) factions.add(net.minecraft.nbt.StringTag.valueOf(id));
            tag.put("broker_factions", factions);
        }
        return tag;
    }

    public static List<SettlementRegion> regions(ListTag list) {
        if (list.size() > 64) throw new IllegalArgumentException("region count limit");
        var result = new ArrayList<SettlementRegion>();
        for (Tag row : list) {
            var t = (CompoundTag) row;
            result.add(
                    new SettlementRegion(
                            t.getString("id"),
                            t.getString("name"),
                            SettlementRegion.Layer.valueOf(t.getString("layer")),
                            boxes(t.getList("include", Tag.TAG_INT_ARRAY)),
                            boxes(t.getList("exclude", Tag.TAG_INT_ARRAY))));
        }
        return List.copyOf(result);
    }

    public static ListTag regions(List<SettlementRegion> regions) {
        var result = new ListTag();
        for (var r : regions) {
            var t = new CompoundTag();
            t.putString("id", r.id());
            t.putString("name", r.name());
            t.putString("layer", r.layer().name());
            t.put("include", boxes(r.includes()));
            t.put("exclude", boxes(r.excludes()));
            result.add(t);
        }
        return result;
    }

    public static List<SettlementFacility> facilities(ListTag list) {
        if (list.size() > 128) throw new IllegalArgumentException("facility count limit");
        var result = new ArrayList<SettlementFacility>();
        for (Tag row : list) {
            var t = (CompoundTag) row;
            result.add(
                    new SettlementFacility(
                            t.getString("id"),
                            SettlementFacility.Type.valueOf(t.getString("type")),
                            t.getString("region"),
                            cell(t, "position")));
        }
        return List.copyOf(result);
    }

    public static ListTag facilities(List<SettlementFacility> facilities) {
        var result = new ListTag();
        for (var f : facilities) {
            var t = new CompoundTag();
            t.putString("id", f.id());
            t.putString("type", f.type().name());
            t.putString("region", f.region());
            cell(t, "position", f.position());
            result.add(t);
        }
        return result;
    }

    public static CompoundTag transform(TemplateTransform transform) {
        var t = new CompoundTag();
        cell(t, "origin", transform.origin());
        cell(t, "pivot", transform.pivot());
        t.putString("mirror", transform.mirror().name());
        t.putInt("turns", transform.clockwiseQuarterTurns());
        return t;
    }

    public static TemplateTransform transform(CompoundTag t) {
        return new TemplateTransform(
                cell(t, "origin"),
                cell(t, "pivot"),
                TemplateTransform.Mirror.valueOf(t.getString("mirror")),
                t.getInt("turns"));
    }
}
