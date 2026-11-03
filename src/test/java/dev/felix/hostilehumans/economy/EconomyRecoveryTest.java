package dev.felix.hostilehumans.economy;

import static dev.felix.hostilehumans.economy.EconomyRecovery.Decision.*;

public final class EconomyRecoveryTest {
    private static int checks;

    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        for (String state :
                new String[] {
                    "NONE", "ABORTED", "PREPARED", "COMMITTED", "REPAIR_REQUIRED", "future"
                })
            for (int bits = 0; bits < 256; bits++) {
                boolean present = (bits & 1) != 0,
                        known = (bits & 2) != 0,
                        thisStamp = (bits & 4) != 0,
                        previous = (bits & 8) != 0,
                        previousPresent = (bits & 16) != 0,
                        before = (bits & 32) != 0,
                        after = (bits & 64) != 0,
                        politics = (bits & 128) != 0;
                var result =
                        EconomyRecovery.decide(
                                state,
                                present,
                                known,
                                thisStamp,
                                previous,
                                previousPresent,
                                before,
                                after,
                                politics,
                                politics);
                if (present && !known)
                    require(result == REPAIR, "unknown stamp never accepted even latest ABORTED");
                if (state.equals("ABORTED") && result == ACCEPT)
                    require(
                            before && (!present && !previousPresent || present && previous),
                            "aborted cannot hide unrecognized/old/mixed saved refund");
            }
        require(
                EconomyRecovery.decide(
                                "ABORTED", true, false, false, false, false, true, true, true, true)
                        == REPAIR,
                "world old aborted B and player unknown refund A fail closed");
        require(
                EconomyRecovery.decide(
                                "ABORTED", true, true, false, true, true, false, true, true, true)
                        == REPAIR,
                "known prior stamp does not prove an aborted grant rolled back");
        require(
                EconomyRecovery.decide(
                                "ABORTED", true, true, false, true, true, true, false, true, true)
                        == ACCEPT,
                "verified aborted rollback with recognized previous stamp");
        require(
                EconomyRecovery.decide(
                                "PREPARED", true, true, true, false, true, true, true, true, true)
                        == COMPLETE_PREPARED,
                "zero inventory change with this stamp completes instead of abort");
        require(
                EconomyRecovery.decide(
                                "PREPARED",
                                false,
                                true,
                                false,
                                false,
                                false,
                                true,
                                false,
                                true,
                                false)
                        == REPAIR,
                "world politics after and player inventory before requires repair");
        require(
                EconomyRecovery.decide(
                                "COMMITTED",
                                true,
                                true,
                                true,
                                false,
                                true,
                                false,
                                false,
                                false,
                                false)
                        == ACCEPT,
                "player inventory legitimately used after committed stamped transaction");
        require(
                EconomyRecovery.decide(
                                "COMMITTED", true, true, false, true, true, true, false, true, true)
                        == REPLAY_COMMITTED,
                "fully verified old inventory can replay once");
        require(
                EconomyRecovery.decide(
                                "COMMITTED", true, true, false, false, true, true, false, true, true)
                        == REPAIR,
                "matching inventory with an older recognized stamp cannot skip the predecessor");
        require(
                EconomyRecovery.decide(
                                "PREPARED", false, true, false, false, true, false, true, true, true)
                        == REPAIR,
                "matching after inventory cannot complete when the expected predecessor is absent");
        require(
                EconomyRecovery.decide(
                                "PREPARED", false, true, false, false, false, false, true, true, true)
                        == COMPLETE_PREPARED,
                "the first transaction can recover without a predecessor");
        System.out.println(
                "EconomyRecoveryTest: "
                        + checks
                        + " mixed-save/unknown-stamp/rollback checks passed");
    }
}
