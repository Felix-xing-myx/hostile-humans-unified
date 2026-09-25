package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Set;

/** Temporarily parks hired humans while their owner is not connected. */
final class OwnerOfflinePolicy {
    private static final String ACTIVE = HumanGunner.MOD_ID + ":owner_offline_standby";
    private static final String WAS_SITTING = HumanGunner.MOD_ID + ":owner_offline_was_sitting";

    private OwnerOfflinePolicy() {}

    static boolean tick(Human human) {
        if (!human.hasOwner() || human.level().isClientSide) {
            clearState(human);
            return false;
        }
        boolean offline = human.getServer() == null
                || human.getServer().getPlayerList().getPlayer(human.getOwnerUUID()) == null;
        if (offline) {
            enterStandby(human);
            return true;
        }
        restore(human);
        return false;
    }

    static boolean isOwnerOffline(Human human) {
        return human.hasOwner() && !human.level().isClientSide
                && (human.getServer() == null
                || human.getServer().getPlayerList().getPlayer(human.getOwnerUUID()) == null);
    }

    static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forEachLoadedRecruit(player, OwnerOfflinePolicy::enterStandby);
        }
    }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forEachLoadedRecruit(player, OwnerOfflinePolicy::restore);
        }
    }

    private static void enterStandby(Human human) {
        if (!human.getPersistentData().getBoolean(ACTIVE)) {
            human.getPersistentData().putBoolean(ACTIVE, true);
            human.getPersistentData().putBoolean(WAS_SITTING, human.isOrderedToSit());
        }
        human.setOrderedToSit(true);
        human.forgetSoldierTarget();
        human.getNavigation().stop();
    }

    private static void restore(Human human) {
        if (!human.getPersistentData().getBoolean(ACTIVE)) return;
        boolean wasSitting = human.getPersistentData().getBoolean(WAS_SITTING);
        clearState(human);
        human.setOrderedToSit(wasSitting);
        human.getNavigation().stop();
    }

    private static void clearState(Human human) {
        human.getPersistentData().remove(ACTIVE);
        human.getPersistentData().remove(WAS_SITTING);
    }

    static void clearForDismiss(Human human) {
        clearState(human);
    }

    private static void forEachLoadedRecruit(ServerPlayer player, java.util.function.Consumer<Human> action) {
        HumanServerData data = HumanServerData.get();
        if (data == null) return;
        for (HumanData stored : Set.copyOf(data.getHumanMobs(player.getUUID()))) {
            for (ServerLevel level : player.server.getAllLevels()) {
                Entity entity = level.getEntity(stored.getUUID());
                if (entity instanceof Human human && HumanRelations.isOwnedBy(human, player)) {
                    action.accept(human);
                    break;
                }
            }
        }
    }
}
