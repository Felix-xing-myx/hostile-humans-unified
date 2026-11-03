package dev.felix.hostilehumans.settlement;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Explicit maintenance entries and confirmed placement of bounded template files.
 */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class SettlementCommands {
    private SettlementCommands() {}

    private static LiteralArgumentBuilder<CommandSourceStack> templates() {
        return Commands.literal("template")
                .then(Commands.literal("preview")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .then(Commands.argument("corner", BlockPosArgument.blockPos())
                                        .executes(c -> preview(c, 0, Mirror.NONE, true))
                                        .then(Commands.argument("turns", IntegerArgumentType.integer(0, 3))
                                                .executes(c -> preview(c, IntegerArgumentType.getInteger(c, "turns"),
                                                        Mirror.NONE, true))
                                                .then(placementVariant("none", Mirror.NONE))
                                                .then(placementVariant("left_right", Mirror.LEFT_RIGHT))
                                                .then(placementVariant("front_back", Mirror.FRONT_BACK))))))
                .then(Commands.literal("confirm").executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    return report(c, () -> SettlementTemplateFiles.confirm(player));
                }))
                .then(Commands.literal("cancel").executes(c -> {
                    SettlementTemplateFiles.cancel(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.literal("Template preview cleared."), false);
                    return 1;
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> placementVariant(String name, Mirror mirror) {
        return Commands.literal(name)
                .executes(c -> preview(c, IntegerArgumentType.getInteger(c, "turns"), mirror, true))
                .then(Commands.argument("entities", BoolArgumentType.bool())
                        .executes(c -> preview(c, IntegerArgumentType.getInteger(c, "turns"), mirror,
                                BoolArgumentType.getBool(c, "entities"))));
    }

    private static int preview(CommandContext<CommandSourceStack> c, int turns, Mirror mirror, boolean entities)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = c.getSource().getPlayerOrException();
        var corner = BlockPosArgument.getLoadedBlockPos(c, "corner");
        var id = ResourceLocationArgument.getId(c, "id");
        return report(c, () -> SettlementTemplateFiles.preview(player, id, corner,
                switch (turns) {
                    case 1 -> Rotation.CLOCKWISE_90;
                    case 2 -> Rotation.CLOCKWISE_180;
                    case 3 -> Rotation.COUNTERCLOCKWISE_90;
                    default -> Rotation.NONE;
                }, mirror, entities));
    }

    private static int report(CommandContext<CommandSourceStack> c, java.util.function.Supplier<String> action) {
        try {
            String message = action.get();
            c.getSource().sendSuccess(() -> Component.literal(message), false);
            return 1;
        } catch (IllegalArgumentException invalid) {
            c.getSource().sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("settlement").requires(s -> s.hasPermission(2));
        root.then(
                Commands.literal("list")
                        .executes(
                                c -> {
                                    var player = c.getSource().getPlayerOrException();
                                    var values = SettlementIndex.get(player.serverLevel()).all();
                                    for (var value : values.stream().limit(20).toList())
                                        c.getSource()
                                                .sendSuccess(
                                                        () ->
                                                                Component.literal(
                                                                        value.id()
                                                                                + " "
                                                                                + value.name()
                                                                                + " revision="
                                                                                + value.revision()
                                                                                + " modules="
                                                                                + value.modules()
                                                                                        .size()),
                                                        false);
                                    c.getSource()
                                            .sendSuccess(
                                                    () ->
                                                            Component.literal(
                                                                    "Dimension instances: "
                                                                            + values.size()
                                                                            + "; showing at most"
                                                                            + " 20"),
                                                    false);
                                    return 1;
                                }));
        root.then(
                Commands.literal("restore_previous")
                        .then(
                                Commands.argument("instance", StringArgumentType.word())
                                        .executes(
                                                c -> {
                                                    var p = c.getSource().getPlayerOrException();
                                                    var id =
                                                            UUID.fromString(
                                                                    StringArgumentType.getString(
                                                                            c, "instance"));
                                                    var index =
                                                            SettlementIndex.get(p.serverLevel());
                                                    var old = index.find(id);
                                                    var result =
                                                            index.restorePrevious(
                                                                    id,
                                                                    old == null
                                                                            ? 0
                                                                            : old.revision());
                                                    c.getSource()
                                                            .sendSuccess(
                                                                    () ->
                                                                            Component.literal(
                                                                                    result
                                                                                            .reason()),
                                                                    true);
                                                    return result.changed() ? 1 : 0;
                                                })));
        root.then(
                Commands.literal("restore_carrier")
                        .then(
                                Commands.argument("instance", StringArgumentType.word())
                                        .then(
                                                Commands.argument(
                                                                "module", StringArgumentType.word())
                                                        .executes(
                                                                c -> {
                                                                    var p =
                                                                            c.getSource()
                                                                                    .getPlayerOrException();
                                                                    var id =
                                                                            UUID.fromString(
                                                                                    StringArgumentType
                                                                                            .getString(
                                                                                                    c,
                                                                                                    "instance"));
                                                                    String key =
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "module");
                                                                    var instance =
                                                                            SettlementIndex.get(
                                                                                            p
                                                                                                    .serverLevel())
                                                                                    .find(id);
                                                                    if (instance == null) return 0;
                                                                    var module =
                                                                            instance
                                                                                    .modules()
                                                                                    .stream()
                                                                                    .filter(
                                                                                            m ->
                                                                                                    m.key()
                                                                                                            .equals(
                                                                                                                    key))
                                                                                    .findFirst()
                                                                                    .orElse(null);
                                                                    if (module == null) return 0;
                                                                    var at =
                                                                            module
                                                                                    .informationBlock();
                                                                    if (!p.serverLevel()
                                                                                    .hasChunkAt(at)
                                                                            || p.distanceToSqr(
                                                                                            at
                                                                                                            .getX()
                                                                                                    + .5,
                                                                                            at
                                                                                                            .getY()
                                                                                                    + .5,
                                                                                            at
                                                                                                            .getZ()
                                                                                                    + .5)
                                                                                    > 4096) {
                                                                        c.getSource()
                                                                                .sendFailure(
                                                                                        Component
                                                                                                .literal(
                                                                                                        "Original"
                                                                                                            + " carrier"
                                                                                                            + " must"
                                                                                                            + " be loaded"
                                                                                                            + " and within"
                                                                                                            + " 64 blocks."));
                                                                        return 0;
                                                                    }
                                                                    var state =
                                                                            p.serverLevel()
                                                                                    .getBlockState(
                                                                                            at);
                                                                    if (state.isAir())
                                                                        p.serverLevel()
                                                                                .setBlock(
                                                                                        at,
                                                                                        SettlementContent
                                                                                                .INFORMATION
                                                                                                .get()
                                                                                                .defaultBlockState(),
                                                                                        3);
                                                                    else if (!(state.getBlock()
                                                                            instanceof
                                                                            SettlementInformationBlock)) {
                                                                        c.getSource()
                                                                                .sendFailure(
                                                                                        Component
                                                                                                .literal(
                                                                                                        "Refusing"
                                                                                                            + " to overwrite"
                                                                                                            + " ordinary"
                                                                                                            + " building"
                                                                                                            + " block."));
                                                                        return 0;
                                                                    }
                                                                    String result =
                                                                            SettlementAuthoring
                                                                                    .restore(
                                                                                            p,
                                                                                            at,
                                                                                            id,
                                                                                            key,
                                                                                            instance
                                                                                                    .revision());
                                                                    c.getSource()
                                                                            .sendSuccess(
                                                                                    () ->
                                                                                            Component
                                                                                                    .literal(
                                                                                                            result),
                                                                                    true);
                                                                    SettlementNetwork.openAuthor(
                                                                            p, at);
                                                                    return 1;
                                                                }))));
        root.then(templates());
        event.getDispatcher().register(Commands.literal("hh").then(root));
    }
}
