package dev.felix.hostilehumans.bootstrap;

import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmoItem;
import dev.felix.hostilehumans.compat.scguns.ScgunsSignalItem;
import dev.felix.hostilehumans.entity.TierThreeHuman;
import dev.felix.hostilehumans.faction.IdentityBadgeItem;
import dev.felix.hostilehumans.faction.IdentityBandItem;
import dev.felix.hostilehumans.monitor.HumanMonitorItem;
import dev.felix.hostilehumans.observer.AttributeObserverItem;
import dev.felix.hostilehumans.observer.NavigationObserverHelmet;
import dev.felix.hostilehumans.observer.NavigationTestItem;
import dev.felix.hostilehumans.soldier.HumanSignalItem;
import dev.felix.hostilehumans.soldier.NaturalStateToolItem;
import dev.felix.hostilehumans.soldier.RecruitmentContractItem;
import dev.felix.hostilehumans.soldier.SoldierRecallCharmItem;
import dev.felix.hostilehumans.soldier.SoldierRosterItem;
import dev.felix.hostilehumans.soldier.SquadCommandTokenItem;

import dev.felix.hostilehumans.faction.FactionContent;
import dev.felix.hostilehumans.inventory.HumanInventoryMenu;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ContentRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HumanRuntime.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, HumanRuntime.MOD_ID);
    public static final java.util.List<RegistryObject<Item>> FACTION_SPAWN_EGGS = FactionContent.registerOrdinaryEggs(ITEMS);
    public static final java.util.List<RegistryObject<Item>> BOSS_SPAWN_EGGS = FactionContent.registerBossEggs(ITEMS);
    public static final RegistryObject<Item> TRIBUTE_CHEST = ITEMS.register("tribute_chest",
            () -> new net.minecraft.world.item.BlockItem(FactionContent.TRIBUTE_CHEST.get(),new Item.Properties()) {
                @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.level.Level level,
                        java.util.List<net.minecraft.network.chat.Component> lines,net.minecraft.world.item.TooltipFlag flags) {
                    super.appendHoverText(stack,level,lines,flags);
                    lines.add(net.minecraft.network.chat.Component.translatable("block.hostile_humans_unified.tribute_chest.tooltip"));
                }
            });

    public static final RegistryObject<Item> HUMAN_ARROW = ITEMS.register("human_arrow",
            () -> new HumanAmmoItem(false, new Item.Properties().stacksTo(64)));
    /** Four factions by four grades; these have no recipe or gameplay acquisition source. */
    public static final java.util.List<RegistryObject<Item>> IDENTITY_BANDS = registerBands();
    private static java.util.List<RegistryObject<Item>> registerBands() {
        java.util.List<RegistryObject<Item>> result = new java.util.ArrayList<>();
        for (String faction : java.util.List.of("crimson", "azure", "verdant", "ashen")) {
            for (int grade = 1; grade <= 4; grade++) {
                final int bandGrade = grade;
                result.add(ITEMS.register("faction_" + faction + "_band_" + grade,
                        () -> new IdentityBandItem(faction, bandGrade, new Item.Properties()
                                .rarity(bandGrade == 4 ? Rarity.EPIC : bandGrade == 3 ? Rarity.RARE : Rarity.UNCOMMON))));
            }
        }
        return java.util.List.copyOf(result);
    }
    public static final RegistryObject<Item> SOLDIER_RECALL_CHARM = ITEMS.register("soldier_recall_charm",
            () -> new SoldierRecallCharmItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HUMAN_MONITOR = ITEMS.register("human_monitor",
            () -> new HumanMonitorItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> NAVIGATION_TEST_POINTER = ITEMS.register("navigation_test_pointer",
            () -> new NavigationTestItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SQUAD_COMMAND_TOKEN = ITEMS.register("squad_command_token",
            () -> new SquadCommandTokenItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> PATROL_COMMAND_BATON = ITEMS.register("patrol_command_baton",
            () -> new dev.felix.hostilehumans.soldier.PatrolCommandBatonItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CREATIVE_SOLDIER_CONTROLLER = ITEMS.register("creative_soldier_controller",
            () -> new dev.felix.hostilehumans.soldier.CreativeSoldierControllerItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> NATURAL_STATE_TOOL = ITEMS.register("natural_state_tool",
            () -> new NaturalStateToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ATTRIBUTE_OBSERVER = ITEMS.register("attribute_observer",
            () -> new AttributeObserverItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> NAVIGATION_OBSERVER_HELMET = ITEMS.register("navigation_observer_helmet",
            () -> new NavigationObserverHelmet(new Item.Properties().rarity(Rarity.EPIC)));
    // Keep the registry ID stable even when TaCZ is absent, so saved inventories remain readable.
    public static final RegistryObject<Item> HUMAN_MAGAZINE = ITEMS.register("human_magazine",
            () -> new HumanAmmoItem(true, new Item.Properties().stacksTo(24)));

    public static final RegistryObject<Item> SCGUNS_SIGNAL_FLARE = ITEMS.register("scguns_signal_flare",
            () -> new ScgunsSignalItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final RegistryObject<MenuType<HumanInventoryMenu>> HUMAN_INVENTORY_MENU = MENUS.register(
            "human_inventory",
            () -> IForgeMenuType.create(HumanInventoryMenu::fromNetwork)
    );

    public static final RegistryObject<net.minecraft.world.entity.EntityType<TierThreeHuman>> TIER_THREE_HUMAN =
            dev.felix.hostilehumans.bootstrap.ModEntityType.HUMAN3;
    public static final RegistryObject<Item> TIER_THREE_HUMAN_SPAWN_EGG =
            dev.felix.hostilehumans.bootstrap.ModItems.HUMAN3_SPAWN_EGG;

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

    private ContentRegistry() {
    }
}
