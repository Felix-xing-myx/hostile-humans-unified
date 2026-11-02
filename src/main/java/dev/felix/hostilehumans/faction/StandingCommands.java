package dev.felix.hostilehumans.faction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

/** Explicit operator diagnostics, separate from political event attribution. */
public final class StandingCommands {
    private StandingCommands() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("standing");
        for (String mode : java.util.List.of("get", "set", "add")) {
            var faction = Commands.argument("faction", StringArgumentType.word()).suggests((context, builder) -> {
                FactionDefinitions.current().definitions().keySet().stream().sorted().forEach(id -> builder.suggest(id.toString()));
                return builder.buildFuture();
            });
            if (mode.equals("get")) faction.executes(context -> execute(context, mode));
            else faction.then(Commands.argument("value", IntegerArgumentType.integer(-500, 500))
                    .executes(context -> execute(context, mode)));
            root.then(Commands.literal(mode).then(Commands.argument("player", EntityArgument.player()).then(faction)));
        }
        dispatcher.register(Commands.literal("hh").requires(source -> source.hasPermission(2)).then(root));
    }
    private static int execute(CommandContext<CommandSourceStack> context, String mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = EntityArgument.getPlayer(context, "player");
        var definition = FactionDefinitions.find(StringArgumentType.getString(context, "faction"));
        if (definition == null) { context.getSource().sendFailure(Component.literal("未知阵营")); return 0; }
        var data = FactionStandingData.get(context.getSource().getServer());
        String faction = definition.id().toString();
        int value = data.standing(player.getUUID(), faction);
        if (!mode.equals("get")) value = data.set(player.getUUID(), faction,
                (mode.equals("add") ? (long) value : 0L) + IntegerArgumentType.getInteger(context, "value"));
        int result = value;
        context.getSource().sendSuccess(() -> Component.literal(player.getScoreboardName() + " / " + faction + "：真实声望 " + result), !mode.equals("get"));
        return 1;
    }
}
