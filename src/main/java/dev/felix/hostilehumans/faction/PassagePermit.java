package dev.felix.hostilehumans.faction;

/**
 * Immutable faction-wide pass/deposit state. Currency movement is committed by the caller, never by
 * a query.
 */
public record PassagePermit(
        long expiresAt,
        long banUntil,
        long depositCopper,
        boolean initiated,
        boolean refundPending) {
    public static final long DEFAULT_DURATION = 24_000, DEFAULT_BAN = 72_000;

    public PassagePermit {
        if (expiresAt < 0 || banUntil < 0 || depositCopper < 0 || refundPending && expiresAt != 0)
            throw new IllegalArgumentException("invalid passage permit");
    }

    public static PassagePermit empty() {
        return new PassagePermit(0, 0, 0, false, false);
    }

    public boolean active(long now, int standing) {
        return expiresAt > now && standing >= -99 && standing < 50 && !banned(now);
    }

    public boolean banned(long now) {
        return banUntil > now;
    }

    public PassagePermit initiate() {
        return new PassagePermit(expiresAt, banUntil, depositCopper, true, refundPending);
    }

    public PassagePermit reconcile(long now, int standing) {
        if (expiresAt == 0) return this;
        // An expired deposit has already been forfeited. Promotion afterwards must not resurrect
        // it.
        if (now >= expiresAt || standing < -99)
            return new PassagePermit(0, banUntil, 0, initiated, false);
        if (standing >= 50)
            return new PassagePermit(0, banUntil, depositCopper, initiated, depositCopper > 0);
        return this;
    }

    public PassagePermit issue(long now, long duration, long deposit, int standing) {
        if (duration <= 0
                || duration > 1_728_000
                || deposit < 0
                || standing < -99
                || standing >= 50
                || !initiated
                || banned(now)
                || refundPending) throw new IllegalArgumentException("passage unavailable");
        if (active(now, standing))
            return this; // the facade returns this receipt without charging again
        return new PassagePermit(Math.addExact(now, duration), banUntil, deposit, true, false);
    }

    public PassagePermit returnEarly(long now, int standing) {
        var current = reconcile(now, standing);
        if (current.refundPending || !current.active(now, standing)) return current;
        return new PassagePermit(0, banUntil, depositCopper, initiated, depositCopper > 0);
    }

    public PassagePermit refunded() {
        return refundPending ? new PassagePermit(0, banUntil, 0, initiated, false) : this;
    }

    public PassagePermit violate(long now, long banDuration) {
        if (banDuration <= 0) throw new IllegalArgumentException("ban duration");
        return new PassagePermit(
                0, Math.max(banUntil, Math.addExact(now, banDuration)), 0, initiated, false);
    }
}
