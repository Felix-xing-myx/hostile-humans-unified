package dev.felix.hostilehumans.faction;

public final class PassagePermitTest {
    public static void main(String[] args) {
        var base = PassagePermit.empty().initiate();
        var issued = base.issue(100, 24_000, 37, -99);
        require(
                issued.active(100, -99) && issued.active(24_099, 49),
                "time is measured from issue, independent of visiting a site");
        require(
                issued.issue(101, 24_000, 37, -99) == issued,
                "duplicate request does not create a new charge/deadline");
        require(!issued.reconcile(24_100, 0).active(24_100, 0), "fixed expiry");
        require(
                issued.reconcile(24_100, 50).depositCopper() == 0,
                "late promotion cannot refund an expired deposit");
        var early = issued.returnEarly(200, 0);
        require(
                early.refundPending() && early.depositCopper() == 37 && !early.active(200, 0),
                "early return creates one payable refund");
        var paid = early.refunded();
        require(
                !paid.refundPending() && paid.depositCopper() == 0 && paid.refunded() == paid,
                "refund is idempotent");
        require(issued.reconcile(200, 50).refundPending(), "promotion triggers refund obligation");
        require(
                issued.reconcile(200, -100).depositCopper() == 0,
                "hostile status revokes without refund");
        var banned = issued.violate(300, 72_000);
        require(
                banned.banned(72_299) && !banned.banned(72_300) && banned.depositCopper() == 0,
                "ban and forfeiture");
        rejects(() -> PassagePermit.empty().issue(100, 24_000, 1, 0));
        rejects(() -> base.issue(100, 24_000, 1, -100));
        rejects(() -> base.issue(100, 24_000, 1, 50));
        rejects(() -> early.issue(200, 24_000, 1, 0));
        System.out.println(
                "PassagePermitTest: fixed timing, deposit lifecycle and idempotence passed");
    }

    private static void require(boolean v, String text) {
        if (!v) throw new AssertionError(text);
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            throw new AssertionError("invalid permit accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}
