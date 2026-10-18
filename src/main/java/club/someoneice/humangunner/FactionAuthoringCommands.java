package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Explicit permission-2 authoring operations; ordinary observations never rewrite identity or spawn equipment. */
final class FactionAuthoringCommands {
    private FactionAuthoringCommands() {}
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hh").requires(source -> source.hasPermission(2))
                .then(Commands.literal("faction")
                        .then(Commands.literal("set").then(Commands.argument("entities", EntityArgument.entities())
                                .then(Commands.argument("faction", StringArgumentType.word()).suggests((context, builder) -> {
                                    FactionDefinitions.current().definitions().keySet().stream().sorted()
                                            .forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                }).executes(context -> set(context.getSource(), EntityArgument.getEntities(context, "entities"),
                                        StringArgumentType.getString(context, "faction"), false)))))
                        .then(Commands.literal("fill_near").then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .then(Commands.argument("faction", StringArgumentType.word()).executes(context ->
                                        set(context.getSource(), nearby(context.getSource(), IntegerArgumentType.getInteger(context, "radius")),
                                                StringArgumentType.getString(context, "faction"), true)))))
                        .then(Commands.literal("check_near").then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(context -> check(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))));
    }
    private static List<Human> nearby(CommandSourceStack source, int radius) {
        var point = source.getPosition();
        return source.getLevel().getEntitiesOfClass(Human.class,
                new AABB(point.x - radius, point.y - radius, point.z - radius, point.x + radius, point.y + radius, point.z + radius));
    }
    private static int set(CommandSourceStack source, Collection<? extends Entity> entities, String requested, boolean missingOnly) {
        var definition = FactionDefinitions.find(requested);
        if (definition == null) { source.sendFailure(Component.literal("未知阵营：" + requested)); return 0; }
        int changed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof Human human) || human.getTier() == com.craftix.hostile_humans.entity.entities.HumanTier.ROAMER
                    || (missingOnly && !HumanFactions.id(human).isBlank())) continue;
            String before = HumanFactions.id(human);
            HumanFactions.set(human, definition.id().toString());
            HumanRelations.reconcileTarget(human);
            if (!before.equals(HumanFactions.id(human))) changed++;
        }
        int count = changed;
        source.sendSuccess(() -> Component.literal("已修改 " + count + " 名人类的阵营；已存属性与装备保持原结果。"), true);
        return changed;
    }
    private static int check(CommandSourceStack source, int radius) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Human human : nearby(source, radius)) {
            String faction = human.getTier() == com.craftix.hostile_humans.entity.entities.HumanTier.ROAMER ? "流浪者"
                    : HumanFactions.id(human).isBlank() ? "未指定" : HumanFactions.id(human)
                    + (FactionDefinitions.find(HumanFactions.id(human)) == null ? "（定义缺失）" : "");
            counts.merge(faction, 1, Integer::sum);
        }
        source.sendSuccess(() -> Component.literal("附近居民阵营：" + counts), false);
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
