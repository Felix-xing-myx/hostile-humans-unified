package dev.felix.hostilehumans.monitor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * One committed NPC discharge, scoped to its exact control lease. Native gun parameters are
 * server-owned.
 */
public record MonitorRecoilSnapshot(
        UUID token,
        UUID target,
        int entityId,
        long generation,
        long sequence,
        String backend,
        ResourceLocation weapon,
        float amplitude,
        float adsReduction) {
    public MonitorRecoilSnapshot {
        if (token == null
                || target == null
                || entityId < 0
                || generation <= 0
                || sequence <= 0
                || weapon == null
                || !("scguns".equals(backend) || "jeg".equals(backend))
                || !Float.isFinite(amplitude)
                || amplitude < 0
                || !Float.isFinite(adsReduction))
            throw new IllegalArgumentException("Invalid native NPC recoil snapshot");
    }

    static void encode(MonitorRecoilSnapshot s, FriendlyByteBuf b) {
        b.writeUUID(s.token);
        b.writeUUID(s.target);
        b.writeVarInt(s.entityId);
        b.writeVarLong(s.generation);
        b.writeVarLong(s.sequence);
        b.writeUtf(s.backend, 8);
        b.writeResourceLocation(s.weapon);
        b.writeFloat(s.amplitude);
        b.writeFloat(s.adsReduction);
    }

    static MonitorRecoilSnapshot decode(FriendlyByteBuf b) {
        var s =
                new MonitorRecoilSnapshot(
                        b.readUUID(),
                        b.readUUID(),
                        b.readVarInt(),
                        b.readVarLong(),
                        b.readVarLong(),
                        b.readUtf(8),
                        b.readResourceLocation(),
                        b.readFloat(),
                        b.readFloat());
        if (b.isReadable()) throw new IllegalArgumentException("Trailing native NPC recoil data");
        return s;
    }
}
