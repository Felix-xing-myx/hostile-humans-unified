package dev.felix.hostilehumans.core;

import java.util.UUID;

public final class StandingIncidentsTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Incident check " + checks); }
    private static StandingIncidents.Outcome hit(StandingIncidents m, UUID p, long t, double damage, int standing, int band, int rank) {
        return m.damage(p, t, "test:weapon", null, damage, 100, standing, band, rank);
    }
    public static void main(String[] args) {
        UUID player = UUID.randomUUID(), other = UUID.randomUUID();
        StandingIncidents m = new StandingIncidents();
        check(hit(m, player, 0, 2, 100, 3, 3).standingPenalty() == 6);
        check(hit(m, player, 1, 2, 100, 3, 3).standingPenalty() == 0);
        check(m.death(player, 1, "test:weapon", null) == 12);
        check(m.death(player, 1, "test:weapon", null) == 0);
        m = new StandingIncidents();
        for (int i = 0; i < 4; i++) check(hit(m, player, i * 4, 1, 100, 3, 3).reason() == StandingIncidents.Reason.NONE);
        check(hit(m, player, 16, 1, 100, 3, 3).reason() == StandingIncidents.Reason.REPEATED_HITS);
        check(m.reason(player, 615) == StandingIncidents.Reason.REPEATED_HITS);
        check(m.reason(player, 616) == StandingIncidents.Reason.NONE);
        check(m.reason(other, 16) == StandingIncidents.Reason.NONE);
        m = new StandingIncidents();
        check(hit(m, player, 0, 29, 100, 3, 3).reason() == StandingIncidents.Reason.NONE);
        check(hit(m, player, 1, 1, 100, 3, 3).reason() == StandingIncidents.Reason.DAMAGE_THRESHOLD);
        check(hit(m, other, 2, 1, 100, 3, 3).reason() == StandingIncidents.Reason.NONE);
        m = new StandingIncidents();
        check(!hit(m, player, 0, 1, 50, 1, 2).notifyGroup());
        check(!hit(m, player, 1, 1, 50, 1, 2).notifyGroup());
        check(hit(m, player, 2, 1, 50, 1, 2).notifyGroup());
        check(!hit(m, player, 3, 1, 50, 1, 2).notifyGroup());
        m = new StandingIncidents();
        check(hit(m, player, 0, 1, 49, 1, 2).notifyGroup());
        check(hit(m, other, 1, 1, 100, 0, 1).reason() == StandingIncidents.Reason.SELF_DEFENSE);
        m = new StandingIncidents();
        check(hit(m, player, 0, 1, 100, 3, 3).standingPenalty() == 6);
        check(hit(m, player, 300, 1, 100, 3, 3).reason() == StandingIncidents.Reason.NONE);
        check(hit(m, player, 800, 29, 100, 3, 3).reason() == StandingIncidents.Reason.DAMAGE_THRESHOLD);
        m = new StandingIncidents();
        check(m.damage(player, 0, "gun", "shot-a", 1, 100, 100, 3, 3).standingPenalty() == 6);
        check(m.damage(player, 5, "gun", "shot-b", 1, 100, 100, 3, 3).standingPenalty() == 6);
        check(m.damage(player, 10, "gun", "shot-a", 1, 100, 100, 3, 3).standingPenalty() == 0);
        check(m.death(player, 10, "gun", "shot-a") == 12);
        for (int i = 0; i < 32; i++) { m.groupAlert(UUID.randomUUID(), 20 + i); check(m.size() <= 16); }
        m.groupAlert(player, 100); check(m.reason(player, 699) == StandingIncidents.Reason.GROUP_ALERT);
        check(m.reason(player, 700) == StandingIncidents.Reason.NONE);
        check(m.damage(player, 701, "gun", null, Double.NaN, 100, 100, 3, 3).standingPenalty() == 0);
        System.out.println("Standing incident checks passed: " + checks);
    }
}
