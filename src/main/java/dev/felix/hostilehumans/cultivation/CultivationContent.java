package dev.felix.hostilehumans.cultivation;

import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/**
 * Fixed content registry. Materials deliberately have no recipes, drops or economic acquisition.
 */
public final class CultivationContent {
    private static final String MOD = "hostile_humans_unified";
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD);
    public static final RegistryObject<Block> TRAINING_BLOCK =
            BLOCKS.register("training_center", CultivationTrainingBlock::new);
    public static final RegistryObject<Item> TRAINING_ITEM =
            ITEMS.register(
                    "training_center",
                    () -> new BlockItem(TRAINING_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SUPPLIES =
            ITEMS.register("training_supplies", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> POTENTIAL =
            ITEMS.register(
                    "growth_potential_token",
                    () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> EXPANSION =
            ITEMS.register(
                    "primary_expansion_token",
                    () -> new Item(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<BlockEntityType<CultivationTrainingBlockEntity>>
            TRAINING_ENTITY =
                    ENTITIES.register(
                            "training_center",
                            () ->
                                    BlockEntityType.Builder.of(
                                                    CultivationTrainingBlockEntity::new,
                                                    TRAINING_BLOCK.get())
                                            .build(null));

    private CultivationContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        CultivationRuntime.register();
    }

    public static java.util.List<ItemStack> creativeItems() {
        return java.util.List.of(
                new ItemStack(TRAINING_ITEM.get()),
                new ItemStack(SUPPLIES.get()),
                new ItemStack(POTENTIAL.get()),
                new ItemStack(EXPANSION.get()));
    }
}
