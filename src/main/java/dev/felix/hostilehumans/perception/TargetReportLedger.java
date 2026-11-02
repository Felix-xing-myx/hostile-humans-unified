package dev.felix.hostilehumans.perception;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Per-recipient target suppression. Active entries are never evicted to admit another report. */
final class TargetReportLedger {
    static final int CAPACITY = 32;
    private final LinkedHashMap<UUID, Long> deadlines = new LinkedHashMap<>();
    private long lastPruned=Long.MIN_VALUE;
    boolean contains(UUID target,long now) {
        if(lastPruned!=now){deadlines.values().removeIf(until->now>=until);lastPruned=now;}
        return deadlines.containsKey(target);
    }
    boolean admit(UUID target,long now,long until) {
        if(target==null||until<=now||contains(target,now)||deadlines.size()>=CAPACITY)return false;
        deadlines.put(target,until);
        return true;
    }
}
