package dev.felix.hostilehumans.economy;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

import java.util.*;

public final class EconomyContent {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HumanRuntime.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HumanRuntime.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HumanRuntime.MOD_ID);
    private static final Map<Currency, Map<Currency.Denomination, RegistryObject<Item>>> COINS =
            new EnumMap<>(Currency.class);
    public static final RegistryObject<Item> WALLET =
            ITEMS.register("faction_wallet", () -> new WalletItem(new Item.Properties()));
    public static final RegistryObject<Item> FLAG =
            ITEMS.register(
                    "parley_flag", () -> new ParleyFlagItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Block> TRADER =
            BLOCKS.register(
                    "faction_trader",
                    () ->
                            new EconomyTraderBlock(
                                    BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                                            .strength(2.5F)));
    public static final RegistryObject<Block> TASK_BOARD =
            BLOCKS.register(
                    "faction_task_board",
                    () ->
                            new EconomyTaskBoardBlock(
                                    BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                                            .strength(2.5F)));
    public static final Map<Currency, RegistryObject<Block>> BROKERS =
            new EnumMap<>(Currency.class);
    public static final RegistryObject<BlockEntityType<EconomyBrokerEntity>> BROKER_ENTITY;

    static {
        for (Currency currency : Currency.values()) {
            var faces =
                    new EnumMap<Currency.Denomination, RegistryObject<Item>>(
                            Currency.Denomination.class);
            for (var face : Currency.Denomination.values())
                faces.put(
                        face,
                        ITEMS.register(
                                "coin_"
                                        + currency.key()
                                        + "_"
                                        + face.name().toLowerCase(Locale.ROOT),
                                () -> new CurrencyItem(currency, face, new Item.Properties())));
            COINS.put(currency, faces);
            RegistryObject<Block> block =
                    BLOCKS.register(
                            "faction_broker_" + currency.key(),
                            () ->
                                    new EconomyBrokerBlock(
                                            currency,
                                            BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                                                    .strength(2.5F)));
            BROKERS.put(currency, block);
            ITEMS.register(
                    "faction_broker_" + currency.key(),
                    () -> new BlockItem(block.get(), new Item.Properties()));
        }
        ITEMS.register("faction_trader", () -> new BlockItem(TRADER.get(), new Item.Properties()));
        ITEMS.register(
                "faction_task_board", () -> new BlockItem(TASK_BOARD.get(), new Item.Properties()));
        BROKER_ENTITY =
                ENTITIES.register(
                        "faction_broker",
                        () ->
                                BlockEntityType.Builder.of(
                                                EconomyBrokerEntity::new,
                                                BROKERS.values().stream()
                                                        .map(RegistryObject::get)
                                                        .toArray(Block[]::new))
                                        .build(null));
    }

    private EconomyContent() {}

    public static Item coin(Currency currency, Currency.Denomination face) {
        return COINS.get(currency).get(face).get();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        ENTITIES.register(bus);
    }

    public static void commonSetup() {
        EconomyNetwork.backend(new EconomyService());
        EconomyNetwork.register();
        EconomyEvents.install();
        dev.felix.hostilehumans.settlement.SettlementAccess.permits(
                dev.felix.hostilehumans.faction.FactionPassage::permitted);
        dev.felix.hostilehumans.settlement.SettlementAccess.presence(
                dev.felix.hostilehumans.settlement.SettlementFacility.Type.TRADER,
                (level, pos) ->
                        level.getBlockState(pos).is(TRADER.get())
                                ? dev.felix.hostilehumans.settlement.SettlementAccess.Presence
                                        .PRESENT
                                : dev.felix.hostilehumans.settlement.SettlementAccess.Presence
                                        .MISSING);
    }
}
