package dev.felix.hostilehumans.cultivation;

import java.util.UUID;

/** One paid, selected period. No automatic period is invented by elapsed-time catchup. */
public record CultivationCycle(
        UUID id,
        CultivationProfile before,
        CultivationPlanner.Mode mode,
        CultivationPlanner.Candidate candidate,
        boolean success,
        Status status,
        long elapsed,
        long checkpoint,
        boolean automatic,
        String reason) {
    public enum Status {
        MOVING,
        ACTIVE,
        PAUSED,
        READY,
        COMPLETED,
        CANCELLED
    }

    public CultivationCycle {
        if (id == null
                || before == null
                || mode == null
                || candidate == null
                || status == null
                || elapsed < 0
                || elapsed > candidate.durationMillis()
                || checkpoint < 0) throw new IllegalArgumentException("Invalid training cycle");
        long d = candidate.nominalStep(), space = before.breakthrough() ? (d * 3 + 1) / 2 : d;
        if (d < 0
                || d > CultivationRules.STEP
                || candidate.index() < 0
                || candidate.index() > 2
                || candidate.successBudget() != before.budget() + d
                || candidate.failureBudget() != before.budget() + d * 4 / 10
                || candidate.potentialCost()
                        != (mode == CultivationPlanner.Mode.REDISTRIBUTE ? 0 : space)
                || candidate.potentialCost() > before.remaining()
                || candidate.materialMicro()
                        != (mode == CultivationPlanner.Mode.REDISTRIBUTE
                                ? CultivationRules.UNIT
                                : d * CultivationRules.UNIT / CultivationRules.STEP)
                || candidate.durationMillis()
                        != (mode == CultivationPlanner.Mode.REDISTRIBUTE
                                ? CultivationRules.PERIOD_MILLIS
                                : Math.max(
                                        1,
                                        CultivationRules.PERIOD_MILLIS * d / CultivationRules.STEP))
                || (mode == CultivationPlanner.Mode.GROW ? d <= 0 : d != 0)
                || success
                        != (mode == CultivationPlanner.Mode.REDISTRIBUTE
                                || CultivationPlanner.succeeds(before)))
            throw new IllegalArgumentException("Inconsistent saved period contract");
    }

    public static CultivationCycle create(
            UUID soldier,
            CultivationProfile profile,
            CultivationPlanner.Mode mode,
            CultivationPlanner.Candidate candidate,
            long clock,
            boolean automatic) {
        UUID id =
                new UUID(
                        CultivationRules.mix(soldier.getMostSignificantBits() ^ profile.seed()),
                        CultivationRules.mix(
                                profile.sequence() ^ soldier.getLeastSignificantBits()));
        return new CultivationCycle(
                id,
                profile,
                mode,
                candidate,
                mode == CultivationPlanner.Mode.REDISTRIBUTE
                        || CultivationPlanner.succeeds(profile),
                Status.MOVING,
                0,
                clock,
                automatic,
                "");
    }

    public CultivationCycle accrue(long clock) {
        if (status != Status.ACTIVE) return this;
        long next = Math.min(candidate.durationMillis(), elapsed + Math.max(0, clock - checkpoint));
        return new CultivationCycle(
                id,
                before,
                mode,
                candidate,
                success,
                next >= candidate.durationMillis() ? Status.READY : Status.ACTIVE,
                next,
                Math.max(checkpoint, clock),
                automatic,
                reason);
    }

    public CultivationCycle arrived(long clock) {
        if (status != Status.MOVING) throw new IllegalStateException();
        return state(Status.ACTIVE, clock, "");
    }

    public CultivationCycle pause(long clock, String why) {
        var p = accrue(clock);
        if (p.status == Status.COMPLETED || p.status == Status.CANCELLED) return p;
        return p.state(Status.PAUSED, clock, why);
    }

    public CultivationCycle resume(long clock) {
        if (status != Status.PAUSED) throw new IllegalStateException();
        return state(Status.MOVING, clock, "");
    }

    public CultivationCycle cancel(long clock, String why) {
        if (status == Status.COMPLETED || status == Status.CANCELLED) return this;
        return state(Status.CANCELLED, clock, why);
    }

    public CultivationCycle complete(long clock) {
        if (status != Status.READY) throw new IllegalStateException();
        return state(Status.COMPLETED, clock, "");
    }

    public CultivationProfile result() {
        if (status != Status.READY && status != Status.COMPLETED)
            throw new IllegalStateException("Incomplete period");
        return before.result(
                success ? candidate.successBudget() : candidate.failureBudget(),
                candidate.potentialCost(),
                success ? candidate.successValues() : candidate.failureValues());
    }

    private CultivationCycle state(Status next, long clock, String why) {
        return new CultivationCycle(
                id,
                before,
                mode,
                candidate,
                success,
                next,
                elapsed,
                Math.max(clock, checkpoint),
                automatic,
                why);
    }
}
