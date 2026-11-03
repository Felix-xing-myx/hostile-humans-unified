package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.progression.SpecialtyTrainingBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** One actor/distance/facility/revision/idempotency gate for every panel and command operation. */
public final class CultivationFacade {
    public static final UUID NONE = new UUID(0, 0);

    private static final class Session {
        final UUID token = UUID.randomUUID();
        final CultivationFacility facility;
        long expires, lastOperation = Long.MIN_VALUE;
        CultivationPlanner.Mode mode = CultivationPlanner.Mode.GROW;
        UUID selected;

        Session(CultivationFacility f, long time) {
            facility = f;
            expires = time + 30_000;
        }
    }

    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();

    private CultivationFacade() {}

    public static void open(ServerPlayer player, CultivationTrainingBlockEntity block) {
        if (block.getLevel() != player.level()
                || player.distanceToSqr(block.getBlockPos().getCenter()) > 64
                || !player.isAlive()
                || player.isSpectator()) return;
        var session = new Session(block.reference(), CultivationRuntime.time(player.server));
        SESSIONS.put(player, session);
        reply(player, session, "", NONE);
    }

    public static void depositHeld(ServerPlayer p, CultivationTrainingBlockEntity b, int count) {
        var s = SESSIONS.get(p);
        if (s == null) return;
        handle(
                p,
                new CultivationNetwork.Request(
                        s.token,
                        UUID.randomUUID(),
                        CultivationData.get(p.server).revision,
                        NONE,
                        CultivationNetwork.Action.DEPOSIT,
                        0,
                        0,
                        CultivationNetwork.Mode.GROW,
                        false,
                        List.of(),
                        Math.min(64, count)));
    }

