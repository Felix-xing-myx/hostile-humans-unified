package dev.felix.hostilehumans.entity;

import java.util.UUID;

/** Mixin-provided removal hook for Hostile Humans' otherwise append-only data. */
public interface HumanServerDataCleanup {
    boolean hhu$removeHuman(UUID humanUuid);
}
