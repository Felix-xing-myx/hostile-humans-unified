package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Author-only tools; not a player route editor. Registration is owned by the mod entry point. */
public final class NaturalStateCommand {
    private NaturalStateCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hh").then(Commands.literal("natural").requires(source->source.hasPermission(2))
                .then(Commands.literal("set").then(Commands.argument("entity",EntityArgument.entity())
                        .then(Commands.argument("stay",BoolArgumentType.bool())
                                .then(Commands.argument("chase",BoolArgumentType.bool())
                                        .then(Commands.argument("radius",IntegerArgumentType.integer(8,96)).executes(context-> {
                                            Entity entity=EntityArgument.getEntity(context,"entity");
                                            if(!(entity instanceof Human human))return fail(context.getSource(),"目标必须是人类");
                                            NaturalState.set(human,new GarrisonSettings(BoolArgumentType.getBool(context,"stay"),
                                                    BoolArgumentType.getBool(context,"chase"),IntegerArgumentType.getInteger(context,"radius")));
                                            return report(context.getSource(),human);
                                        }))))))
                .then(Commands.literal("clear").then(Commands.argument("entity",EntityArgument.entity()).executes(context->{
                    Entity entity=EntityArgument.getEntity(context,"entity");
                    if(!(entity instanceof Human human))return fail(context.getSource(),"目标必须是人类");
                    NaturalState.clear(human);context.getSource().sendSuccess(()->Component.literal("已清除自然作者状态；无主时恢复自由游荡"),true);return 1;
                })))
                .then(Commands.literal("check").then(Commands.argument("entity",EntityArgument.entity()).executes(context->{
                    Entity entity=EntityArgument.getEntity(context,"entity");
                    return entity instanceof Human human?report(context.getSource(),human):fail(context.getSource(),"目标必须是人类");
                })))));
    }
    private static int fail(CommandSourceStack source,String reason){source.sendFailure(Component.literal(reason));return 0;}
    private static int report(CommandSourceStack source,Human human) {
        source.sendSuccess(()->Component.literal(describe(human)),false);return 1;
    }
    public static String describe(Human human) {
        if(!NaturalState.hasState(human))return "自然状态：自由游荡";
        GarrisonSettings settings=GarrisonSettings.natural(human);
        OrderDistances.Context context=NaturalState.context(human,settings);
        String route=NaturalRoute.hasRoute(human,context)?NaturalRoute.definition(human,context).error():"none";
        return "自然状态：stay="+settings.stay()+", chase="+settings.chase()+", radius="+settings.radius()
                +", origin="+context.reason()+", route="+(route.isEmpty()?"ready":route)
                +", runtime="+NaturalRoute.status(human)+(human.hasOwner()?"；雇佣命令当前优先":"");
    }
    /** Debug item hook: sneak inspects; ordinary use cycles the four author presets. */
    public static boolean useDebugTool(ServerPlayer player,Human human,boolean inspect) {
        if(!player.hasPermissions(2))return false;
        if(!inspect){
            GarrisonSettings old=GarrisonSettings.natural(human);
            int preset=NaturalState.hasState(human)?(old.stay()?2:0)+(old.chase()?1:0): -1;
            int next=(preset+1)%4;
            NaturalState.set(human,new GarrisonSettings((next&2)!=0,(next&1)!=0,old.radius()));
        }
        player.displayClientMessage(Component.literal(describe(human)),false);return true;
    }
}
