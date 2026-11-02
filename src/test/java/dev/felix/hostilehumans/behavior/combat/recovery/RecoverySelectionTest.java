package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.behavior.combat.recovery.PotionRecoveryPolicy;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySelectionPolicy;
import dev.felix.hostilehumans.behavior.combat.retreat.RecoveryIntentPolicy;

/** Pure recovery policy boundaries. Not executed without Java-test authorization. */
public final class RecoverySelectionTest {
    private static int checks;
    public static void main(String[] args) {
        check(RecoverySelectionPolicy.retreatRank(0,true,true) > RecoverySelectionPolicy.retreatRank(7,false,true), "escape prioritizes every valid non-food medicine before even enchanted apples");
        check(RecoverySelectionPolicy.retreatRank(0,true,false) == 0, "ordinary emergency apple order is unchanged");
        check(RecoverySelectionPolicy.retreatRank(6,true,true) > RecoverySelectionPolicy.retreatRank(0,true,true), "escape retains relative food quality order");
        var intent = new RecoveryIntentPolicy();
        intent.record(0, 25, 100);
        check(!intent.wantsRegeneration(0, .8), "exact 25 percent does not trigger loss branch");
        intent.record(99, .01, 100);
        check(intent.wantsRegeneration(99, .8), "real losses above 25 percent within five seconds trigger");
        check(intent.wantsRegeneration(698, .8), "intent remains through tick 599");
        check(!intent.wantsRegeneration(699, .8), "30 second intent expires exactly at 600 ticks");
        intent = new RecoveryIntentPolicy();
        intent.record(0, 25, 100);
        intent.record(100, 1, 100);
        check(!intent.wantsRegeneration(100, .8), "damage exactly five seconds old is excluded");
        check(!intent.wantsRegeneration(100, .30), "exact 30 percent does not trigger low-health branch");
        check(intent.wantsRegeneration(100, .299), "below 30 percent creates intent");
        intent.consumed();
        check(!intent.wantsRegeneration(101, .8), "successful regeneration clears pending intent");
        intent = new RecoveryIntentPolicy();
        intent.record(102, Double.NaN, 100); intent.record(102, -1, 100);
        check(!intent.recentDamage(102), "invalid and nonpositive damage is ignored");
        intent.record(103, 1, 100);
        check(intent.recentDamage(202) && !intent.recentDamage(203), "morphine recent real damage window is five seconds");
        var food = new RecoverySelectionPolicy.Candidate(0, 6, 20, 10, 1);
        var regen = new RecoverySelectionPolicy.Candidate(1, 4, 2, 32, 40);
        check(RecoverySelectionPolicy.better(regen, food), "category order precedes inventory value or healing amount");
        var current = new RecoverySelectionPolicy.Candidate(3, 3, 6, 30, 10);
        check(RecoverySelectionPolicy.better(new RecoverySelectionPolicy.Candidate(4,3,7.01,60,50),current), "more than one effective HP wins");
        check(RecoverySelectionPolicy.better(new RecoverySelectionPolicy.Candidate(4,3,5,20,50),current), "within one HP use shorter duration");
        check(RecoverySelectionPolicy.better(new RecoverySelectionPolicy.Candidate(4,3,6,30,9),current), "equal benefit/duration consumes lower unit quality");
        check(RecoverySelectionPolicy.better(new RecoverySelectionPolicy.Candidate(2,3,6,30,10),current), "final tie uses stable real location order");
        check(RecoverySelectionPolicy.better(new RecoverySelectionPolicy.Candidate(2,3,Double.NaN,20,50),current), "unknown backend healing falls back to reliable duration");
        check(!PotionRecoveryPolicy.urgentInstantHealing(.5) && PotionRecoveryPolicy.urgentInstantHealing(.499), "strict fifty percent instant boundary");
        System.out.println("RecoverySelectionTest: " + checks + " checks passed");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
