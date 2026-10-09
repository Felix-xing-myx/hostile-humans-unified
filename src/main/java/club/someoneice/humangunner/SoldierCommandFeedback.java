package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/** Shared localized chat acknowledgements for direct and radio commands. */
final class SoldierCommandFeedback {
    private SoldierCommandFeedback() {}

    static void say(ServerPlayer player, Human human, Component message) {
        player.displayClientMessage(Component.translatable(
                "message.hostile_humans_unified.soldier.speech", human.getDisplayName(), message), false);
    }

    static void order(ServerPlayer player, Human human, SoldierOrder order) {
        say(player, human, Component.translatable("dialogue.hostile_humans_unified.order."
                + order.name().toLowerCase(Locale.ROOT)));
    }

    static void combat(ServerPlayer player, Human human, SoldierCombatMode mode) {
        say(player, human, Component.translatable("dialogue.hostile_humans_unified.combat."
                + mode.name().toLowerCase(Locale.ROOT)));
    }

    static void pickup(ServerPlayer player, Human human, boolean enabled) {
        say(player, human, Component.translatable("message.hostile_humans_unified.pickup.changed",
                Component.translatable(enabled ? "screen.hostile_humans_unified.soldier.pickup.enabled"
                        : "screen.hostile_humans_unified.soldier.pickup.disabled")));
    }
}
