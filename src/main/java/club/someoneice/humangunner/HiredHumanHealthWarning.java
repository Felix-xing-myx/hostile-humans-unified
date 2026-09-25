package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Sends one chat warning per critical-health episode to the online owner. */
final class HiredHumanHealthWarning {
    private static final String WARNED = "humangunner:critical_health_warned";
    private static final double WARNING_RATIO = 0.15D;
    private static final double REARM_RATIO = 0.20D;

    private HiredHumanHealthWarning() {
    }

    static void tick(Human human) {
        if (!human.hasOwner() || !human.isAlive() || human.getServer() == null) {
            return;
        }
        double healthRatio = human.getHealth() / Math.max(1.0F, human.getMaxHealth());
        if (healthRatio >= REARM_RATIO) {
            human.getPersistentData().remove(WARNED);
            return;
        }
        if (healthRatio >= WARNING_RATIO || human.getPersistentData().getBoolean(WARNED)) {
            return;
        }
        ServerPlayer owner = human.getServer().getPlayerList().getPlayer(human.getOwnerUUID());
        if (owner == null) {
            // Keep the warning pending until the owner comes online.
            return;
        }
        int percent = Math.max(0, (int) Math.floor(healthRatio * 100.0D));
        owner.displayClientMessage(Component.translatable(
                "message.humangunner.soldier_critical_health", human.getDisplayName(), percent
        ).withStyle(ChatFormatting.RED), false);
        human.getPersistentData().putBoolean(WARNED, true);
    }
}
