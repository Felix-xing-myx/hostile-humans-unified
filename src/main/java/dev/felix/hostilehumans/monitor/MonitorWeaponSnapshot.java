package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.behavior.combat.gun.GunHudData;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Only authoritative numbers; no player inventory or client-side reserve estimates. */
public record MonitorWeaponSnapshot(UUID token, UUID target, int entityId, long generation,
        GunHudData weapon, int magazines, boolean infinite, boolean reloading) {
    public MonitorWeaponSnapshot {
        if (token == null || target == null || entityId < 0 || generation < 0 || weapon == null || magazines < 0)
            throw new IllegalArgumentException("Invalid monitor weapon snapshot");
    }
    static void encode(MonitorWeaponSnapshot s, FriendlyByteBuf out) {
        out.writeUUID(s.token); out.writeUUID(s.target); out.writeVarInt(s.entityId); out.writeVarLong(s.generation);
        out.writeUtf(s.weapon.backend(), 16); out.writeResourceLocation(s.weapon.gun()); out.writeVarInt(s.weapon.loaded());
        out.writeVarInt(s.weapon.capacity()); out.writeUtf(s.weapon.mode(), 32); out.writeBoolean(s.weapon.energy());
        out.writeVarInt(s.magazines); out.writeBoolean(s.infinite); out.writeBoolean(s.reloading);
    }
    static MonitorWeaponSnapshot decode(FriendlyByteBuf in) {
        var value = new MonitorWeaponSnapshot(in.readUUID(), in.readUUID(), in.readVarInt(), in.readVarLong(),
                new GunHudData(in.readUtf(16), in.readResourceLocation(), in.readVarInt(), in.readVarInt(), in.readUtf(32), in.readBoolean()),
                in.readVarInt(), in.readBoolean(), in.readBoolean());
        if (in.isReadable()) throw new IllegalArgumentException("Trailing monitor weapon data");
        return value;
    }
}
