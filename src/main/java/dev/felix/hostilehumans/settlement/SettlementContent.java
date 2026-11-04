package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owned registers; root calls register(bus). Does not replace the existing tribute_chest registry
 * ID.
 */
public final class SettlementContent {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HumanRuntime.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HumanRuntime.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HumanRuntime.MOD_ID);
    public static final RegistryObject<Block> INFORMATION =
            BLOCKS.register(
                    "settlement_information",
                    () ->
                            new SettlementInformationBlock(
                                    BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                                            .noOcclusion()
                                            .strength(-1, 3600000)));
    public static final RegistryObject<BlockEntityType<SettlementInformationBlockEntity>>
            INFORMATION_ENTITY =
                    BLOCK_ENTITIES.register(
                            "settlement_information",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    SettlementInformationBlockEntity::new,
                                                    INFORMATION.get())
                                            .build(null));
    public static final RegistryObject<Item> INFORMATION_ITEM =
            ITEMS.register(
                    "settlement_information",
                    () -> new BlockItem(INFORMATION.get(), new Item.Properties()));
    public static final RegistryObject<Item> PLACEMENT_TOOL =
            ITEMS.register("settlement_selection_tool",
                    () -> new SettlementPlacementTool(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SELECTION_TOOL =
            ITEMS.register(
                    "settlement_region_tool",
                    () -> new SettlementSelectionTool(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FACILITY_TOOL = ITEMS.register("settlement_facility_tool",
            () -> new SettlementFacilityBindingTool(new Item.Properties().stacksTo(1)));
    public static final Map<String, RegistryObject<SettlementControlBlock>> CONTROLS = controls();

    private static Map<String, RegistryObject<SettlementControlBlock>> controls() {
        var values = new LinkedHashMap<String, RegistryObject<SettlementControlBlock>>();
        for (String faction : java.util.List.of("crimson", "azure", "verdant", "ashen")) {
            var block =
                    BLOCKS.register(
                            "settlement_control_" + faction,
                            () ->
                                    new SettlementControlBlock(
                                            faction,
                                            BlockBehaviour.Properties.copy(Blocks.STONE)
                                                    .strength(4)));
            values.put(faction, block);
            ITEMS.register(
                    "settlement_control_" + faction,
                    () -> new BlockItem(block.get(), new Item.Properties()));
        }
        return java.util.Collections.unmodifiableMap(values);
    }

    private SettlementContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ModLoadingContext.get()
                .registerConfig(
                        ModConfig.Type.COMMON,
                        SettlementLimits.SPEC,
                        "hostile_humans_unified-settlements.toml");
        SettlementNetwork.register();
    }
}
