package dev.felix.hostilehumans.economy;

import com.mojang.brigadier.arguments.StringArgumentType;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.FactionContent;
import dev.felix.hostilehumans.faction.FactionEconomyLedger;
import dev.felix.hostilehumans.faction.FactionPassage;
import dev.felix.hostilehumans.faction.HumanFactions;
import dev.felix.hostilehumans.faction.StandingAccess;

import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;

import java.util.*;

/**
 * Gameplay adapters, excluding Human damage/death (owned by StandingEffects' confirmed pipeline).
 */
public final class EconomyEvents {
    private static boolean installed;
    private static final Set<Human> NATURAL = Collections.newSetFromMap(new WeakHashMap<>());

    private record Broken(
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.core.BlockPos position,
            net.minecraft.world.level.block.state.BlockState before,
            UUID owner,
            Set<String> factions,
            long tick) {}

    private static final Map<net.minecraft.server.MinecraftServer, ArrayDeque<Broken>> BREAKS =
            new WeakHashMap<>();

    private EconomyEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breaking(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || dev.felix.hostilehumans.settlement.SettlementAccess.author(player)) return;
        var query =
                dev.felix.hostilehumans.settlement.SettlementIndex.get(player.serverLevel())
                        .query(
                                event.getPos().getX() + .5,
                                event.getPos().getY(),
                                event.getPos().getZ() + .5);
        if (query.unknown()) return;
        var factions = new HashSet<String>();
        for (var hit : query.hits()) {
            var site =
                    dev.felix.hostilehumans.settlement.SettlementIndex.get(player.serverLevel())
                            .find(hit.instance());
            if (site != null) factions.add(site.faction());
        }
        if (factions.isEmpty()) return;
        var queue = BREAKS.computeIfAbsent(player.getServer(), k -> new ArrayDeque<>());
        if (queue.size() >= 256) return;
        queue.add(
                new Broken(
                        player.serverLevel(),
                        event.getPos().immutable(),
                        event.getState(),
                        player.getUUID(),
                        Set.copyOf(factions),
                        player.getServer().overworld().getGameTime()));
    }

    @SubscribeEvent
    public static void broken(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var queue = BREAKS.get(event.getServer());
        if (queue == null) return;
        int budget = 64;
        long now = event.getServer().overworld().getGameTime();
        while (!queue.isEmpty() && budget-- > 0) {
            var pending = queue.peek();
            if (pending.tick() >= now) break;
            queue.remove();
            if (now - pending.tick() > 20
                    || !pending.level().hasChunkAt(pending.position())
                    || pending.level().getBlockState(pending.position()).equals(pending.before()))
                continue;
            for (String faction : pending.factions())
                FactionPassage.violate(event.getServer(), pending.owner(), faction);
        }
    }

    public static void install() {
        if (installed) return;
        installed = true;
        MinecraftForge.EVENT_BUS.register(EconomyEvents.class);
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(EconomyTables.INSTANCE);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p)
            try {
                FactionEconomyLedger.recover(p);
            } catch (RuntimeException ignored) {
            }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) FactionPassage.clear(p);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) FactionPassage.clear(p);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer p) FactionPassage.clear(p);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer p
                && p.tickCount % 20 == 0) FactionPassage.reconcile(p);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void nonHumanAttack(
            net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof Human || event.getEntity().level().isClientSide) return;
        UUID owner = StandingAccess.responsible(event.getSource().getEntity());
        if (owner != null && FactionPassage.underFlag(event.getEntity().getServer(), owner))
            FactionPassage.violate(event.getEntity().getServer(), owner, null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer p)
                || !event.getLevel()
                        .getBlockState(event.getPos())
                        .is(FactionContent.TRIBUTE_CHEST.get())) return;
        if ((p.getMainHandItem().getItem()
                        instanceof dev.felix.hostilehumans.settlement.SettlementFacilityBindingTool
                || p.getMainHandItem().getItem() instanceof dev.felix.hostilehumans.settlement.SettlementSelectionTool)
                && dev.felix.hostilehumans.settlement.SettlementAccess.author(p)) return;
        EconomyNetwork.openFacility(p, event.getPos(), EconomyView.Page.SUPPLY);
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)
                || p.isSpectator()
                || event.getResult() == Event.Result.DENY) return;
        var entity = event.getItem();
        if (entity.hasPickUpDelay()
                || entity.isRemoved()
                || !(entity.getItem().getItem() instanceof CurrencyItem coin)) return;
        var saved = entity.saveWithoutId(new CompoundTag());
        if (saved.hasUUID("Owner") && !saved.getUUID("Owner").equals(p.getUUID())) return;
        try {
            int reconciled = FactionEconomyLedger.external(entity);
            if (reconciled != 0) {
                event.setCanceled(true);
                return;
            }
            WalletInventory.initializeEmptyWallets(p);
            var inventory = new InventoryTransaction(p);
            int count =
                    inventory.absorb(
                            coin.currency(), coin.denomination(), entity.getItem().getCount());
            if (count == 0) return;
            UUID transaction = UUID.randomUUID();
            inventory.pickup(entity, transaction, count);
            var draft = FactionEconomyLedger.draft(p);
            var receipt = draft.commit(p, transaction, inventory, false);
            if (receipt.changed()) {
                event.setCanceled(true);
                p.take(entity, count);
                if (entity.getItem().isEmpty()) entity.discard();
            }
        } catch (RuntimeException blocked) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void projectile(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk()) return;
        if (event.getEntity() instanceof Projectile projectile)
            FactionPassage.captureProjectile(
                    projectile, StandingAccess.responsible(projectile.getOwner()));
        if (event.getEntity() instanceof Human human
                && NATURAL.remove(human)
                && !HumanFactions.id(human).isEmpty()
                && event.getLevel() instanceof net.minecraft.server.level.ServerLevel server
                && BrokerIndex.get(server)
                        .excludesSpawn(server, human.getX(), human.getY(), human.getZ()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void natural(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Human human
                && (event.getSpawnType() == net.minecraft.world.entity.MobSpawnType.NATURAL
                        || event.getSpawnType()
                                == net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION))
            NATURAL.add(human);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("hhu")
                                .then(
                                        Commands.literal("economy")
                                                .then(
                                                        Commands.literal("test")
                                                                .requires(
                                                                        source ->
                                                                                source.getEntity()
                                                                                                instanceof
                                                                                                ServerPlayer
                                                                                                                p
                                                                                        && p
                                                                                                .isCreative())
                                                                .then(
                                                                        Commands.argument(
                                                                                        "faction",
                                                                                        StringArgumentType
                                                                                                .word())
                                                                                .suggests(
                                                                                        (context,
                                                                                                builder) -> {
                                                                                            for (Currency
                                                                                                    c :
                                                                                                            Currency
                                                                                                                    .values())
                                                                                                builder
                                                                                                        .suggest(
                                                                                                                c
                                                                                                                        .key());
                                                                                            return builder
                                                                                                    .buildFuture();
                                                                                        })
                                                                                .executes(
                                                                                        context -> {
                                                                                            var
                                                                                                    player =
                                                                                                            context.getSource()
                                                                                                                    .getPlayerOrException();
                                                                                            var
                                                                                                    currency =
                                                                                                            Currency
                                                                                                                    .faction(
                                                                                                                            StringArgumentType
                                                                                                                                    .getString(
                                                                                                                                            context,
                                                                                                                                            "faction"))
                                                                                                                    .orElseThrow(
                                                                                                                            () ->
                                                                                                                                    new IllegalArgumentException(
                                                                                                                                            "unknown"
                                                                                                                                                + " currency"));
                                                                                            var
                                                                                                    inventory =
                                                                                                            new InventoryTransaction(
                                                                                                                    player);
                                                                                            if (inventory
                                                                                                    .wallets()
                                                                                                    .isEmpty())
                                                                                                inventory
                                                                                                        .insert(
                                                                                                                new ItemStack(
                                                                                                                        EconomyContent
                                                                                                                                .WALLET
                                                                                                                                .get()));
                                                                                            inventory
                                                                                                    .insert(
                                                                                                            new ItemStack(
                                                                                                                    EconomyContent
                                                                                                                            .coin(
                                                                                                                                    currency,
                                                                                                                                    Currency
                                                                                                                                            .Denomination
                                                                                                                                            .GOLD)));
                                                                                            FactionEconomyLedger
                                                                                                    .draft(
                                                                                                            player)
                                                                                                    .commit(
                                                                                                            player,
                                                                                                            UUID
                                                                                                                    .randomUUID(),
                                                                                                            inventory,
                                                                                                            false);
                                                                                            EconomyNetwork
                                                                                                    .openTest(
                                                                                                            player,
                                                                                                            currency);
                                                                                            return 1;
                                                                                        })))));
    }
}
