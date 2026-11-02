package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.navigation.SafePositions;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** One-use soldier protection. Acquisition is deliberately creative-only until trading/loot is authored. */
public final class SoldierRecallCharmItem extends Item {
    public static final double INVENTORY_VALUE = 10_000;
    public SoldierRecallCharmItem(Properties properties) { super(properties.stacksTo(1).fireResistant()); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.FAIL;
        var feet = context.getClickedPos().above();
        if (!SafePositions.isSafe(level, feet)) {
            player.displayClientMessage(Component.translatable("recall_charm.unsafe"), true);
            return InteractionResult.FAIL;
        }
        new SoldierRecallAnchor(level.dimension().location(), feet).bind(context.getItemInHand());
        player.displayClientMessage(Component.translatable("recall_charm.bound", feet.toShortString(), level.dimension().location().toString()), false);
        return InteractionResult.CONSUME;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!level.isClientSide) player.displayClientMessage(SoldierRecallAnchor.of(stack)
                .map(anchor -> Component.translatable("recall_charm.bound", anchor.position().toShortString(), anchor.dimension().toString()))
                .orElse(Component.translatable("recall_charm.unbound")), false);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("recall_charm.help").withStyle(ChatFormatting.GRAY));
        lines.add(SoldierRecallAnchor.of(stack).map(anchor -> Component.translatable("recall_charm.bound", anchor.position().toShortString(), anchor.dimension().toString()))
                .orElse(Component.translatable("recall_charm.unbound")).withStyle(ChatFormatting.AQUA));
    }
}
