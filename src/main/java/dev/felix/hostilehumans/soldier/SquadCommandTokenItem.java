package dev.felix.hostilehumans.soldier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Creative-only command token; registration adds no recipe or recruitment reward. */
public final class SquadCommandTokenItem extends Item {
    public SquadCommandTokenItem(Properties properties){super(properties.stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<Component> lines,net.minecraft.world.item.TooltipFlag flag){
        lines.add(Component.translatable("item.hostile_humans_unified.squad_command_token.help").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);if(level.isClientSide)return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer serverPlayer))return InteractionResultHolder.pass(stack);
        if(player.isShiftKeyDown()){
            int number=SquadCommands.cycleToken(serverPlayer,stack);
            serverPlayer.displayClientMessage(Component.translatable(number==0?"message.hostile_humans_unified.squad.invalid_token":"message.hostile_humans_unified.squad.bound",number),true);
        }else{
            if(SquadCommands.boundSquad(serverPlayer,stack)==0&&!SquadCommands.bind(serverPlayer,stack,1))return InteractionResultHolder.fail(stack);
            SquadCommandNetwork.open(serverPlayer,stack);
        }
        return InteractionResultHolder.consume(stack);
    }
}
