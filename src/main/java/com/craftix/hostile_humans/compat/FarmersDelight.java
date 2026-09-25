package com.craftix.hostile_humans.compat;

import com.craftix.hostile_humans.HumanUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public final class FarmersDelight {
    private static final List<String> FOODS = List.of(
        "fried_egg",
        "apple_cider",
        "melon_juice",
        "pumpkin_slice",
        "cabbage_leaf",
        "minced_beef",
        "beef_patty",
        "cooked_chicken_cuts",
        "cooked_bacon",
        "cooked_cod_slice",
        "cooked_salmon_slice",
        "cooked_mutton_chops",
        "ham",
        "smoked_ham",
        "pie_crust",
        "apple_pie",
        "sweet_berry_cheesecake",
        "chocolate_pie",
        "cake_slice",
        "apple_pie_slice",
        "sweet_berry_cheesecake_slice",
        "chocolate_pie_slice",
        "sweet_berry_cookie",
        "honey_cookie",
        "melon_popsicle",
        "glow_berry_custard",
        "fruit_salad",
        "mixed_salad",
        "nether_salad",
        "barbecue_stick",
        "egg_sandwich",
        "chicken_sandwich",
        "hamburger",
        "bacon_sandwich",
        "mutton_wrap",
        "dumplings",
        "stuffed_potato",
        "cabbage_rolls",
        "salmon_roll",
        "cod_roll",
        "kelp_roll",
        "kelp_roll_slice",
        "cooked_rice",
        "bone_broth",
        "beef_stew",
        "chicken_soup",
        "vegetable_soup",
        "fish_stew",
        "fried_rice",
        "pumpkin_soup",
        "baked_cod_stew",
        "noodle_soup",
        "bacon_and_eggs",
        "pasta_with_meatballs",
        "pasta_with_mutton_chop",
        "mushroom_rice",
        "roasted_mutton_chops",
        "vegetable_noodles",
        "steak_and_potatoes",
        "ratatouille",
        "squid_ink_pasta",
        "grilled_salmon",
        "roast_chicken",
        "stuffed_pumpkin",
        "honey_glazed_ham",
        "shepherds_pie");
    public static void addFoodItems() {
        List<ItemStack> result = new ArrayList<>(List.of(HumanUtil.EDIBLE_ITEMS));
        for (String id : FOODS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("farmersdelight", id));
            if (item != null && item != Items.AIR && result.stream().noneMatch(s -> s.is(item))) {
                result.add(item.getDefaultInstance());
            }
        }
        HumanUtil.EDIBLE_ITEMS = result.toArray(ItemStack[]::new);
    }
}

