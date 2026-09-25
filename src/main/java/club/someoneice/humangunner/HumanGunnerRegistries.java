package club.someoneice.humangunner;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HumanGunnerRegistries {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HumanGunner.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, HumanGunner.MOD_ID);

    public static final RegistryObject<MenuType<HumanInventoryMenu>> HUMAN_INVENTORY_MENU = MENUS.register(
            "human_inventory",
            () -> IForgeMenuType.create(HumanInventoryMenu::fromNetwork)
    );

    public static final RegistryObject<net.minecraft.world.entity.EntityType<TierThreeHuman>> TIER_THREE_HUMAN =
            com.craftix.hostile_humans.entity.entities.ModEntityType.HUMAN3;
    public static final RegistryObject<Item> TIER_THREE_HUMAN_SPAWN_EGG =
            com.craftix.hostile_humans.item.ModItems.HUMAN3_SPAWN_EGG;

    public static final RegistryObject<Item> ROAMER_IDENTITY_BADGE = ITEMS.register(
            "roamer_identity_badge",
            () -> new IdentityBadgeItem(0, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final RegistryObject<Item> TIER_ONE_IDENTITY_BADGE = ITEMS.register(
            "tier_one_identity_badge",
            () -> new IdentityBadgeItem(1, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );
    public static final RegistryObject<Item> TIER_TWO_IDENTITY_BADGE = ITEMS.register(
            "tier_two_identity_badge",
            () -> new IdentityBadgeItem(2, new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );
    public static final RegistryObject<Item> TIER_THREE_IDENTITY_BADGE = ITEMS.register(
            "tier_three_identity_badge",
            () -> new IdentityBadgeItem(3, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    public static final RegistryObject<Item> ULTIMATE_IDENTITY_BADGE = ITEMS.register(
            "ultimate_identity_badge",
            () -> new IdentityBadgeItem(4, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())
    );
    public static final RegistryObject<Item> SOLDIER_ROSTER = ITEMS.register(
            "soldier_roster",
            () -> new SoldierRosterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final RegistryObject<Item> ROAMER_CONTRACT = contract("roamer_contract", 0);
    public static final RegistryObject<Item> TIER_ONE_CONTRACT = contract("tier_one_contract", 1);
    public static final RegistryObject<Item> TIER_TWO_CONTRACT = contract("tier_two_contract", 2);
    public static final RegistryObject<Item> TIER_THREE_CONTRACT = contract("tier_three_contract", 3);

    public static final RegistryObject<Item> ROAMER_SIGNAL_FLARE = flare("roamer_signal_flare", 0, false);
    public static final RegistryObject<Item> TIER_ONE_SIGNAL_FLARE = flare("tier_one_signal_flare", 1, false);
    public static final RegistryObject<Item> TIER_TWO_SIGNAL_FLARE = flare("tier_two_signal_flare", 2, false);
    public static final RegistryObject<Item> TIER_THREE_SIGNAL_FLARE = flare("tier_three_signal_flare", 3, false);
    public static final RegistryObject<Item> ROAMER_HOSTILE_BEACON = flare("roamer_hostile_beacon", 0, true);
    public static final RegistryObject<Item> TIER_ONE_HOSTILE_BEACON = flare("tier_one_hostile_beacon", 1, true);
    public static final RegistryObject<Item> TIER_TWO_HOSTILE_BEACON = flare("tier_two_hostile_beacon", 2, true);
    public static final RegistryObject<Item> TIER_THREE_HOSTILE_BEACON = flare("tier_three_hostile_beacon", 3, true);

    private static RegistryObject<Item> contract(String name, int tier) {
        return ITEMS.register(name, () -> new RecruitmentContractItem(tier,
                new Item.Properties().stacksTo(16).rarity(tier >= 2 ? Rarity.RARE : Rarity.UNCOMMON)));
    }

    private static RegistryObject<Item> flare(String name, int tier, boolean hostile) {
        return ITEMS.register(name, () -> new HumanSignalItem(tier, hostile,
                new Item.Properties().stacksTo(16).rarity(tier >= 2 ? Rarity.RARE : Rarity.UNCOMMON)));
    }

    private HumanGunnerRegistries() {
    }
}
