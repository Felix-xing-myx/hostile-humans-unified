package club.someoneice.humangunner;

import java.util.UUID;

/** Mixin-provided removal hook for Hostile Humans' otherwise append-only data. */
public interface HumanServerDataCleanup {
    boolean humanGunner$removeHuman(UUID humanUuid);
}
