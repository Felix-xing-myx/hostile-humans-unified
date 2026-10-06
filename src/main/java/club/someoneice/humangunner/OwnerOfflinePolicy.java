package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Offline followers guard their current location; all other orders remain intact. */
final class OwnerOfflinePolicy {
    private static final String ACTIVE = HumanGunner.MOD_ID + ":owner_offline_standby";
    private static final String WAS_SITTING = HumanGunner.MOD_ID + ":owner_offline_was_sitting";

    private OwnerOfflinePolicy() {}

    static void tick(Human human) {
        if (human.level().isClientSide) return;
        if (human.hasOwner() && human.getPersistentData().getBoolean(ACTIVE)) {
            human.setOrderedToSit(human.getPersistentData().getBoolean(WAS_SITTING));
        }
        clearForDismiss(human);
        // Loaded soldiers are handled immediately at logout. This also covers
        // soldiers whose chunks load later, including after a server restart.
        if (human.tickCount > 1 && (human.tickCount + human.getId()) % 20 != 0) return;
        MinecraftServer server = human.getServer();
        UUID owner = human.getOwnerUUID();
        if (server != null && owner != null && server.getPlayerList().getPlayer(owner) == null) {
            guardOfflineFollower(human, server);
        }
    }

    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Human human && player.getUUID().equals(human.getOwnerUUID())) {
                    guardOfflineFollower(human, player.server);
                }
            }
        }
    }

    private static void guardOfflineFollower(Human human, MinecraftServer server) {
        if (!shouldGuard(human.hasOwner(), human.isAlive(), SoldierOrder.get(human))) return;
        boolean escaping = human.isInLava() || human.isFleeing || human.isSeekingShore();
        SoldierOrder.set(human, SoldierOrder.GUARD, escaping);
        human.setOrderedToSit(false);
        if (!escaping) {
            MovementSpeedController.normal(human);
        }
        HiredHumanRecall.cancelPending(server, human.getUUID());
        // SoldierCombatMode is deliberately preserved. GUARD stores this
        // position as its anchor and does not resume FOLLOW on the next login.
    }

    static boolean shouldGuard(boolean hired, boolean alive, SoldierOrder order) {
        return hired && alive && order == SoldierOrder.FOLLOW;
    }

    static void clearForDismiss(Human human) {
        human.getPersistentData().remove(ACTIVE);
        human.getPersistentData().remove(WAS_SITTING);
    }
}