    public static void command(
            ServerPlayer p,
            CultivationNetwork.Action action,
            UUID target,
            CultivationNetwork.Mode mode,
            int index,
            boolean automatic,
            List<String> primary,
            int amount) {
        var s = SESSIONS.get(p);
        if (s == null) {
            p.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Open a training block first"),
                    true);
            return;
        }
        long signature = 0;
        try {
            if (action == CultivationNetwork.Action.START) {
                var h = target(p, s, target);
                var profile = SpecialtyTrainingBridge.read(h);
                var c =
                        CultivationPlanner.preview(
                                        profile,
                                        SpecialtyTrainingBridge.policy(h, profile.breakthrough()),
                                        SpecialtyTrainingBridge.phaseBoundary(h),
                                        CultivationPlanner.Mode.valueOf(mode.name()))
                                .get(index);
                signature = CultivationService.signature(c);
            }
            handle(
                    p,
                    new CultivationNetwork.Request(
                            s.token,
                            UUID.randomUUID(),
                            CultivationData.get(p.server).revision,
                            target,
                            action,
                            index,
                            signature,
                            mode,
                            automatic,
                            primary,
                            amount));
        } catch (RuntimeException failure) {
            reply(p, s, failure.getMessage(), NONE);
        }
    }

    private static CultivationTrainingBlockEntity block(ServerPlayer p, Session s) {
        var f = s.facility;
        if (!p.isAlive()
                || p.isSpectator()
                || !p.level().dimension().location().toString().equals(f.dimension())
                || p.distanceToSqr(f.x() + .5, f.y() + .5, f.z() + .5) > 64)
            throw new IllegalStateException("Move near this training block");
        var pos = new BlockPos(f.x(), f.y(), f.z());
        if (!p.serverLevel().hasChunkAt(pos)
                || !(p.serverLevel().getBlockEntity(pos)
                        instanceof CultivationTrainingBlockEntity b)
                || !CultivationService.same(f, b.reference()))
            throw new IllegalStateException("Training facility or revision changed");
        return b;
    }

    public static void handle(ServerPlayer p, CultivationNetwork.Request request) {
        Session s = SESSIONS.get(p);
        if (s == null || !s.token.equals(request.token())) {
            CultivationNetwork.snapshot(
                    p,
                    new CultivationSnapshot(
                            request.token(),
                            p.level().dimension().location().toString(),
                            p.getBlockX(),
                            p.getBlockY(),
                            p.getBlockZ(),
                            0,
                            0,
                            false,
                            new CultivationSnapshot.AccountView(0, 0, 0),
                            List.of(),
                            List.of(),
                            "Training session unavailable; reopen block",
                            request.requestId()));
            return;
        }
        String reason = "";
        try {
            var b = block(p, s);
            var data = CultivationData.get(p.server);
            data.writable();
            if (CultivationRuntime.time(p.server) > s.expires)
                throw new IllegalStateException("Training session expired; reopen block");
            s.expires = CultivationRuntime.time(p.server) + 30_000;
            if (data.seen(request.requestId(), p.getUUID())) {
                reply(p, s, "Already processed", request.requestId());
                return;
            }
            if (request.action() != CultivationNetwork.Action.REFRESH
                    && request.action() != CultivationNetwork.Action.PREVIEW
                    && request.expectedRevision() != data.revision)
                throw new IllegalStateException("Training state changed; refreshed");
            boolean mutation =
                    request.action() != CultivationNetwork.Action.REFRESH
                            && request.action() != CultivationNetwork.Action.PREVIEW;
            if (mutation) {
                long tick = p.server.getTickCount();
                if (s.lastOperation != Long.MIN_VALUE && tick - s.lastOperation < 2)
                    throw new IllegalStateException("Training request too fast");
                s.lastOperation = tick;
            }
            s.mode = CultivationPlanner.Mode.valueOf(request.mode().name());
            if (!request.target().equals(NONE)) s.selected = request.target();
            switch (request.action()) {
                case REFRESH -> {
                    reply(p, s, "", request.requestId());
                    return;
                }
                case PREVIEW -> {
                    target(p, s, request.target());
                    reply(p, s, "", request.requestId());
                    return;
                }
                case DEPOSIT -> {
                    access(p, s, null, true);
                    deposit(p, b, request.amount());
                }
                case WITHDRAW -> withdraw(p, s, request.amount());
                case START -> {
                    Human h = target(p, s, request.target());
                    CultivationService.start(
                            p.server,
                            p.getUUID(),
                            b,
                            h,
                            s.mode,
                            request.candidateIndex(),
                            request.previewSignature(),
                            request.automatic());
                }
                case PAUSE, CANCEL, RESUME -> {
                    var record = ownedRecord(p, s, request.target());
                    if (request.action() == CultivationNetwork.Action.PAUSE)
                        CultivationService.pause(
                                data,
                                record,
                                CultivationRuntime.time(p.server),
                                "Paused by investor");
                    else if (request.action() == CultivationNetwork.Action.CANCEL)
                        CultivationService.cancel(
                                data,
                                record,
                                CultivationRuntime.time(p.server),
                                "Cancelled by investor");
                    else {
                        Human h = target(p, s, request.target());
                        CultivationService.resume(p.server, record, h);
                    }
                }
                case CONFIRM_BREAKTHROUGH, SET_PRIMARY, USE_POTENTIAL, EXPAND_PRIMARY ->
                        mutateProfile(p, s, b, request);
            }
            data.remember(request.requestId(), p.getUUID());
        } catch (RuntimeException failure) {
            reason =
                    failure.getMessage() == null
                            ? "Training operation rejected"
                            : failure.getMessage();
        }
        if (!reason.isEmpty())
            p.displayClientMessage(net.minecraft.network.chat.Component.literal(reason), true);
        reply(p, s, reason, request.requestId());
    }

    private static void access(ServerPlayer p, Session s, Human h, boolean next) {
        var a = CultivationService.permission(p.server, p.getUUID(), s.facility, h, next);
        if (!a.allowed()) throw new IllegalStateException(a.reason());
    }

    private static Human target(ServerPlayer p, Session s, UUID id) {
        Human h = p.serverLevel().getEntity(id) instanceof Human found ? found : null;
        CultivationService.own(p.server, p.getUUID(), h);
        if (h.level() != p.level()
                || h.distanceToSqr(s.facility.x() + .5, s.facility.y() + .5, s.facility.z() + .5)
                        > 32 * 32) throw new IllegalStateException("Choose your nearby soldier");
        return h;
    }

    private static CultivationRecord ownedRecord(ServerPlayer p, Session s, UUID target) {
        var r = CultivationData.get(p.server).records.get(target);
        if (r == null || !r.owner.equals(p.getUUID()) || !r.facility.id().equals(s.facility.id()))
            throw new IllegalStateException("No owned training record here");
        return r;
    }

    private static void mutateProfile(
            ServerPlayer p,
            Session s,
            CultivationTrainingBlockEntity b,
            CultivationNetwork.Request request) {
        Human h = target(p, s, request.target());
        access(p, s, h, true);
        var data = CultivationData.get(p.server);
        var r = CultivationService.ensure(data, h, p.getUUID(), s.facility);
        if (r.cycle != null
                && r.cycle.status() != CultivationCycle.Status.CANCELLED
                && r.cycle.status() != CultivationCycle.Status.COMPLETED)
            throw new IllegalStateException(
                    "Cancel or finish the locked period before changing training parameters");
        var before = SpecialtyTrainingBridge.read(h);
        CultivationProfile next;
        TrainingCredit credit = null;
        net.minecraft.world.item.Item item = null;
        switch (request.action()) {
            case CONFIRM_BREAKTHROUGH -> {
                if (before.breakthrough())
                    throw new IllegalStateException("Breakthrough already confirmed");
                if (before.budget() + 2 < SpecialtyTrainingBridge.phaseBoundary(h))
                    throw new IllegalStateException("Reach the current phase boundary first");
                next = before.breakthrough(true);
            }
            case SET_PRIMARY -> {
                if (!CultivationRules.DIMENSIONS.containsAll(request.primary()))
                    throw new IllegalStateException("Unknown primary dimension");
                if (before.primary().equals(request.primary()))
                    throw new IllegalStateException("Primary selection unchanged");
                next = before.primary(request.primary());
                credit =
                        data.account(p.getUUID())
                                .at(s.facility.id())
                                .reserve(CultivationRules.UNIT)
                                .settle(CultivationRules.UNIT);
            }
            case USE_POTENTIAL -> {
                item = CultivationContent.POTENTIAL.get();
                requireItems(p, item, 1);
                next = before.extendPotential();
            }
            case EXPAND_PRIMARY -> {
                item = CultivationContent.EXPANSION.get();
                requireItems(p, item, 1);
                next = before.expand();
            }
            default -> throw new IllegalStateException("Unsupported profile operation");
        }
        SpecialtyTrainingBridge.apply(h, before, next);
        r.previous = before;
        r.profile = next;
        r.appliedRevision = next.revision();
        if (credit != null) data.account(p.getUUID()).put(s.facility.id(), credit);
        if (item != null) removeItems(p, item, 1);
        data.changed();
        if (item != null) p.containerMenu.broadcastChanges();
    }

    private static void requireItems(
            ServerPlayer p, net.minecraft.world.item.Item item, int count) {
        int found = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            var stack = p.getInventory().getItem(i);
            if (stack.is(item)) found += stack.getCount();
        }
        if (count <= 0 || found < count) throw new IllegalStateException("Required items missing");
    }

    private static void removeItems(ServerPlayer p, net.minecraft.world.item.Item item, int count) {
        for (int i = 0; i < p.getInventory().getContainerSize() && count > 0; i++) {
            var stack = p.getInventory().getItem(i);
            if (stack.is(item)) {
                int n = Math.min(count, stack.getCount());
                stack.shrink(n);
                count -= n;
            }
        }
        p.getInventory().setChanged();
    }

    private static void deposit(ServerPlayer p, CultivationTrainingBlockEntity b, int count) {
        if (count <= 0 || count > 64)
            throw new IllegalStateException("Deposit 1..64 supplies per action");
        requireItems(p, CultivationContent.SUPPLIES.get(), count);
        var d = CultivationData.get(p.server);
        var a = d.account(p.getUUID());
        var credited = a.at(b.reference().id()).deposit(count);
        b.intake(count);
        removeItems(p, CultivationContent.SUPPLIES.get(), count);
        a.put(b.reference().id(), credited);
        b.finishIntake();
        d.changed();
        p.containerMenu.broadcastChanges();
    }

    private static void withdraw(ServerPlayer p, Session s, int count) {
        if (count <= 0 || count > 64)
            throw new IllegalStateException("Withdraw 1..64 supplies per action");
        var d = CultivationData.get(p.server);
        var a = d.account(p.getUUID());
        var after = a.at(s.facility.id()).withdraw(count);
        var stack = new ItemStack(CultivationContent.SUPPLIES.get(), count);
        a.put(s.facility.id(), after);
        d.changed();
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        p.containerMenu.broadcastChanges();
    }

    private static String shortName(String n) {
        return n.length() > 128 ? n.substring(0, 128) : n;
    }

    private static CultivationSnapshot.CandidateView view(CultivationPlanner.Candidate c) {
        return new CultivationSnapshot.CandidateView(
                c.index(),
                c.style(),
                CultivationService.signature(c),
                c.nominalStep() / 1e6,
                c.potentialCost() / 1e6,
                c.materialMicro() / 1e6,
                c.durationMillis(),
                c.successBudget() / 1e6,
                c.failureBudget() / 1e6,
                c.successValues(),
                c.failureValues(),
                c.lower(),
                c.upper());
    }

    private static CultivationSnapshot.SoldierView soldier(
            ServerPlayer player, Session session, UUID id, Human h, CultivationRecord record) {
        try {
            var p = h == null ? record.profile : SpecialtyTrainingBridge.read(h);
            String reason = "";
            double[] lower = p.values(), upper = p.values();
            List<CultivationSnapshot.CandidateView> candidates = List.of();
            if (h != null)
                try {
                    var policy = SpecialtyTrainingBridge.policy(h, p.breakthrough());
                    var bounds = policy.bounds(p.budget() / 1e6);
                    lower = bounds.lower();
                    upper = bounds.upper();
                    if (id.equals(session.selected))
                        candidates =
                                CultivationPlanner.preview(
                                                p,
                                                policy,
                                                SpecialtyTrainingBridge.phaseBoundary(h),
                                                session.mode)
                                        .stream()
                                        .map(CultivationFacade::view)
                                        .toList();
                } catch (RuntimeException invalid) {
                    reason = invalid.getMessage();
                }
            else reason = "Soldier unloaded; current confirmed period only";
            CultivationSnapshot.CycleView cycle = null;
            if (record != null && record.cycle != null) {
                var c = record.cycle;
                cycle =
                        new CultivationSnapshot.CycleView(
                                c.id(),
                                c.status().name(),
                                c.elapsed(),
                                c.candidate().durationMillis(),
                                c.automatic(),
                                c.candidate().index(),
                                record.reason.isEmpty() ? c.reason() : record.reason);
            }
            return new CultivationSnapshot.SoldierView(
                    id,
                    shortName(h == null ? record.name : h.getDisplayName().getString()),
                    p.revision(),
                    p.baseBudget() / 1e6,
                    p.budget() / 1e6,
                    p.potential() / 1e6,
                    p.used() / 1e6,
                    p.breakthrough(),
                    p.expanded(),
                    p.primary().stream()
                            .map(v -> v.length() > 64 ? v.substring(0, 61) + "..." : v)
                            .toList(),
                    p.values(),
                    lower,
                    upper,
                    candidates,
                    cycle,
                    reason == null ? "" : reason);
        } catch (RuntimeException invalid) {
            double[] zero = new double[13];
            return new CultivationSnapshot.SoldierView(
                    id,
                    shortName(h == null ? record.name : h.getDisplayName().getString()),
                    0,
                    0,
                    0,
                    0,
                    0,
                    false,
                    false,
                    List.of(),
                    zero,
                    zero,
                    zero,
                    List.of(),
                    null,
                    "Read-only: " + invalid.getMessage());
        }
    }

    private static void reply(ServerPlayer p, Session s, String reason, UUID ack) {
        reason = reason == null ? "Training operation rejected" : reason;
        if (reason.length() > 256) reason = reason.substring(0, 256);
        try {
            var d = CultivationData.get(p.server);
            var a = d.account(p.getUUID()).at(s.facility.id());
            var ids = new LinkedHashMap<UUID, Human>();
            var f = s.facility;
            for (Human h :
                    p.serverLevel()
                            .getEntitiesOfClass(
                                    Human.class,
                                    new net.minecraft.world.phys.AABB(
                                            f.x() - 32, f.y() - 16, f.z() - 32, f.x() + 33,
                                            f.y() + 17, f.z() + 33),
                                    h -> h.hasOwner() && p.getUUID().equals(h.getOwnerUUID())))
                if (ids.size() < 32) ids.put(h.getUUID(), h);
            for (var record : d.records.values())
                if (record.owner.equals(p.getUUID())
                        && record.facility.id().equals(f.id())
                        && ids.size() < 32)
                    ids.putIfAbsent(
                            record.soldier, CultivationService.loaded(p.server, record.soldier));
            List<CultivationSnapshot.SoldierView> soldiers = new ArrayList<>();
            ids.forEach((id, h) -> soldiers.add(soldier(p, s, id, h, d.records.get(id))));
            List<CultivationSnapshot.SeatView> seats = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final int seat = i;
                var record =
                        d.records.values().stream()
                                .filter(
                                        r ->
                                                r.facility.id().equals(f.id())
                                                        && r.occupies()
                                                        && r.seat == seat)
                                .findFirst()
                                .orElse(null);
                seats.add(
                        new CultivationSnapshot.SeatView(
                                i,
                                record == null ? null : record.soldier,
                                record == null ? "AVAILABLE" : record.cycle.status().name()));
            }
            if (reason.isEmpty() && d.quarantinedCount() > 0)
                reason =
                        "Training journal contains quarantined records; reserved credit retained"
                            + " for review";
            var permission = CultivationService.permission(p.server, p.getUUID(), f, null, true);
            if (reason.isEmpty() && !permission.allowed()) reason = permission.reason();
            CultivationNetwork.snapshot(
                    p,
                    new CultivationSnapshot(
                            s.token,
                            f.dimension(),
                            f.x(),
                            f.y(),
                            f.z(),
                            f.revision(),
                            d.revision,
                            f.testMode(),
                            new CultivationSnapshot.AccountView(
                                    a.stock(), a.available(), a.reserved()),
                            seats,
                            soldiers,
                            reason,
                            ack));
        } catch (RuntimeException unavailable) {
            CultivationNetwork.snapshot(
                    p,
                    new CultivationSnapshot(
                            s.token,
                            s.facility.dimension(),
                            s.facility.x(),
                            s.facility.y(),
                            s.facility.z(),
                            s.facility.revision(),
                            0,
                            s.facility.testMode(),
                            new CultivationSnapshot.AccountView(0, 0, 0),
                            List.of(),
                            List.of(),
                            "Read-only: " + unavailable.getMessage(),
                            ack));
        }
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (var e : List.copyOf(SESSIONS.entrySet()))
            if (e.getKey().server == server) {
                try {
                    block(e.getKey(), e.getValue());
                    if (CultivationRuntime.time(server) > e.getValue().expires) {
                        reply(
                                e.getKey(),
                                e.getValue(),
                                "Training session expired; reopen block",
                                NONE);
                        SESSIONS.remove(e.getKey());
                    } else reply(e.getKey(), e.getValue(), "", NONE);
                } catch (RuntimeException closed) {
                    SESSIONS.remove(e.getKey());
                }
            }
    }

    static void close(ServerPlayer player) {
        SESSIONS.remove(player);
    }

    static void close(MinecraftServer server) {
        SESSIONS.keySet().removeIf(p -> p.server == server);
    }
}
