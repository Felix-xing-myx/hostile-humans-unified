package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.AggressionMode;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.StandingPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** One server transaction for permission, payment and ownership. Contracts are reusable tools, not a second fee. */
final class FactionRecruitment {
    record Quote(String faction, Item currency, int count, int tier, int limit) {}
    private FactionRecruitment() {}
    static Quote quote(ServerPlayer player, Human human, int tier) {
        var definition = FactionDefinitions.find(HumanFactions.id(human));
        if (definition == null || tier < 1 || tier > 3) return null;
        Item currency = ForgeRegistries.ITEMS.getValue(definition.hireItem());
        if (currency == null || currency == net.minecraft.world.item.Items.AIR) return null;
        int price = StandingPolicy.price(definition.hireCosts().get(tier - 1), StandingEffects.trueStanding(human, player),
                definition.friendlyDiscount(), definition.allyDiscount());
        return new Quote(definition.id().toString(), currency, price, tier, UnifiedConfig.get().recruitmentLimit(tier));
    }
    static InteractionResult hire(ServerPlayer player, Human human, int tier, ItemStack contract) {
        if (player.isSpectator() || !human.isAlive() || human.level() != player.level()
                || human.distanceToSqr(player) > 64 || contract.isEmpty()) return fail(player, "invalid_target");
        if (!(contract.getItem() instanceof RecruitmentContractItem item) || item.tier() != tier
                || (player.getMainHandItem() != contract && player.getOffhandItem() != contract)) return fail(player, "invalid_target");
        if (StandingEffects.humanRank(human) != tier || tier < 1 || tier > 3) return fail(player, "wrong_tier");
        if (human.hasOwner() || (HumanRelations.effectiveOwner(human) != null && !HumanRelations.isTemporaryFor(human, player)))
            return fail(player, "owned");
        if (!StandingEffects.canHire(human, player)) return fail(player, "clearance");
        if (human.getData() == null || HumanServerData.get() == null) return fail(player, "invalid_target");
        Quote quote = quote(player, human, tier);
        if (quote == null) return fail(player, "invalid_payment");
        var ledger = RecruitmentLedger.get(player.server);
        if (ledger.count(player.getUUID(), tier) >= quote.limit()) return fail(player, "limit");
        var inventory = player.getInventory();
        // Server execution is synchronous: all fallible checks precede the only inventory mutation.
        if (!player.isCreative() && !RecruitmentPayment.pay(false, quote.count(), inventory.items.size(),
                slot -> inventory.items.get(slot).is(quote.currency()) ? inventory.items.get(slot).getCount() : 0,
                (slot, count) -> inventory.items.get(slot).shrink(count))) {
            player.displayClientMessage(Component.translatable("message.hostile_humans_unified.hire.payment",
                    quote.count(), quote.currency().getDescription()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        human.tame(player);
        human.setTarget(null);
        human.setAggressionLevel(AggressionMode.AGGRESSIVE_MONSTER);
        human.getPersistentData().putInt(HumanRelations.HIRED_TIER, tier);
        for (String field : new String[]{HumanRelations.TEMP_OWNER, HumanRelations.TEMP_UNTIL,
                HumanRelations.NO_DROPS, HumanRelations.FORCED_HOSTILE_TARGET}) human.getPersistentData().remove(field);
        SoldierOrder.set(human, SoldierOrder.FOLLOW);
        SoldierCombatMode.set(human, SoldierCombatMode.ACTIVE);
        ledger.hire(human.getUUID(), player.getUUID(), tier);
        HumanServerData.get().updateOrRegisterHumanMob(human);
        inventory.setChanged(); player.containerMenu.broadcastChanges();
        SoldierDialogue.hired(player, human);
        HumanCommandNetwork.openFor(player, human);
        return InteractionResult.CONSUME;
    }
    private static InteractionResult fail(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("message.hostile_humans_unified.hire." + reason)
                .withStyle(ChatFormatting.RED), true);
        return InteractionResult.CONSUME;
    }
}
