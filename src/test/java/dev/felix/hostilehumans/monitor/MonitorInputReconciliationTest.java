package dev.felix.hostilehumans.monitor;

/** Executable deterministic intent/ack/replay scenarios; no game or world is constructed. */
public final class MonitorInputReconciliationTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var server = new MonitorSequencedInput<Integer>(5);
        var client = new MonitorInputHistory<Integer>();
        int predicted = 0, authoritative = 0;
        // Acceleration then reversal before the server tick: only the actual drained intent is acked.
        client.add(1, 1); predicted += 1; server.offer(1, 1, 10);
        client.add(2, -1); predicted -= 1; server.offer(2, -1, 10);
        check(server.acknowledged() == -1, "Receiving multiple packets must not acknowledge simulation");
        authoritative += server.drain();
        check(server.acknowledged() == 2 && authoritative == -1, "Coalescing applies latest intent exactly once");
        check(server.drain() == null && server.acknowledged() == 2, "A held tick does not invent another input acknowledgement");
        client.add(3, 1); predicted += 1;
        client.acknowledge(server.acknowledged());
        predicted = authoritative;
        for (var input : client.replay()) predicted += input.input();
        check(predicted == 0 && client.size() == 1, "Authoritative correction replays only still-unacknowledged intent");
        check(!server.offer(2, 1000, 11) && !server.offer(1, 1000, 11), "Duplicate and out-of-order packets cannot replace movement");
        check(server.fresh(15) && !server.fresh(16), "Held input expires rather than causing unbounded motion");
        client.acknowledge(1);
        check(client.size() == 1, "Late ack cannot retire a newer input");
        check(server.offer(3, 1, 17), "New intent resumes after expiry");
        check(server.drain() == 1 && server.acknowledged() == 3, "Resume acknowledgement follows actual drain");
        client.acknowledge(3);
        check(client.replay().isEmpty(), "All-acknowledged history is a valid empty replay");
        for (int i = 4; i < 13; i++) client.add(i, 1);
        check(client.replay() == null && client.size() == 0, "More than eight unacked steps triggers authoritative reset");
        client.clear();
        for (int i = 0; i < 33; i++) client.add(i, 1);
        check(client.size() == 32 && client.replay() == null, "Overflow is bounded and cannot replay a silently truncated timeline");
        client.clear(); client.add(1, 1);
        try { client.add(1, -1); throw new AssertionError("Duplicate client sequence accepted"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("MonitorInputReconciliationTest passed");
    }
}
