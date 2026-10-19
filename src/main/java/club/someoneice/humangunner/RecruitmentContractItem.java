package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

public final class RecruitmentContractItem extends Item {
    private final int tier;

    RecruitmentContractItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hostile_humans_unified.contract.faction_terms")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hostile_humans_unified.contract.creative_terms")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    int tier() { return tier; }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)
                || !(target instanceof Human human)) return InteractionResult.PASS;
        // Player.interactOn passes a copy in creative mode. Authorize the actual server-held stack.
        return FactionRecruitment.hire(serverPlayer, human, tier, serverPlayer.getItemInHand(hand));
    }

}
