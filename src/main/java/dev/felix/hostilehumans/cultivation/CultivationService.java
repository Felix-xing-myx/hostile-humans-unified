package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.progression.SpecialtyTrainingBridge;
import dev.felix.hostilehumans.soldier.RecruitmentLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Server lifecycle and transaction authority shared by commands, the block and the typed panel. */
public final class CultivationService {
    private static CultivationFacilityPolicy policy =
            (server, owner, facility, human, next) -> {
                if (!facility.testMode())
                    return CultivationFacilityPolicy.Access.deny(
                            "Formal N6 integration unavailable");
                var player = server.getPlayerList().getPlayer(owner);
                if (next && (player == null || !player.isCreative()))
                    return CultivationFacilityPolicy.Access.deny(
                            "Explicit creative test requires its investor online");
                if (player != null && !player.isCreative())
                    return CultivationFacilityPolicy.Access.deny("Creative test permission lost");
                return CultivationFacilityPolicy.Access.allow();
            };

    private CultivationService() {}

    public static void setFacilityPolicy(CultivationFacilityPolicy next) {
        policy = Objects.requireNonNull(next);
    }

    static CultivationFacilityPolicy.Access permission(
            MinecraftServer server, UUID owner, CultivationFacility f, Human human, boolean next) {
        if (!f.testMode()
                && !dev.felix.hostilehumans.config.UnifiedConfig.get()
                        .featureEnabled("standing", true))
            return CultivationFacilityPolicy.Access.deny(
                    "Standing disabled; formal training paused");
        if (CultivationData.get(server).retired.contains(f.id()))
            return CultivationFacilityPolicy.Access.deny("Training block retired");
        var level = level(server, f);
        if (level == null)
            return CultivationFacilityPolicy.Access.deny("Training dimension unavailable");
        var pos = new BlockPos(f.x(), f.y(), f.z());
        if (level.hasChunkAt(pos)) {
            if (!(level.getBlockEntity(pos) instanceof CultivationTrainingBlockEntity b)
                    || !same(f, b.reference()))
                return CultivationFacilityPolicy.Access.deny("Training block or revision changed");
        } else if (next)
            return CultivationFacilityPolicy.Access.deny(
                    "Facility unloaded; next period cannot be verified");
        return policy.authorize(server, owner, f, human, next);
    }

    static ServerLevel level(MinecraftServer server, CultivationFacility f) {
        return server.getLevel(
                ResourceKey.create(Registries.DIMENSION, new ResourceLocation(f.dimension())));
    }

    static boolean same(CultivationFacility a, CultivationFacility b) {
        return a.id().equals(b.id())
                && a.revision() == b.revision()
                && a.dimension().equals(b.dimension());
    }

