package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Persisted temporary standby, bound to the owner who issued the original order. */
final class OwnerOfflinePolicy {
    private static final String RESUME = HumanGunner.MOD_ID + ":owner_offline_resume";
    private static final String ACTIVE = HumanGunner.MOD_ID + ":owner_offline_standby";
    private static final String WAS_SITTING = HumanGunner.MOD_ID + ":owner_offline_was_sitting";

    private OwnerOfflinePolicy() {}

    static void tick(Human human) {
        if (human.level().isClientSide) return;
        if (!human.hasOwner()) {
            clearForDismiss(human);
            return;
        }
        // Loaded soldiers are handled immediately at logout. This also covers
        // soldiers whose chunks load later, including after a server restart.
        if (human.tickCount > 1 && (human.tickCount + human.getId()) % 20 != 0) return;
        MinecraftServer server = human.getServer();
        UUID owner = human.getOwnerUUID();
        if (server == null || owner == null) return;
        if (human.getPersistentData().contains(RESUME)
                && resumeOrder(human.getPersistentData(), owner, SoldierOrder.get(human)) == null) {
            clearForDismiss(human);
        }
        if (server.getPlayerList().getPlayer(owner) == null) {
            guardOfflineFollower(human, server);
        } else {
            restoreOnlineFollower(human);
        }
    }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Human human && player.getUUID().equals(human.getOwnerUUID())) {
                    restoreOnlineFollower(human);
                }
            }
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
        SoldierOrder original = SoldierOrder.get(human);
        if (!shouldGuard(human.hasOwner(), human.isAlive(), original)) return;
        UUID owner = human.getOwnerUUID();
        if (owner == null) return;
        boolean escaping = human.isInLava() || human.isFleeing || human.isSeekingShore();
        SoldierOrder.set(human, SoldierOrder.GUARD, escaping);
        // set() cancels older resume records. Save only after the temporary
        // transition; repeated offline ticks must not replace the original order.
        remember(human.getPersistentData(), owner, original);
        human.setOrderedToSit(false);
        if (!escaping) {
            MovementSpeedController.normal(human);
        }
        HiredHumanRecall.cancelPending(server, human.getUUID());
        // Forge persists this record with the entity, including chunk unloads
        // and normal server restarts. Neither transition changes combat mode.
    }

    private static void restoreOnlineFollower(Human human) {
        if (!human.hasOwner() || !human.isAlive()) return;
        MinecraftServer server = human.getServer();
        if (server == null || server.getPlayerList().getPlayer(human.getOwnerUUID()) == null) return;
        if (RecruitmentLedger.get(server).pendingDismissalOwner(human.getUUID()) != null) {
            clearForDismiss(human);
            return;
        }
        SoldierOrder original = resumeOrder(human.getPersistentData(), human.getOwnerUUID(), SoldierOrder.get(human));
        if (original == null) return;
        boolean escaping = human.isInLava() || human.isFleeing || human.isSeekingShore();
        SoldierOrder.set(human, original, escaping); // Consumes the record once.
    }

    static void remember(CompoundTag data, UUID owner, SoldierOrder original) {
        if (owner == null || !shouldGuard(true, true, original)) return;
        CompoundTag record = new CompoundTag();
        record.putUUID("Owner", owner);
        record.putString("Order", original.name());
        data.put(RESUME, record);
    }

    /** Reject missing, malformed, transferred or superseded commands. */
    static SoldierOrder resumeOrder(CompoundTag data, UUID owner, SoldierOrder current) {
        if (owner == null || current != SoldierOrder.GUARD) return null;
        CompoundTag record = data.getCompound(RESUME);
        if (!record.hasUUID("Owner") || !owner.equals(record.getUUID("Owner"))) return null;
        String value = record.getString("Order");
        if (SoldierOrder.FOLLOW.name().equals(value)) return SoldierOrder.FOLLOW;
        if (SoldierOrder.BODYGUARD.name().equals(value)) return SoldierOrder.BODYGUARD;
        return null;
    }

    static boolean shouldGuard(boolean hired, boolean alive, SoldierOrder order) {
        return hired && alive && (order == SoldierOrder.FOLLOW || order == SoldierOrder.BODYGUARD);
    }

    static void clearForDismiss(Human human) {
        human.getPersistentData().remove(RESUME);
        human.getPersistentData().remove(ACTIVE);
        human.getPersistentData().remove(WAS_SITTING);
    }
}
