package com.craftix.hostile_humans.item;

import club.someoneice.humangunner.HumanGunnerRegistries;
import com.craftix.hostile_humans.HostileHumans;
import java.util.ArrayList;
import java.util.Comparator;
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
                        var items = new ArrayList<RegistryObject<Item>>();
                        items.addAll(ModItems.ITEMS.getEntries());
                        items.addAll(HumanGunnerRegistries.ITEMS.getEntries());
                        items.sort(Comparator.comparing(item -> item.getId().toString()));
                        for (RegistryObject<Item> item : items) {
                            output.accept(item.get());
                        }
                    })
                    .build());

    private UnifiedCreativeTab() {
    }
}
