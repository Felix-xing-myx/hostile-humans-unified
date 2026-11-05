package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.soldier.RecruitmentContractItem;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.soldier.HumanCommandNetwork;
import dev.felix.hostilehumans.soldier.RecruitmentLedger;
import dev.felix.hostilehumans.soldier.RecruitmentPayment;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;
import dev.felix.hostilehumans.soldier.SoldierDialogue;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import dev.felix.hostilehumans.entity.AggressionMode;
import dev.felix.hostilehumans.entity.HumanServerData;
import dev.felix.hostilehumans.faction.StandingPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** One server transaction for permission, payment and ownership. Contracts are reusable tools, not a second fee. */
public final class FactionRecruitment {
    record Quote(String faction, dev.felix.hostilehumans.economy.Currency currency, long count, int tier, int limit) {}
    private FactionRecruitment() {}
    static Quote quote(ServerPlayer player, Human human, int tier) {
        if(tier<0||tier>3)return null;
        if(player.isCreative())return new Quote(HumanFactions.id(human),dev.felix.hostilehumans.economy.Currency.AZURE,0,tier,UnifiedConfig.get().recruitmentLimit(tier));
        var definition=FactionDefinitions.find(HumanFactions.id(human));
        if(tier>0&&definition==null)return null;
        int base=tier==0?RecruitmentPricing.baseAzureCopper(0):definition.hireCosts().get(tier-1);
        if(definition!=null)base=StandingPolicy.price(base,StandingEffects.trueStanding(human,player),definition.friendlyDiscount(),definition.allyDiscount());
        var allowed=tier==0?java.util.List.of(dev.felix.hostilehumans.economy.Currency.AZURE,dev.felix.hostilehumans.economy.Currency.CRIMSON,
                dev.felix.hostilehumans.economy.Currency.VERDANT,dev.felix.hostilehumans.economy.Currency.ASHEN)
                :dev.felix.hostilehumans.economy.Currency.faction(HumanFactions.id(human)).map(java.util.List::of).orElse(java.util.List.of());
        var inventory=new dev.felix.hostilehumans.economy.InventoryTransaction(player);
        Quote fallback=null;
        for(var currency:allowed){
            var rate=dev.felix.hostilehumans.economy.EconomyTables.current().coefficients().get(currency);
            if(rate==null)continue;
            long price=dev.felix.hostilehumans.economy.MoneyMath.quote(base,1,rate,1,1,true);
            var result=new Quote(HumanFactions.id(human),currency,price,tier,UnifiedConfig.get().recruitmentLimit(tier));
            if(fallback==null)fallback=result;
            if(inventory.total(currency)>=price)return result;
        }
        return fallback;
    }

    public static InteractionResult hire(ServerPlayer player, Human human, int tier, ItemStack contract) {
        if (player.isSpectator() || !human.isAlive() || human.level() != player.level()
                || human.distanceToSqr(player) > 64 || contract.isEmpty()) return fail(player, "invalid_target");
        if (!(contract.getItem() instanceof RecruitmentContractItem item) || item.tier() != tier
                || (player.getMainHandItem() != contract && player.getOffhandItem() != contract)) return fail(player, "invalid_target");
        if (StandingEffects.humanRank(human) != tier || tier < 0 || tier > 3) return fail(player, "wrong_tier");
        if (human.hasOwner() || (HumanRelations.effectiveOwner(human) != null && !HumanRelations.isTemporaryFor(human, player)))
            return fail(player, "owned");
        if (!player.isCreative() && tier > 0 && !StandingEffects.canHire(human, player)) return fail(player, "clearance");
        if (human.getData() == null || HumanServerData.get() == null) return fail(player, "invalid_target");
        Quote quote = quote(player, human, tier);
        if (quote == null) return fail(player, "invalid_payment");
        var ledger = RecruitmentLedger.get(player.server);
        if (ledger.count(player.getUUID(), tier) >= quote.limit()) return fail(player, "limit");
        var inventory = player.getInventory();
        // Server execution is synchronous: all fallible checks precede the only inventory mutation.
        if(!player.isCreative()){
            try{
                var payment=new dev.felix.hostilehumans.economy.InventoryTransaction(player);
                payment.debit(quote.currency(),quote.count());
                if(!payment.publish())return fail(player,"invalid_payment");
            }catch(IllegalArgumentException insufficient){
                player.displayClientMessage(Component.translatable("message.hostile_humans_unified.hire.faction_payment",
                        quote.count(),Component.translatable("item.hostile_humans_unified.coin_"+quote.currency().key()+"_copper"))
                        .withStyle(ChatFormatting.RED),true);
                return InteractionResult.CONSUME;
            }
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
