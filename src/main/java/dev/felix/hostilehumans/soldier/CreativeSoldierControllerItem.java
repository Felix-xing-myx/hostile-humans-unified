package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Creative authoring only. It never creates an employer, bypasses another owner or writes permanent NoAI. */
public final class CreativeSoldierControllerItem extends Item {
    public CreativeSoldierControllerItem(Properties p){super(p.stacksTo(1));}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player p,LivingEntity target,InteractionHand hand){
        if(!(target instanceof Human h))return InteractionResult.PASS;
        if(!(p instanceof ServerPlayer server))return InteractionResult.SUCCESS;
        if(!PatrolControlService.mayControl(server,h)||p.distanceToSqr(h)>64){
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("patrol_tool.creative_only"),true);return InteractionResult.FAIL;}
        if(CommandRateLimit.allow(server))PatrolToolNetwork.openController(server,h);
        return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.translatable("developer_guide.hover"));
    }
}
