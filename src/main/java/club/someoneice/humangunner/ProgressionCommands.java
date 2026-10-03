package club.someoneice.humangunner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

final class ProgressionCommands {
    private ProgressionCommands() { }
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hostilehumans")
                .then(Commands.literal("day")
                        .executes(c -> show(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.literal("get")
                                .executes(c -> show(c.getSource(), c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                        .executes(c -> show(c.getSource(), EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 1000000))
                                        .executes(c -> change(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "day"), true))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> change(c.getSource(), EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "day"), true)))))
                        .then(Commands.literal("add").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("days", IntegerArgumentType.integer(-1000000, 1000000))
                                        .executes(c -> change(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "days"), false))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> change(c.getSource(), EntityArgument.getPlayer(c, "player"), IntegerArgumentType.getInteger(c, "days"), false)))))));
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        long day = NaturalSpawnProgression.dayAt(PlayerProgressionData.time(player));
        source.sendSuccess(() -> Component.translatable("commands.humangunner.day.status", player.getDisplayName(), day), false);
        if (!UnifiedConfig.get().spawn().progression().enabled()) {
            source.sendSuccess(() -> Component.translatable("commands.humangunner.day.disabled"), false);
        }
        return (int) Math.min(Integer.MAX_VALUE, day);
    }

    private static int change(CommandSourceStack source, ServerPlayer player, int days, boolean absolute) {
        PlayerProgressionData.change(player, days, absolute);
        long day = NaturalSpawnProgression.dayAt(PlayerProgressionData.time(player));
        source.sendSuccess(() -> Component.translatable("commands.humangunner.day.changed", player.getDisplayName(), day), true);
        return (int) Math.min(Integer.MAX_VALUE, day);
    }
}
