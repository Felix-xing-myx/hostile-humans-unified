package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.behavior.combat.PeacefulHumanPolicy;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Creative operator tool for clearing test humans from the current save. */
public final class WildHumanCleanupItem extends Item {
    private static final String KEY = "item.hostile_humans_unified.wild_human_cleanup";

    public WildHumanCleanupItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer server) || !server.canUseGameMasterBlocks()) {
            player.displayClientMessage(Component.translatable(KEY + ".permission"), true);
            return InteractionResultHolder.fail(stack);
        }
        server.getCooldowns().addCooldown(this, 10);
        int removed = PeacefulHumanPolicy.clearUnhired(server.serverLevel().getServer());
        server.displayClientMessage(Component.translatable(KEY + ".result", removed), false);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(KEY + ".tooltip"));
    }
}
