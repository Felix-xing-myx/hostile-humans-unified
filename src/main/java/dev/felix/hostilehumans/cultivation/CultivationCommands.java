package dev.felix.hostilehumans.cultivation;

import com.mojang.brigadier.arguments.*;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Explicit test and equivalent server facade operations; no separate command-only training rules.
 */
public final class CultivationCommands {
    private CultivationCommands() {}

    public static void register(RegisterCommandsEvent e) {
        var root = Commands.literal("hhcultivation");
        root.then(
                Commands.literal("open")
                        .executes(
                                c -> {
                                    var p = c.getSource().getPlayerOrException();
                                    var hit = p.pick(8, 0, false);
                                    if (hit instanceof net.minecraft.world.phys.BlockHitResult b
                                            && p.serverLevel().getBlockEntity(b.getBlockPos())
                                                    instanceof
                                                    CultivationTrainingBlockEntity block) {
                                        CultivationFacade.open(p, block);
                                        return 1;
                                    }
                                    throw new com.mojang.brigadier.exceptions
                                                    .SimpleCommandExceptionType(
                                                    net.minecraft.network.chat.Component.literal(
                                                            "Look at a training block"))
                                            .create();
                                }));
        root.then(
                Commands.literal("test")
                        .requires(s -> s.hasPermission(2))
                        .then(
                                Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(
                                                c -> {
                                                    var p = c.getSource().getPlayerOrException();
                                                    if (!p.isCreative()) return 0;
                                                    var hit = p.pick(8, 0, false);
                                                    if (hit
                                                                    instanceof
                                                                    net.minecraft.world.phys
                                                                                            .BlockHitResult
                                                                                    b
                                                            && p.serverLevel()
                                                                            .getBlockEntity(
                                                                                    b.getBlockPos())
                                                                    instanceof
                                                                    CultivationTrainingBlockEntity
                                                                                    block) {
                                                        block.testMode(
                                                                BoolArgumentType.getBool(
                                                                        c, "enabled"));
                                                        CultivationFacade.open(p, block);
                                                        return 1;
                                                    }
                                                    return 0;
                                                })));
        for (var action :
                java.util.List.of(
                        CultivationNetwork.Action.PREVIEW,
                        CultivationNetwork.Action.PAUSE,
                        CultivationNetwork.Action.RESUME,
                        CultivationNetwork.Action.CANCEL,
                        CultivationNetwork.Action.CONFIRM_BREAKTHROUGH,
                        CultivationNetwork.Action.USE_POTENTIAL,
                        CultivationNetwork.Action.EXPAND_PRIMARY)) {
            String name =
                    switch (action) {
                        case CONFIRM_BREAKTHROUGH -> "breakthrough";
                        case USE_POTENTIAL -> "potential";
                        case EXPAND_PRIMARY -> "expand";
                        default -> action.name().toLowerCase(java.util.Locale.ROOT);
                    };
            root.then(
                    Commands.literal(name)
                            .then(
                                    Commands.argument("soldier", EntityArgument.entity())
                                            .executes(
                                                    c -> {
                                                        var actor =
                                                                c.getSource()
                                                                        .getPlayerOrException();
                                                        var target =
                                                                EntityArgument.getEntity(
                                                                        c, "soldier");
                                                        if (!(target instanceof Human)) return 0;
                                                        CultivationFacade.command(
                                                                actor,
                                                                action,
                                                                target.getUUID(),
                                                                CultivationNetwork.Mode.GROW,
                                                                0,
                                                                false,
                                                                java.util.List.of(),
                                                                0);
                                                        return 1;
                                                    })));
        }
        for (var mode : CultivationNetwork.Mode.values())
            root.then(
                    Commands.literal(
                                    mode == CultivationNetwork.Mode.GROW ? "start" : "redistribute")
                            .then(
                                    Commands.argument("soldier", EntityArgument.entity())
                                            .then(
                                                    Commands.argument(
                                                                    "candidate",
                                                                    IntegerArgumentType.integer(
                                                                            0, 2))
                                                            .executes(
                                                                    c -> {
                                                                        var p =
                                                                                c.getSource()
                                                                                        .getPlayerOrException();
                                                                        CultivationFacade.command(
                                                                                p,
                                                                                CultivationNetwork
                                                                                        .Action
                                                                                        .START,
                                                                                EntityArgument
                                                                                        .getEntity(
                                                                                                c,
                                                                                                "soldier")
                                                                                        .getUUID(),
                                                                                mode,
                                                                                IntegerArgumentType
                                                                                        .getInteger(
                                                                                                c,
                                                                                                "candidate"),
                                                                                false,
                                                                                java.util.List.of(),
                                                                                0);
                                                                        return 1;
                                                                    })
                                                            .then(
                                                                    Commands.argument(
                                                                                    "automatic",
                                                                                    BoolArgumentType
                                                                                            .bool())
                                                                            .executes(
                                                                                    c -> {
                                                                                        var p =
                                                                                                c.getSource()
                                                                                                        .getPlayerOrException();
                                                                                        CultivationFacade
                                                                                                .command(
                                                                                                        p,
                                                                                                        CultivationNetwork
                                                                                                                .Action
                                                                                                                .START,
                                                                                                        EntityArgument
                                                                                                                .getEntity(
                                                                                                                        c,
                                                                                                                        "soldier")
                                                                                                                .getUUID(),
                                                                                                        mode,
                                                                                                        IntegerArgumentType
                                                                                                                .getInteger(
                                                                                                                        c,
                                                                                                                        "candidate"),
                                                                                                        BoolArgumentType
                                                                                                                .getBool(
                                                                                                                        c,
                                                                                                                        "automatic"),
                                                                                                        java
                                                                                                                .util
                                                                                                                .List
                                                                                                                .of(),
                                                                                                        0);
                                                                                        return 1;
                                                                                    })))));
        root.then(
                Commands.literal("primary")
                        .then(
                                Commands.argument("soldier", EntityArgument.entity())
                                        .then(
                                                Commands.argument(
                                                                "ids",
                                                                StringArgumentType.greedyString())
                                                        .executes(
                                                                c -> {
                                                                    var p =
                                                                            c.getSource()
                                                                                    .getPlayerOrException();
                                                                    String ids =
                                                                            StringArgumentType
                                                                                    .getString(
                                                                                            c,
                                                                                            "ids");
                                                                    var values =
                                                                            ids.equals("none")
                                                                                    ? java.util.List
                                                                                            .<String>
                                                                                                    of()
                                                                                    : java
                                                                                            .util
                                                                                            .Arrays
                                                                                            .stream(
                                                                                                    ids
                                                                                                            .split(
                                                                                                                    ","))
                                                                                            .map(
                                                                                                    String
                                                                                                            ::trim)
                                                                                            .toList();
                                                                    CultivationFacade.command(
                                                                            p,
                                                                            CultivationNetwork
                                                                                    .Action
                                                                                    .SET_PRIMARY,
                                                                            EntityArgument
                                                                                    .getEntity(
                                                                                            c,
                                                                                            "soldier")
                                                                                    .getUUID(),
                                                                            CultivationNetwork.Mode
                                                                                    .GROW,
                                                                            0,
                                                                            false,
                                                                            values,
                                                                            0);
                                                                    return 1;
                                                                }))));
        for (var action :
                java.util.List.of(
                        CultivationNetwork.Action.DEPOSIT, CultivationNetwork.Action.WITHDRAW))
            root.then(
                    Commands.literal(action.name().toLowerCase(java.util.Locale.ROOT))
                            .then(
                                    Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                            .executes(
                                                    c -> {
                                                        CultivationFacade.command(
                                                                c.getSource()
                                                                        .getPlayerOrException(),
                                                                action,
                                                                CultivationFacade.NONE,
                                                                CultivationNetwork.Mode.GROW,
                                                                0,
                                                                false,
                                                                java.util.List.of(),
                                                                IntegerArgumentType.getInteger(
                                                                        c, "count"));
                                                        return 1;
                                                    })));
        e.getDispatcher().register(root);
    }
}
