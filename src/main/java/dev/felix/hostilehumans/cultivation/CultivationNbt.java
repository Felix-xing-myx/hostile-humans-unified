package dev.felix.hostilehumans.cultivation;

import net.minecraft.nbt.*;

import java.util.*;

/** Strict version1 codec; callers quarantine the original payload on an unknown/invalid record. */
final class CultivationNbt {
    private CultivationNbt() {}

    static CompoundTag profile(CultivationProfile p) {
        var t = new CompoundTag();
        t.putLong("base", p.baseBudget());
        t.putLong("budget", p.budget());
        t.putLong("potential", p.potential());
        t.putLong("used", p.used());
        t.putLong("seed", p.seed());
        t.putLong("sequence", p.sequence());
        t.putLong("revision", p.revision());
        t.putBoolean("breakthrough", p.breakthrough());
        t.putBoolean("expanded", p.expanded());
        var names = new ListTag();
        for (String n : p.primary()) names.add(StringTag.valueOf(n));
        t.put("primary", names);
        t.put("values", array(p.values()));
        return t;
    }

    static CultivationProfile profile(CompoundTag t) {
        List<String> names = new ArrayList<>();
        for (Tag v : t.getList("primary", Tag.TAG_STRING)) names.add(v.getAsString());
        return new CultivationProfile(
                t.getLong("base"),
                t.getLong("budget"),
                t.getLong("potential"),
                t.getLong("used"),
                t.getLong("seed"),
                t.getLong("sequence"),
                t.getLong("revision"),
                t.getBoolean("breakthrough"),
                t.getBoolean("expanded"),
                names,
                array(t, "values"));
    }

    static CompoundTag facility(CultivationFacility f) {
        var t = new CompoundTag();
        t.putString("dimension", f.dimension());
        t.putUUID("id", f.id());
        t.putInt("x", f.x());
        t.putInt("y", f.y());
        t.putInt("z", f.z());
        t.putLong("revision", f.revision());
        t.putBoolean("test", f.testMode());
        return t;
    }

    static CultivationFacility facility(CompoundTag t) {
        return new CultivationFacility(
                t.getString("dimension"),
                t.getUUID("id"),
                t.getInt("x"),
                t.getInt("y"),
                t.getInt("z"),
                t.getLong("revision"),
                t.getBoolean("test"));
    }

    static CompoundTag candidate(CultivationPlanner.Candidate c) {
        var t = new CompoundTag();
        t.putLong("signature", CultivationPlanner.signature(c));
        t.putInt("index", c.index());
        t.putString("style", c.style());
        t.putLong("step", c.nominalStep());
        t.putLong("space", c.potentialCost());
        t.putLong("material", c.materialMicro());
        t.putLong("duration", c.durationMillis());
        t.putLong("success_budget", c.successBudget());
        t.putLong("failure_budget", c.failureBudget());
        t.put("success", array(c.successValues()));
        t.put("failure", array(c.failureValues()));
        t.put("lower", array(c.lower()));
        t.put("upper", array(c.upper()));
        return t;
    }

    static CultivationPlanner.Candidate candidate(CompoundTag t) {
        long step = t.getLong("step"),
                material = t.getLong("material"),
                duration = t.getLong("duration"),
                space = t.getLong("space");
        if (step < 0
                || step > CultivationRules.STEP
                || material <= 0
                || material > CultivationRules.UNIT
                || space < 0
                || space > 150000
                || duration <= 0
                || duration > CultivationRules.PERIOD_MILLIS
                || t.getInt("index") < 0
                || t.getInt("index") > 2)
            throw new IllegalArgumentException("Invalid saved candidate");
        var c =
                new CultivationPlanner.Candidate(
                        t.getInt("index"),
                        t.getString("style"),
                        step,
                        space,
                        material,
                        duration,
                        t.getLong("success_budget"),
                        t.getLong("failure_budget"),
                        array(t, "success"),
                        array(t, "failure"),
                        array(t, "lower"),
                        array(t, "upper"));
        if (!t.contains("signature") || t.getLong("signature") != CultivationPlanner.signature(c))
            throw new IllegalArgumentException("Saved candidate signature mismatch");
        return c;
    }

    static CompoundTag cycle(CultivationCycle c) {
        var t = new CompoundTag();
        t.putUUID("id", c.id());
        t.put("before", profile(c.before()));
        t.putString("mode", c.mode().name());
        t.put("candidate", candidate(c.candidate()));
        t.putBoolean("success", c.success());
        t.putString("status", c.status().name());
        t.putLong("elapsed", c.elapsed());
        t.putLong("checkpoint", c.checkpoint());
        t.putBoolean("automatic", c.automatic());
        t.putString("reason", c.reason());
        return t;
    }

    static CultivationCycle cycle(CompoundTag t) {
        return new CultivationCycle(
                t.getUUID("id"),
                profile(t.getCompound("before")),
                CultivationPlanner.Mode.valueOf(t.getString("mode")),
                candidate(t.getCompound("candidate")),
                t.getBoolean("success"),
                CultivationCycle.Status.valueOf(t.getString("status")),
                t.getLong("elapsed"),
                t.getLong("checkpoint"),
                t.getBoolean("automatic"),
                t.getString("reason"));
    }

    static CompoundTag record(CultivationRecord r) {
        var t = new CompoundTag();
        t.putInt("schema", 1);
        t.putUUID("soldier", r.soldier);
        t.putUUID("owner", r.owner);
        t.put("facility", facility(r.facility));
        t.put("profile", profile(r.profile));
        t.put("previous", profile(r.previous));
        if (r.cycle != null) t.put("cycle", cycle(r.cycle));
        t.putInt("seat", r.seat);
        t.putString("name", r.name);
        t.putString("reason", r.reason);
        t.putString("command", r.command);
        return t;
    }

    static CultivationRecord record(CompoundTag t) {
        if (t.getInt("schema") != 1) throw new IllegalArgumentException("Unknown training schema");
        var r =
                new CultivationRecord(
                        t.getUUID("soldier"),
                        t.getUUID("owner"),
                        facility(t.getCompound("facility")),
                        profile(t.getCompound("profile")),
                        t.getString("name"));
        if (t.contains("previous", Tag.TAG_COMPOUND))
            r.previous = profile(t.getCompound("previous"));
        if (t.contains("cycle", Tag.TAG_COMPOUND)) r.cycle = cycle(t.getCompound("cycle"));
        r.seat = t.getInt("seat");
        if (r.seat < -1 || r.seat >= 3) throw new IllegalArgumentException("Invalid saved seat");
        r.reason = t.getString("reason");
        r.command = t.getString("command");
        return r;
    }

    static ListTag array(double[] values) {
        var l = new ListTag();
        for (double v : values) l.add(DoubleTag.valueOf(v));
        return l;
    }

    static double[] array(CompoundTag t, String key) {
        var l = t.getList(key, Tag.TAG_DOUBLE);
        if (l.size() != 13) throw new IllegalArgumentException("Unknown allocation dimensions");
        double[] v = new double[13];
        for (int i = 0; i < 13; i++) {
            v[i] = l.getDouble(i);
            if (!Double.isFinite(v[i]) || v[i] < 0)
                throw new IllegalArgumentException("Invalid saved allocation");
        }
        return v;
    }
}
