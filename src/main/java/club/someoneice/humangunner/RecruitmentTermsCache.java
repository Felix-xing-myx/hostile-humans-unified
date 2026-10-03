package club.someoneice.humangunner;

/** A stale packet from another connection must never become the current server's prices. */
final class RecruitmentTermsCache {
    private Object connection;
    private RecruitmentConfigNetwork.Snapshot terms;

    void accept(Object source, Object current, RecruitmentConfigNetwork.Snapshot snapshot) {
        if (current == null || source != current) return;
        connection = source;
        terms = snapshot;
    }

    RecruitmentConfigNetwork.Price price(Object current, int tier) {
        return current != null && current == connection && terms != null ? terms.tier(tier) : null;
    }

    void clear() { connection = null; terms = null; }
}
