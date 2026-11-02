package dev.felix.hostilehumans.economy;

/**
 * Conservative cross-file reconciliation decision. Ambiguity requires repair; it is never
 * advertised as disk atomicity.
 */
public final class EconomyRecovery {
    public enum Decision {
        ACCEPT,
        ABORT_PREPARED,
        COMPLETE_PREPARED,
        REPLAY_COMMITTED,
        REPAIR
    }

    private EconomyRecovery() {}

    public static Decision decide(
            String state,
            boolean stampPresent,
            boolean stampRecognized,
            boolean stampThis,
            boolean stampPrevious,
            boolean previousPresent,
            boolean before,
            boolean after,
            boolean politicsCompatible,
            boolean politicsBefore) {
        if (stampPresent && !stampRecognized) return Decision.REPAIR;
        if (state.equals("NONE")) return stampPresent ? Decision.REPAIR : Decision.ACCEPT;
        if (state.equals("ABORTED"))
            return before && (!stampPresent && !previousPresent || stampPresent && stampPrevious)
                    ? Decision.ACCEPT
                    : Decision.REPAIR;
        if (state.equals("COMMITTED")) {
            if (stampThis) return Decision.ACCEPT;
            return before ? Decision.REPLAY_COMMITTED : Decision.REPAIR;
        }
        if (state.equals("PREPARED")) {
            if (stampThis)
                return after && politicsCompatible ? Decision.COMPLETE_PREPARED : Decision.REPAIR;
            if (before) return politicsBefore ? Decision.ABORT_PREPARED : Decision.REPAIR;
            return after && politicsCompatible ? Decision.COMPLETE_PREPARED : Decision.REPAIR;
        }
        return Decision.REPAIR;
    }
}
