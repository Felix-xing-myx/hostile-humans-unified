package dev.felix.hostilehumans.behavior.combat.retreat;

/** Actual displacement is independent of route existence, repeated route replacement, jumping and weapon animation. */
public final class RetreatEscapeProgressTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        RetreatEscapeProgress noRoute = new RetreatEscapeProgress();
        check(!noRoute.update(100, 0, 0, 0, true, false), "path failure alone does not enable melee");
        check(!noRoute.update(111, 0, 0, 0, true, false), "wait through transient planning pause");
        check(noRoute.update(112, 0, 0, 0, true, false), "no route and no progress enter last stand");
        check(noRoute.hasProgress(.5, 0, 0, false), "impact gate can see escape before next goal update");
        check(!noRoute.update(113, .5, 0, 0, true, false), "real escape progress immediately revokes melee even without a route");
        RetreatEscapeProgress stuckRoute = new RetreatEscapeProgress();
        stuckRoute.update(0, 0, 0, 0, false, false);
        check(!stuckRoute.update(19, 0, 0, 0, false, false), "nominal route has a grace period");
        check(stuckRoute.update(20, 0, 0, 0, false, false), "valid path cannot conceal actual immobility");
        check(stuckRoute.update(21, 0, 0, 0, false, false), "route replanning alone does not revoke last stand");
        check(!stuckRoute.update(22, .6, 0, 0, false, false), "escape restores travel immediately");
        RetreatEscapeProgress walking = new RetreatEscapeProgress();
        for (int tick = 0; tick < 500; tick++)
            check(!walking.update(tick, tick * .08, 0, 0, tick % 7 == 0, false), "ordinary walking and manual escape steps do not enter melee");
        RetreatEscapeProgress jitter = new RetreatEscapeProgress();
        jitter.update(0, 0, 0, 0, false, false);
        for (int tick = 1; tick <= 30; tick++) {
            boolean active = jitter.update(tick, (tick % 2) * .08, (tick % 5) * .3, 0, false, false);
            check(active == (tick >= 20), "corner jitter and jumping in place cannot look like successful escape");
        }
        check(!jitter.update(31, .08, 1, 0, false, true), "actual climbing/swimming height progress is escape");
        RetreatEscapeProgress slow = new RetreatEscapeProgress();
        for (int tick = 0; tick < 200; tick++)
            check(!slow.update(tick, tick * .04, 0, 0, false, false), "small per-tick movement accumulates into real progress");
        check(!slow.update(1, 0, 0, 0, false, false), "clock/session rollback resets progress history");
        check(!slow.update(2, Double.NaN, 0, 0, true, false), "invalid positions never grant last-stand authority");
        System.out.println("RetreatEscapeProgressTest passed: blocked retreat, displacement and release boundaries");
    }
}
