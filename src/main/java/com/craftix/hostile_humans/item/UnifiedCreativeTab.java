package com.craftix.hostile_humans.item;

import club.someoneice.humangunner.HumanGunnerRegistries;
import com.craftix.hostile_humans.HostileHumans;
import java.util.LinkedHashSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** One creative tab for both retained mod IDs in the unified JAR. */
public final class UnifiedCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HostileHumans.MOD_ID);

    public static final RegistryObject<CreativeModeTab> UNIFIED = TABS.register("unified", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hostile_humans"))
                    .icon(() -> new ItemStack(HumanGunnerRegistries.TIER_ONE_IDENTITY_BADGE.get()))
                    .displayItems((parameters, output) -> {
                        var items = new LinkedHashSet<RegistryObject<Item>>();
                        // Keep related items together, in tier order from roamer to tier III.
                        items.add(ModItems.ROAMER_SPAWN_EGG);
                        items.add(ModItems.HUMAN1_SPAWN_EGG);
                        items.add(ModItems.HUMAN2_SPAWN_EGG);
                        items.add(ModItems.HUMAN3_SPAWN_EGG);

                        items.add(HumanGunnerRegistries.ROAMER_IDENTITY_BADGE);
                        items.add(HumanGunnerRegistries.TIER_ONE_IDENTITY_BADGE);
                        items.add(HumanGunnerRegistries.TIER_TWO_IDENTITY_BADGE);
                        items.add(HumanGunnerRegistries.TIER_THREE_IDENTITY_BADGE);
                        items.add(HumanGunnerRegistries.ULTIMATE_IDENTITY_BADGE);

                        items.add(HumanGunnerRegistries.ROAMER_CONTRACT);
                        items.add(HumanGunnerRegistries.TIER_ONE_CONTRACT);
                        items.add(HumanGunnerRegistries.TIER_TWO_CONTRACT);
                        items.add(HumanGunnerRegistries.TIER_THREE_CONTRACT);

                        items.add(HumanGunnerRegistries.ROAMER_SIGNAL_FLARE);
                        items.add(HumanGunnerRegistries.TIER_ONE_SIGNAL_FLARE);
                        items.add(HumanGunnerRegistries.TIER_TWO_SIGNAL_FLARE);
                        items.add(HumanGunnerRegistries.TIER_THREE_SIGNAL_FLARE);

                        items.add(HumanGunnerRegistries.ROAMER_HOSTILE_BEACON);
                        items.add(HumanGunnerRegistries.TIER_ONE_HOSTILE_BEACON);
                        items.add(HumanGunnerRegistries.TIER_TWO_HOSTILE_BEACON);
                        items.add(HumanGunnerRegistries.TIER_THREE_HOSTILE_BEACON);

                        items.add(HumanGunnerRegistries.SOLDIER_ROSTER);
                        // Preserve discoverability if more items are registered later.
                        items.addAll(ModItems.ITEMS.getEntries());
                        items.addAll(HumanGunnerRegistries.ITEMS.getEntries());
                        for (RegistryObject<Item> item : items) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private UnifiedCreativeTab() {
    }
}
