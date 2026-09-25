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
        tooltip.add(Component.translatable("item.humangunner.contract.tooltip",
                RecruitmentPolicy.cost(tier), RecruitmentPolicy.limit(tier))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)
                || !(target instanceof Human human)) return InteractionResult.PASS;
        if (IdentityBadgeAccess.humanRank(human) != tier) return fail(player, "wrong_tier");
        if (!RecruitmentPolicy.hasContractClearance(
                player.isCreative(),
                IdentityBadgeAccess.isNeutralTo(human, player),
                IdentityBadgeAccess.mayRetaliateAgainst(human, player)
        )) return fail(player, "clearance");
        if (human.hasOwner()) return fail(player, "owned");
        if (human.getPersistentData().hasUUID(HumanRelations.TEMP_OWNER)
                && !HumanRelations.isTemporaryFor(human, player)) return fail(player, "owned");
        if (RecruitmentLedger.get(serverPlayer.server).count(player.getUUID(), tier) >= RecruitmentPolicy.limit(tier))
            return fail(player, "limit");
        if (player.isSpectator()) return InteractionResult.PASS;
        var inventory = player.getInventory();
        if (!RecruitmentPayment.pay(player.isCreative(), RecruitmentPolicy.cost(tier), inventory.items.size(),
                slot -> inventory.items.get(slot).is(net.minecraft.world.item.Items.EMERALD)
                        ? inventory.items.get(slot).getCount() : 0,
                (slot, count) -> inventory.items.get(slot).shrink(count))) return fail(player, "emeralds");

        if (!player.isCreative()) {
            stack.shrink(1);
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
        human.tame(player);
        human.setTarget(null);
        human.setAggressionLevel(com.craftix.hostile_humans.entity.AggressionMode.AGGRESSIVE_MONSTER);
        human.getPersistentData().putInt(HumanRelations.HIRED_TIER, tier);
        human.getPersistentData().remove(HumanRelations.TEMP_OWNER);
        human.getPersistentData().remove(HumanRelations.TEMP_UNTIL);
        human.getPersistentData().remove(HumanRelations.NO_DROPS);
        SoldierOrder.set(human, SoldierOrder.FOLLOW);
        SoldierCombatMode.set(human, SoldierCombatMode.ACTIVE);
        RecruitmentLedger.get(serverPlayer.server).hire(human.getUUID(), player.getUUID(), tier);
        com.craftix.hostile_humans.entity.data.HumanServerData.get().updateOrRegisterHumanMob(human);
        player.displayClientMessage(Component.translatable("message.humangunner.hired"), false);
        HumanCommandNetwork.openFor(serverPlayer, human);
        return InteractionResult.CONSUME;
    }

    private static InteractionResult fail(Player player, String reason) {
        player.displayClientMessage(Component.translatable("message.humangunner.hire." + reason)
                .withStyle(ChatFormatting.RED), true);
        return InteractionResult.CONSUME;
    }
}
