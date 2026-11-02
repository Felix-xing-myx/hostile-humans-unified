package dev.felix.hostilehumans.bootstrap;

import dev.felix.hostilehumans.cultivation.CultivationContent;
import dev.felix.hostilehumans.cultivation.CultivationFacilityPolicy;
import dev.felix.hostilehumans.cultivation.CultivationService;
import dev.felix.hostilehumans.faction.StandingAccess;
import dev.felix.hostilehumans.settlement.SettlementAccess;
import dev.felix.hostilehumans.settlement.SettlementFacility;
import dev.felix.hostilehumans.settlement.SettlementIndex;
import dev.felix.hostilehumans.settlement.SettlementRegion;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;

import java.util.*;

/**
 * Composition root: cultivation owns its lifecycle; settlement and standing retain permission
 * authority.
 */
public final class CultivationSettlementIntegration {
    private CultivationSettlementIntegration() {}

    public static void install() {
        SettlementAccess.presence(
                SettlementFacility.Type.TRAINING,
                (level, pos) ->
                        level.getBlockState(pos).is(CultivationContent.TRAINING_BLOCK.get())
                                ? SettlementAccess.Presence.PRESENT
                                : SettlementAccess.Presence.MISSING);
        CultivationService.setFacilityPolicy(
                (server, investor, facility, soldier, newPeriod) -> {
                    if (facility.testMode()) {
                        var player = server.getPlayerList().getPlayer(investor);
                        if (newPeriod && (player == null || !player.isCreative())
                                || player != null && !player.isCreative())
                            return CultivationFacilityPolicy.Access.deny(
                                    "Creative test permission unavailable");
                        return CultivationFacilityPolicy.Access.allow();
                    }
                    var level =
                            server.getLevel(
                                    ResourceKey.create(
                                            Registries.DIMENSION,
                                            new ResourceLocation(facility.dimension())));
                    if (level == null)
                        return CultivationFacilityPolicy.Access.deny(
                                "Training dimension unavailable");
                    var index = SettlementIndex.get(level);
                    var query =
                            index.query(facility.x() + .5, facility.y() + .5, facility.z() + .5);
                    if (query.unknown())
                        return CultivationFacilityPolicy.Access.deny("Region index is unknown");
                    var regionPermission =
                            SettlementAccess.at(
                                    server,
                                    investor,
                                    level,
                                    facility.x() + .5,
                                    facility.y() + .5,
                                    facility.z() + .5);
                    if (!regionPermission.allowed())
                        return CultivationFacilityPolicy.Access.deny(regionPermission.reason());
                    Set<UUID> matches = new HashSet<>();
                    for (var hit : query.hits()) {
                        var site = index.find(hit.instance());
                        if (site == null || site.incomplete())
                            return CultivationFacilityPolicy.Access.deny("Incomplete settlement");
                        for (var module : site.modules())
                            for (var anchor : module.facilities()) {
                                var at = anchor.position();
                                if (anchor.type() == SettlementFacility.Type.TRAINING
                                        && at.x() == facility.x()
                                        && at.y() == facility.y()
                                        && at.z() == facility.z()) {
                                    if (site.tier() < 2
                                            || module.regions().stream()
                                                    .noneMatch(
                                                            region ->
                                                                    region.id()
                                                                                    .equals(
                                                                                            anchor
                                                                                                    .region())
                                                                            && region.layer()
                                                                                    == SettlementRegion
                                                                                            .Layer
                                                                                            .INNER))
                                        return CultivationFacilityPolicy.Access.deny(
                                                "Training requires a tier-two-or-higher inner"
                                                        + " anchor");
                                    if (!StandingAccess.snapshot(server, investor, site.faction())
                                            .atLeast(250))
                                        return CultivationFacilityPolicy.Access.deny(
                                                "Training requires 250 real faction standing");
                                    matches.add(site.id());
                                }
                            }
                    }
                    return matches.size() == 1
                            ? CultivationFacilityPolicy.Access.allow()
                            : CultivationFacilityPolicy.Access.deny(
                                    "Training anchor missing or belongs to multiple settlements");
                });
    }
}
