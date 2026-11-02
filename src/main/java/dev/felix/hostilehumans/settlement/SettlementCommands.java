package dev.felix.hostilehumans.settlement;

import com.mojang.brigadier.arguments.StringArgumentType;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Explicit bounded maintenance entries; never scans chunks or overwrites ordinary building blocks.
 */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class SettlementCommands {
    private SettlementCommands() {}

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
        event.getDispatcher().register(Commands.literal("hh").then(root));
    }
}
