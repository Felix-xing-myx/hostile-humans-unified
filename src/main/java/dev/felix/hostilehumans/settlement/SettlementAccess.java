package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.faction.FactionContent;
import dev.felix.hostilehumans.faction.StandingAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.UUID;

/**
 * One typed operation/region policy. A white flag authorizes passage, never an economic service.
 */
public final class SettlementAccess {
    public enum Operation {
        ENTER_OUTER,
        ENTER_INNER,
        VIEW,
        RENAME,
        SUPPLY,
        TRADE,
        POST,
        TRAIN
    }

    public enum Status {
        ALLOWED,
        LOCKED,
        UNKNOWN,
        MISSING
    }

    public enum Presence {
        PRESENT,
        MISSING,
        UNKNOWN
    }

    public record Result(Status status, String reason, SettlementIndex.Located located) {
        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }

    public interface PermitPolicy {
        boolean allows(
                net.minecraft.server.MinecraftServer server,
                UUID player,
                String faction,
                boolean service);

        default boolean allows(ServerPlayer player, String faction, boolean service) {
            return player != null && allows(player.getServer(), player.getUUID(), faction, service);
        }
    }

    public interface PresencePolicy {
        Presence check(ServerLevel level, BlockPos position);
    }

    private static volatile PermitPolicy permits = (server, player, faction, service) -> false;
    private static final EnumMap<SettlementFacility.Type, PresencePolicy> PRESENCE =
            new EnumMap<>(SettlementFacility.Type.class);

    private SettlementAccess() {}

    public static void permits(PermitPolicy policy) {
        permits = java.util.Objects.requireNonNull(policy);
    }

    public static void presence(SettlementFacility.Type type, PresencePolicy policy) {
        PRESENCE.put(type, java.util.Objects.requireNonNull(policy));
    }

    public static boolean author(ServerPlayer player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && (player.isCreative() || player.hasPermissions(2));
    }

    private static boolean passage(
            ServerPlayer player, String faction, boolean inner, boolean service) {
        return passage(player.getServer(), player.getUUID(), faction, inner, service);
    }

    private static boolean passage(
            net.minecraft.server.MinecraftServer server,
            UUID player,
            String faction,
            boolean inner,
            boolean service) {
        var online = server.getPlayerList().getPlayer(player);
        if (online != null && online.isCreative()) return true;
        var standing = StandingAccess.snapshot(server, player, faction);
        return standing.known()
                && standing.enabled()
                && (standing.atLeast(50)
                        || !inner
                                && standing.value() >= -99
                                && permits.allows(server, player, faction, service)
                        || !inner && !service && permits.allows(server, player, faction, false));
    }

    public static Result at(ServerPlayer player, double x, double y, double z) {
        return at(player.getServer(), player.getUUID(), player.serverLevel(), x, y, z);
    }

    /**
     * The same authoritative region policy is usable for an offline investor's already-paid
     * training period.
     */
    public static Result at(
            net.minecraft.server.MinecraftServer server,
            UUID player,
            ServerLevel level,
            double x,
            double y,
            double z) {
        var index = SettlementIndex.get(level);
        var query = index.query(x, y, z);
        if (query.unknown())
            return new Result(
                    Status.UNKNOWN, "region index is incomplete or query limit exceeded", null);
        for (var hit : query.hits()) {
            var instance = index.find(hit.instance());
            if (instance == null || instance.incomplete())
                return new Result(Status.UNKNOWN, "incomplete instance", null);
            if (!passage(
                    server,
                    player,
                    instance.faction(),
                    hit.layer() == SettlementRegion.Layer.INNER,
                    false))
                return new Result(Status.LOCKED, "protected region permission denied", null);
        }
        return new Result(Status.ALLOWED, "region access permitted", null);
    }

    public static Result queryFacility(
            ServerPlayer player, UUID instanceId, String facilityId, Operation operation) {
        var level = player.serverLevel();
        var index = SettlementIndex.get(level);
        var found = index.facility(instanceId, facilityId);
        if (found == null) return new Result(Status.MISSING, "facility not registered", null);
        var required =
                switch (operation) {
                    case VIEW, RENAME -> SettlementFacility.Type.CONTROL;
                    case SUPPLY -> SettlementFacility.Type.SUPPLY;
                    case TRADE -> SettlementFacility.Type.TRADER;
                    case TRAIN -> SettlementFacility.Type.TRAINING;
                    case POST -> SettlementFacility.Type.POST;
                    default -> null;
                };
        if (required != null && found.facility().type() != required)
            return new Result(Status.MISSING, "operation/facility type mismatch", found);
        if (index.uncertain() || found.instance().incomplete())
            return new Result(Status.UNKNOWN, "incomplete index/instance", found);
        if (operation == Operation.TRAIN
                && (found.instance().tier() < 2
                        || found.module().regions().stream()
                                .noneMatch(
                                        region ->
                                                region.id().equals(found.facility().region())
                                                        && region.layer()
                                                                == SettlementRegion.Layer.INNER)))
            return new Result(
                    Status.LOCKED, "training requires a tier-two-or-higher inner facility", found);
        if (!player.isCreative()) {
            var standing = StandingAccess.snapshot(player, found.instance().faction());
            if (!standing.known() || !standing.enabled())
                return new Result(Status.LOCKED, "standing policy unavailable or disabled", found);
            int threshold =
                    switch (operation) {
                        case VIEW -> 150;
                        case RENAME, TRAIN -> 250;
                        case ENTER_INNER -> 50;
                        default -> -500;
                    };
            if (!standing.atLeast(threshold))
                return new Result(Status.LOCKED, "standing threshold " + threshold, found);
        }
        var p = found.facility().position();
        Result area = at(player, p.x() + .5, p.y(), p.z() + .5);
        if (!area.allowed()) return new Result(area.status(), area.reason(), found);
        boolean service =
                operation == Operation.SUPPLY
                        || operation == Operation.TRADE
                        || operation == Operation.POST;
        if (service && !passage(player, found.instance().faction(), false, true))
            return new Result(
                    Status.LOCKED, "services require valid permit or true standing 50", found);
        var position = new BlockPos(p.x(), p.y(), p.z());
        if (!level.hasChunkAt(position))
            return new Result(Status.UNKNOWN, "facility chunk unloaded", found);
        Presence physical;
        var provider = PRESENCE.get(found.facility().type());
        if (provider != null) physical = provider.check(level, position);
        else
            physical =
                    switch (found.facility().type()) {
                        case CONTROL ->
                                level.getBlockState(position).getBlock()
                                                instanceof SettlementControlBlock
                                        ? Presence.PRESENT
                                        : Presence.MISSING;
                        case SUPPLY ->
                                level.getBlockState(position).is(FactionContent.TRIBUTE_CHEST.get())
                                        ? Presence.PRESENT
                                        : Presence.MISSING;
                        case POST, ENTRANCE -> Presence.PRESENT;
                        default -> Presence.UNKNOWN;
                    };
        return new Result(
                physical == Presence.PRESENT
                        ? Status.ALLOWED
                        : physical == Presence.MISSING ? Status.MISSING : Status.UNKNOWN,
                physical == Presence.PRESENT
                        ? "available"
                        : physical == Presence.MISSING
                                ? "declared facility missing"
                                : "facility adapter not installed",
                found);
    }
}
