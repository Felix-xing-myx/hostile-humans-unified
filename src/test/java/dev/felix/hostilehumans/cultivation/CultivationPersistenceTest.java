package dev.felix.hostilehumans.cultivation;

import net.minecraft.nbt.*;

import java.util.*;

/** Saved journal restart, locked resources, one-period catchup and unknown-payload quarantine. */
public final class CultivationPersistenceTest {
    private static void check(boolean b, String m) {
        if (!b) throw new AssertionError(m);
    }

    private static CultivationData load(CompoundTag tag) throws Exception {
        var m = CultivationData.class.getDeclaredMethod("load", CompoundTag.class);
        m.setAccessible(true);
        return (CultivationData) m.invoke(null, tag);
    }

    public static void main(String[] args) throws Exception {
        UUID soldier = UUID.randomUUID(), owner = UUID.randomUUID(), facility = UUID.randomUUID();
        var profile = CultivationCoreTest.profile(1_370_000, 600_000, false);
        var candidate =
                CultivationPlanner.preview(
                                profile,
                                CultivationCoreTest.policy(false),
                                1_400_000,
                                CultivationPlanner.Mode.GROW)
                        .get(2);
        var record =
                new CultivationRecord(
                        soldier,
                        owner,
                        new CultivationFacility("minecraft:overworld", facility, 0, 64, 0, 0, true),
                        profile,
                        "Test soldier");
        record.cycle =
                CultivationCycle.create(
                                soldier,
                                profile,
                                CultivationPlanner.Mode.GROW,
                                candidate,
                                100,
                                true)
                        .arrived(100)
                        .accrue(60_100);
        record.seat = 1;
        var data = new CultivationData();
        data.records.put(soldier, record);
        data.account(owner)
                .put(facility, new TrainingCredit(1, 0, 0).reserve(candidate.materialMicro()));
        data.runtimeMillis = 60_100;
        var saved = data.save(new CompoundTag());
        var restored = load(saved);
        var r = restored.records.get(soldier);
        check(
                r.cycle.id().equals(record.cycle.id())
                        && r.cycle.elapsed() == 60_000
                        && r.seat == 1,
                "Selected cycle and progress survive restart");
        var credit = restored.account(owner).at(facility);
        check(
                credit.reserved() == 300_000 && credit.available() == 700_000,
                "Reservation survives separately from whole stock");
        var current = r.cycle.accrue(1_000_000);
        check(
                current.status() == CultivationCycle.Status.READY
                        && current.result().sequence() == 1,
                "Offline catchup closes only known period");
        var cancelled = r.cycle.cancel(100_000, "withdrawn");
        check(
                cancelled.status() == CultivationCycle.Status.CANCELLED,
                "Cancel journals terminal state");
        var bad = saved.copy();
        bad.getList("records", Tag.TAG_COMPOUND)
                .getCompound(0)
                .getCompound("cycle")
                .getCompound("candidate")
                .putLong("success_budget", 999);
        var quarantined = load(bad);
        check(
                quarantined.records.isEmpty()
                        && quarantined
                                        .save(new CompoundTag())
                                        .getList("quarantined", Tag.TAG_COMPOUND)
                                        .size()
                                == 1,
                "Corrupt candidate is preserved read-only, not applied");
        var future = new CompoundTag();
        future.putInt("schema", 77);
        future.putString("opaque", "retain exact future payload");
        var unknown = load(future);
        check(unknown.save(new CompoundTag()).equals(future), "Unknown schema exact preservation");
        try {
            unknown.account(owner);
            throw new AssertionError("Unknown writes must fail");
        } catch (IllegalStateException expected) {
        }
        System.out.println(
                "Cultivation journal persistence and unknown-schema preservation passed");
    }
}
