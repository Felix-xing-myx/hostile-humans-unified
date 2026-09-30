package club.someoneice.humangunner;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

final class ProgressionCommands {
    private ProgressionCommands() { }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hostilehumans")
                .then(Commands.literal("day")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("get").executes(context -> show(context.getSource())))
                        .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, 1000000))
                                        .executes(context -> {
                                            WorldProgressionData.setDay(context.getSource().getServer(),
                                                    IntegerArgumentType.getInteger(context, "day"));
                                            return changed(context.getSource(), "commands.humangunner.day.changed");
                                        })))
                        .then(Commands.literal("add").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("days", IntegerArgumentType.integer(-1000000, 1000000))
                                        .executes(context -> {
                                            WorldProgressionData.addDays(context.getSource().getServer(),
                                                    IntegerArgumentType.getInteger(context, "days"));
                                            return changed(context.getSource(), "commands.humangunner.day.changed");
                                        })))
                        .then(Commands.literal("sync").requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    WorldProgressionData.syncFromOverworld(context.getSource().getServer());
                                    return changed(context.getSource(), "commands.humangunner.day.synced");
                                }))));
    }

    private static int show(CommandSourceStack source) {
        long day = NaturalSpawnProgression.dayAt(WorldProgressionData.time(source.getServer()));
        long worldDay = NaturalSpawnProgression.dayAt(source.getServer().overworld().getDayTime());
        source.sendSuccess(() -> Component.translatable("commands.humangunner.day.status", day, worldDay), false);
        if (!UnifiedConfig.get().spawn().progression().enabled()) {
            source.sendSuccess(() -> Component.translatable("commands.humangunner.day.disabled"), false);
        }
        return (int) Math.min(Integer.MAX_VALUE, day);
    }

    private static int changed(CommandSourceStack source, String key) {
        long day = NaturalSpawnProgression.dayAt(WorldProgressionData.time(source.getServer()));
        source.sendSuccess(() -> Component.translatable(key, day), true);
        return (int) Math.min(Integer.MAX_VALUE, day);
    }
}
