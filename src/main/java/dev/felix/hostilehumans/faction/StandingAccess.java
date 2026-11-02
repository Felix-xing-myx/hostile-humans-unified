package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.config.UnifiedConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * Read-only political authority for facilities/economy. Does not inherit combat's disabled-feature
 * neutral fallback.
 */
public final class StandingAccess {
    public record Snapshot(boolean known, boolean enabled, int value) {
        public boolean atLeast(int threshold) {
            return known && enabled && value >= threshold;
        }
    }

    private StandingAccess() {}

    public static java.util.UUID responsible(net.minecraft.world.entity.Entity source) {
        if (source instanceof net.minecraft.world.entity.player.Player player)
            return player.getUUID();
        if (source instanceof dev.felix.hostilehumans.entity.Human human)
            return HumanRelations.effectiveOwner(human);
        if (source instanceof net.minecraft.world.entity.TamableAnimal animal)
            return animal.getOwnerUUID();
        return null;
    }

    public static boolean enemies(String first, String second) {
        return FactionDefinitions.enemies(first, second);
    }

    public static Snapshot snapshot(ServerPlayer player, String faction) {
        return snapshot(
                player == null ? null : player.getServer(),
                player == null ? null : player.getUUID(),
                faction);
    }

    public static Snapshot snapshot(
            net.minecraft.server.MinecraftServer server, java.util.UUID player, String faction) {
        boolean enabled = UnifiedConfig.get().featureEnabled("standing", true);
        if (server == null
                || player == null
                || faction == null
                || FactionDefinitions.find(faction) == null) return new Snapshot(false, enabled, 0);
        if (!server.isSameThread())
            throw new IllegalStateException("Standing access requires server thread");
        String id = FactionDefinitions.find(faction).id().toString();
        var data = FactionStandingData.get(server);
        return new Snapshot(data.known(player), enabled, data.standing(player, id));
    }
}
