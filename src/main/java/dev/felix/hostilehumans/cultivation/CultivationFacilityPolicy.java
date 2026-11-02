package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Root injects the settlement N6 policy; no local guessing of region/standing or unloaded block
 * presence.
 */
@FunctionalInterface
public interface CultivationFacilityPolicy {
    record Access(boolean allowed, String reason) {
        public static Access allow() {
            return new Access(true, "");
        }

        public static Access deny(String reason) {
            return new Access(false, reason);
        }
    }

    Access authorize(
            MinecraftServer server,
            UUID investor,
            CultivationFacility facility,
            Human soldier,
            boolean newPeriod);
}
