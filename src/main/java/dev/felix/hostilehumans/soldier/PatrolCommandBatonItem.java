package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PatrolCommandBatonItem extends Item {
    public PatrolCommandBatonItem(Properties p){super(p.stacksTo(1));}
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player p,LivingEntity target,InteractionHand hand){
        if(!(target instanceof Human h))return InteractionResult.PASS;
        if(p instanceof ServerPlayer server)PatrolRouteEditor.human(server,h,p.isShiftKeyDown());
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext c){return useOn(c);}
    @Override public InteractionResult useOn(UseOnContext c){
        if(c.getPlayer() instanceof ServerPlayer p)PatrolRouteEditor.block(p,c.getClickedPos());
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player p,InteractionHand hand){
        if(p instanceof ServerPlayer server&&p.isShiftKeyDown())PatrolRouteEditor.undo(server);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack,Level level,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.translatable("patrol_tool.help"));
    }
}