    static Human loaded(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            var e = level.getEntity(id);
            if (e instanceof Human h) return h;
        }
        return null;
    }

    static void own(MinecraftServer server, UUID owner, Human h) {
        if (h == null
                || !h.isAlive()
                || h.isRemoved()
                || !h.hasOwner()
                || !owner.equals(h.getOwnerUUID())
                || !RecruitmentLedger.get(server).isOwnedRecord(h.getUUID(), owner))
            throw new IllegalStateException("Only your formal hired soldier can train");
    }

    static int availableSeat(CultivationData data, UUID facility) {
        boolean[] busy = new boolean[3];
        for (var r : data.records.values())
            if (r.facility.id().equals(facility) && r.occupies()) busy[r.seat] = true;
        for (int i = 0; i < 3; i++) if (!busy[i]) return i;
        throw new IllegalStateException("All three seats occupied");
    }

    static long signature(CultivationPlanner.Candidate c) {
        return CultivationPlanner.signature(c);
    }

    static CultivationRecord ensure(
            CultivationData data, Human human, UUID owner, CultivationFacility f) {
        own(human.getServer(), owner, human);
        var current = SpecialtyTrainingBridge.read(human);
        var record = data.records.get(human.getUUID());
        if (record == null || !record.owner.equals(owner)) {
            if (record != null)
                cancel(data, record, CultivationRuntime.time(human.getServer()), "Owner changed");
            record =
                    new CultivationRecord(
                            human.getUUID(), owner, f, current, human.getDisplayName().getString());
            data.records.put(human.getUUID(), record);
            data.changed();
        } else if (record.cycle == null
                || record.cycle.status() == CultivationCycle.Status.CANCELLED)
            record.profile = current;
        return record;
    }

    static void start(
            MinecraftServer server,
            UUID owner,
            CultivationTrainingBlockEntity block,
            Human human,
            CultivationPlanner.Mode mode,
            int index,
            long sig,
            boolean automatic) {
        var data = CultivationData.get(server);
        data.writable();
        var f = block.reference();
        own(server, owner, human);
        var access = permission(server, owner, f, human, true);
        if (!access.allowed()) throw new IllegalStateException(access.reason());
        if (human.level() != block.getLevel()
                || human.distanceToSqr(f.x() + .5, f.y() + .5, f.z() + .5) > 32 * 32)
            throw new IllegalStateException("Soldier must be near this facility");
        if (human.getTarget() != null && human.getTarget().isAlive())
            throw new IllegalStateException("Threat detected; cannot begin training");
        if (dev.felix.hostilehumans.monitor.HumanMonitorControl.controls(human)
                || dev.felix.hostilehumans.inventory.HumanInventorySession.isEditing(human))
            throw new IllegalStateException("Soldier controlled or inventory open");
        var record = ensure(data, human, owner, f);
        if (record.cycle != null
                && record.cycle.status() != CultivationCycle.Status.CANCELLED
                && record.cycle.status() != CultivationCycle.Status.COMPLETED)
            throw new IllegalStateException("A paid period already exists");
        if (record.pending()) applyPending(server, record, human);
        var profile = SpecialtyTrainingBridge.read(human);
        var candidates =
                CultivationPlanner.preview(
                        profile,
                        SpecialtyTrainingBridge.policy(human, profile.breakthrough()),
                        SpecialtyTrainingBridge.phaseBoundary(human),
                        mode);
        var candidate = candidates.get(index);
        if (signature(candidate) != sig)
            throw new IllegalStateException("Preview changed; request new candidates");
        int seat = availableSeat(data, f.id());
        var account = data.account(owner);
        var reserved = account.at(f.id()).reserve(candidate.materialMicro());
        var cycle =
                CultivationCycle.create(
                        human.getUUID(),
                        profile,
                        mode,
                        candidate,
                        CultivationRuntime.time(server),
                        automatic);
        dev.felix.hostilehumans.behavior.HumanBehaviorControl.cancel(human, "cultivation started");
        // Old movement cleanup occurs before acquiring the new record; command hooks cannot cancel
        // the new period.
        // All fallible solver/ownership/resource checks precede this in-memory commit.
        record.facility = f;
        record.previous = profile;
        record.profile = profile;
        record.appliedRevision = profile.revision();
        record.cycle = cycle;
        record.seat = seat;
        record.reason = "";
        record.name = human.getDisplayName().getString();
        account.put(f.id(), reserved);
        data.changed();
    }

    public static boolean holds(Human h) {
        if (h.level().isClientSide || h.getServer() == null) return false;
        var data = CultivationData.get(h.getServer());
        var r = data.records.get(h.getUUID());
        if (r == null || !r.occupies()) return false;
        long now = CultivationRuntime.time(h.getServer());
        try {
            own(h.getServer(), r.owner, h);
        } catch (IllegalStateException invalid) {
            cancel(data, r, now, invalid.getMessage());
            return false;
        }
        if (dev.felix.hostilehumans.monitor.HumanMonitorControl.controls(h)) {
            cancel(data, r, now, "Player takeover");
            return false;
        }
        if (h.isNoAi()
                || h.isSleeping()
                || h.isPassenger()
                || dev.felix.hostilehumans.inventory.HumanInventorySession.isEditing(h)) {
            pause(data, r, now, "Soldier temporarily unavailable");
            return false;
        }
        if (h.getTarget() != null && h.getTarget().isAlive()) {
            pause(data, r, now, "Threat detected; normal AI released");
            return false;
        }
        var access = permission(h.getServer(), r.owner, r.facility, h, false);
        if (!access.allowed()) {
            pause(data, r, now, access.reason());
            return false;
        }
        return true;
    }

    static net.minecraft.world.phys.Vec3 destination(CultivationRecord r) {
        return new net.minecraft.world.phys.Vec3(
                r.facility.x() + (r.seat - 1) * 1.5 + .5, r.facility.y(), r.facility.z() + 2.5);
    }

    /**
     * Both navigation arrival and post-travel settlement use the same loaded, dry, collision-safe
     * seat.
     */
    static net.minecraft.world.phys.Vec3 safeDestination(Human h, CultivationRecord r) {
        if (!(h.level() instanceof ServerLevel level)
                || !level.dimension().location().toString().equals(r.facility.dimension())
                || r.seat < 0) return null;
        var nominal = destination(r);
        var floor =
                dev.felix.hostilehumans.navigation.SafePositions.nearFloor(
                        level,
                        (int) Math.floor(nominal.x),
                        (int) Math.floor(nominal.z),
                        (int) Math.floor(nominal.y));
        return floor == null
                ? null
                : dev.felix.hostilehumans.navigation.SafePositions.checkedPlacement(
                        h,
                        level,
                        new net.minecraft.world.phys.Vec3(nominal.x, floor.getY(), nominal.z));
    }

    static void joined(Human h) {
        var r = CultivationData.get(h.getServer()).records.get(h.getUUID());
        if (r != null) r.appliedRevision = -1;
    }

    static CultivationRecord record(Human h) {
        return CultivationData.get(h.getServer()).records.get(h.getUUID());
    }

    static void arrived(Human h) {
        var d = CultivationData.get(h.getServer());
        var r = d.records.get(h.getUUID());
        if (r != null && r.cycle != null && r.cycle.status() == CultivationCycle.Status.MOVING) {
            r.cycle = r.cycle.arrived(CultivationRuntime.time(h.getServer()));
            d.changed();
        }
    }

    static void pause(CultivationData d, CultivationRecord r, long now, String why) {
        if (r.cycle == null
                || r.cycle.status() == CultivationCycle.Status.COMPLETED
                || r.cycle.status() == CultivationCycle.Status.CANCELLED) return;
        r.cycle = r.cycle.pause(now, why);
        r.seat = -1;
        r.reason = why;
        d.changed();
    }

    static void cancel(CultivationData d, CultivationRecord r, long now, String why) {
        if (r.cycle == null
                || r.cycle.status() == CultivationCycle.Status.CANCELLED
                || r.cycle.status() == CultivationCycle.Status.COMPLETED) return;
        var a = d.account(r.owner);
        var refund = a.at(r.facility.id()).refund(r.cycle.candidate().materialMicro());
        r.cycle = r.cycle.cancel(now, why);
        r.seat = -1;
        r.reason = why;
        a.put(r.facility.id(), refund);
        d.changed();
    }

    public static void interrupt(Human h, String reason, boolean cancel) {
        if (h.level().isClientSide || h.getServer() == null) return;
        var d = CultivationData.get(h.getServer());
        var r = d.records.get(h.getUUID());
        if (r == null) return;
        if (cancel) cancel(d, r, CultivationRuntime.time(h.getServer()), reason);
        else pause(d, r, CultivationRuntime.time(h.getServer()), reason);
    }

    static void resume(MinecraftServer server, CultivationRecord r, Human h) {
        own(server, r.owner, h);
        if (r.cycle == null || r.cycle.status() != CultivationCycle.Status.PAUSED)
            throw new IllegalStateException("No paused period");
        var a = permission(server, r.owner, r.facility, h, true);
        if (!a.allowed()) throw new IllegalStateException(a.reason());
        int seat = availableSeat(CultivationData.get(server), r.facility.id());
        if (h.getTarget() != null && h.getTarget().isAlive())
            throw new IllegalStateException("Threat remains; cannot resume training");
        dev.felix.hostilehumans.behavior.HumanBehaviorControl.cancel(h, "cultivation resumed");
        r.cycle = r.cycle.resume(CultivationRuntime.time(server));
        r.seat = seat;
        r.reason = "";
        CultivationData.get(server).changed();
    }

    static void tick(MinecraftServer server) {
        var data = CultivationData.get(server);
        long now = CultivationRuntime.time(server);
        int processed = 0;
        for (var r : List.copyOf(data.records.values())) {
            // Exactly one already-confirmed period per record; no while-loop can catch up arbitrary
            // new periods.
            Human h = loaded(server, r.soldier);
            if (h != null) {
                try {
                    own(server, r.owner, h);
                } catch (IllegalStateException invalid) {
                    cancel(data, r, now, invalid.getMessage());
                    continue;
                }
            }
            if (r.pending() && h != null && r.appliedRevision != r.profile.revision()) {
                if (processed++ >= 64) continue;
                try {
                    applyPending(server, r, h);
                    r.appliedRevision = r.profile.revision();
                    data.setDirty();
                    nextAutomatic(server, r, h);
                } catch (RuntimeException invalid) {
                    r.reason = invalid.getMessage();
                    continue;
                }
            }
            if (r.cycle == null
                    || r.cycle.status() != CultivationCycle.Status.ACTIVE
                            && r.cycle.status() != CultivationCycle.Status.READY) continue;
            var access = permission(server, r.owner, r.facility, h, false);
            if (!access.allowed()) {
                pause(data, r, now, access.reason());
                continue;
            }
            if (h != null && !holds(h)) continue;
            if (h != null) {
                var seat = safeDestination(h, r);
                if (seat == null || h.position().distanceToSqr(seat) > .8 * .8) {
                    pause(data, r, now, "Left safe training seat before settlement");
                    continue;
                }
            }
            r.cycle = r.cycle.accrue(now);
            data.setDirty();
            if (r.cycle.status() != CultivationCycle.Status.READY || processed++ >= 64) continue;
            try {
                var result = r.cycle.result();
                var account = data.account(r.owner);
                var paid = account.at(r.facility.id()).settle(r.cycle.candidate().materialMicro());
                var completed = r.cycle.complete(now);
                if (h != null) {
                    CultivationPlanner.validateCurrent(
                            result, SpecialtyTrainingBridge.policy(h, result.breakthrough()));
                    SpecialtyTrainingBridge.apply(h, r.profile, result);
                }
                r.previous = r.profile;
                r.profile = result;
                r.appliedRevision = h == null ? -1 : result.revision();
                r.cycle = completed;
                r.seat = -1;
                account.put(r.facility.id(), paid);
                data.changed();
                if (h != null) nextAutomatic(server, r, h);
            } catch (RuntimeException invalid) {
                pause(data, r, now, invalid.getMessage());
            }
        }
    }

    private static void applyPending(MinecraftServer server, CultivationRecord r, Human h) {
        own(server, r.owner, h);
        var access = permission(server, r.owner, r.facility, h, false);
        if (!access.allowed())
            throw new IllegalStateException(
                    "Committed result awaiting valid identity/facility: " + access.reason());
        SpecialtyTrainingBridge.apply(h, r.previous, r.profile);
        // Keep the completed journal for repeat-application deduplication; no second mutable entity
        // allocation lives here.
    }

    private static void nextAutomatic(MinecraftServer server, CultivationRecord r, Human h) {
        if (!r.cycle.automatic()) return;
        try {
            var access = permission(server, r.owner, r.facility, h, true);
            if (!access.allowed()) throw new IllegalStateException(access.reason());
            var l = level(server, r.facility);
            var p = new BlockPos(r.facility.x(), r.facility.y(), r.facility.z());
            if (l == null
                    || !l.hasChunkAt(p)
                    || !(l.getBlockEntity(p) instanceof CultivationTrainingBlockEntity b))
                throw new IllegalStateException(
                        "Automatic next period waiting for loaded facility");
            var choices =
                    CultivationPlanner.preview(
                            r.profile,
                            SpecialtyTrainingBridge.policy(h, r.profile.breakthrough()),
                            SpecialtyTrainingBridge.phaseBoundary(h),
                            r.cycle.mode());
            int pick =
                    new Random(CultivationRules.mix(r.profile.seed() ^ r.profile.sequence()))
                            .nextInt(3);
            start(server, r.owner, b, h, r.cycle.mode(), pick, signature(choices.get(pick)), true);
        } catch (RuntimeException wait) {
            r.reason = wait.getMessage();
            CultivationData.get(server).setDirty();
        }
    }

    public static void removeFacility(CultivationTrainingBlockEntity b) {
        if (!(b.getLevel() instanceof ServerLevel level)) return;
        var data = CultivationData.get(level.getServer());
        var f = b.reference();
        if (!data.retired.add(f.id())) return;
        for (var r : data.records.values())
            if (r.facility.id().equals(f.id()))
                cancel(
                        data,
                        r,
                        CultivationRuntime.time(level.getServer()),
                        "Training block removed");
        for (var a : data.accounts.values()) {
            Long stock = a.stock.remove(f.id());
            if (stock != null)
                while (stock > 0) {
                    int count = (int) Math.min(64, stock);
                    Containers.dropItemStack(
                            level,
                            f.x() + .5,
                            f.y() + .5,
                            f.z() + .5,
                            new ItemStack(CultivationContent.SUPPLIES.get(), count));
                    stock -= count;
                }
        }
        var stranded = b.strandedInput();
        if (!stranded.isEmpty())
            Containers.dropItemStack(level, f.x() + .5, f.y() + .5, f.z() + .5, stranded);
        data.changed();
    }
}
