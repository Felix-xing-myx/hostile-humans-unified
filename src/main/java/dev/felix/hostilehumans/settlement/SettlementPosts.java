package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Explicit player command at an authored post; occupying a region never assigns a soldier
 * automatically.
 */
public final class SettlementPosts {
    private SettlementPosts() {}

    public static String assign(
            ServerPlayer player, UUID instance, String facility, UUID humanId, int expected) {
        return assignValidated(player, instance, facility, humanId, expected, true);
    }

    /**
     * Called only with a live management session; the control block independently revalidates that
     * authority.
     */
    static String assignFromControl(
            ServerPlayer player,
            BlockPos control,
            UUID instance,
            String facility,
            UUID humanId,
            int expected) {
        if (player.distanceToSqr(control.getX() + .5, control.getY() + .5, control.getZ() + .5)
                > 64) return "management control out of reach";
        var site = SettlementIndex.get(player.serverLevel()).find(instance);
        boolean authorized = false;
        if (site != null)
            for (var module : site.modules())
                for (var anchor : module.facilities()) {
                    var at = anchor.position();
                    if (anchor.type() == SettlementFacility.Type.CONTROL
                            && at.x() == control.getX()
                            && at.y() == control.getY()
                            && at.z() == control.getZ()
                            && SettlementAccess.queryFacility(
                                            player,
                                            instance,
                                            module.key() + "/" + anchor.id(),
                                            SettlementAccess.Operation.VIEW)
                                    .allowed()) authorized = true;
                }
        if (!authorized) return "management control permission unavailable";
        return assignValidated(player, instance, facility, humanId, expected, false);
    }

    private static String assignValidated(
            ServerPlayer player,
            UUID instance,
            String facility,
            UUID humanId,
            int expected,
            boolean local) {
        var result =
                SettlementAccess.queryFacility(
                        player, instance, facility, SettlementAccess.Operation.POST);
        if (!result.allowed()) return result.reason();
        var located = result.located();
        if (located.facility().type() != SettlementFacility.Type.POST
                || located.instance().revision() != expected) return "post revision/type changed";
        var cell = located.facility().position();
        var position = new BlockPos(cell.x(), cell.y(), cell.z());
        if (local && player.distanceToSqr(cell.x() + .5, cell.y() + .5, cell.z() + .5) > 64)
            return "stand within eight blocks of the post";
        var entity = player.serverLevel().getEntity(humanId);
        if (!(entity instanceof Human human)
                || !human.isAlive()
                || !HumanRelations.isOwnedBy(human, player)
                || player.distanceToSqr(human) > 4096)
            return "only your loaded formal soldier nearby can be posted";
        if (!player.serverLevel()
                .noCollision(
                        human,
                        human.getBoundingBox()
                                .move(
                                        cell.x() + .5 - human.getX(),
                                        cell.y() - human.getY(),
                                        cell.z() + .5 - human.getZ())))
            return "post has no clear standing volume";
        dev.felix.hostilehumans.cultivation.CultivationService.interrupt(
                human, "Explicit post assignment", true);
        SoldierOrder.set(human, SoldierOrder.HOLD_POSITION);
        human.getPersistentData().putLong(SoldierOrder.ANCHOR, position.asLong());
        human.getPersistentData()
                .putString(
                        SoldierOrder.ANCHOR_DIMENSION,
                        player.level().dimension().location().toString());
        dev.felix.hostilehumans.soldier.CommandView.persist(human);
        return "post assigned through the existing soldier command/navigation system";
    }
}
