package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Permission-2 author tool; sneak inspects, ordinary use cycles natural presets. */
public final class NaturalStateToolItem extends Item {
    public NaturalStateToolItem(Properties properties){super(properties.stacksTo(1));}
    @Override public void appendHoverText(ItemStack stack,net.minecraft.world.level.Level level,java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flag){
        lines.add(net.minecraft.network.chat.Component.translatable("item.hostile_humans_unified.natural_state_tool.help").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,LivingEntity target,InteractionHand hand){
        if(!(target instanceof Human human))return InteractionResult.PASS;
        if(player.level().isClientSide)return InteractionResult.SUCCESS;
        return player instanceof ServerPlayer serverPlayer&&NaturalStateCommand.useDebugTool(serverPlayer,human,player.isShiftKeyDown())
                ?InteractionResult.CONSUME:InteractionResult.FAIL;
    }
}
