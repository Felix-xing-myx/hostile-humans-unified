package club.someoneice.humangunner;

import java.util.ArrayDeque;

/** Bounded rolling-window budget, shared by all soldiers speaking to one owner. */
final class SoldierChatBudget {
    private final ArrayDeque<Long> sent = new ArrayDeque<>();
    private long next;
    boolean allow(long now, int gap, int window, int maximum) {
        if (!sent.isEmpty() && now < sent.peekLast()) { sent.clear(); next = now; }
        while (!sent.isEmpty() && now - sent.peekFirst() >= window) sent.removeFirst();
        if (maximum <= 0 || now < next || sent.size() >= maximum) return false;
        sent.addLast(now);
        next = now + gap;
        return true;
    }
}
