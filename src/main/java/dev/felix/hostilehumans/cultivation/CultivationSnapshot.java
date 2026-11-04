package dev.felix.hostilehumans.cultivation;

import java.util.List;
import java.util.UUID;

/**
 * Server-authored bounded panel view. Seeds and the selected hidden success roll never cross the
 * network.
 */
public record CultivationSnapshot(
        UUID token,
        String dimension,
        int x,
        int y,
        int z,
        long facilityRevision,
        long revision,
        boolean testMode,
        AccountView account,
        List<SeatView> seats,
        List<SoldierView> soldiers,
        String reason,
        UUID acknowledgedRequest) {
    public CultivationSnapshot {
        if (acknowledgedRequest == null)
            throw new IllegalArgumentException("Missing training acknowledgement");
        seats = List.copyOf(seats);
        soldiers = List.copyOf(soldiers);
        if (seats.size() > 3 || soldiers.size() > 128)
            throw new IllegalArgumentException("Oversized training view");
    }

    public record AccountView(long stock, long creditMicro, long reservedMicro) {}

    public record SeatView(int index, UUID soldier, String status) {}

    public record CycleView(
            UUID id,
            String status,
            long elapsedMillis,
            long durationMillis,
            boolean automatic,
            int selected,
            String reason) {}

    public record CandidateView(
            int index,
            String style,
            long signature,
            double nominalStep,
            double potentialCost,
            double materialCost,
            long durationMillis,
            double successBudget,
            double failureBudget,
            double[] successValues,
            double[] failureValues,
            double[] lower,
            double[] upper) {
        public CandidateView {
            successValues = successValues.clone();
            failureValues = failureValues.clone();
            lower = lower.clone();
            upper = upper.clone();
        }

        @Override
        public double[] successValues() {
            return successValues.clone();
        }

        @Override
        public double[] failureValues() {
            return failureValues.clone();
        }

        @Override
        public double[] lower() {
            return lower.clone();
        }

        @Override
        public double[] upper() {
            return upper.clone();
        }
    }

    public record SoldierView(
            UUID id,
            String name,
            String faction,
            int tier,
            boolean breakthroughReady,
            long revision,
            double baseBudget,
            double budget,
            double potential,
            double used,
            boolean breakthrough,
            boolean expanded,
            List<String> primary,
            double[] values,
            double[] lower,
            double[] upper,
            List<CandidateView> candidates,
            CycleView cycle,
            String reason) {
        public SoldierView {
            primary = List.copyOf(primary);
            values = values.clone();
            lower = lower.clone();
            upper = upper.clone();
            candidates = List.copyOf(candidates);
            if (primary.size() > 5 || candidates.size() > 3)
                throw new IllegalArgumentException("Oversized soldier view");
        }

        @Override
        public double[] values() {
            return values.clone();
        }

        @Override
        public double[] lower() {
            return lower.clone();
        }

        @Override
        public double[] upper() {
            return upper.clone();
        }
    }
}
