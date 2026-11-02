package dev.felix.hostilehumans.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** One shared limit for the three vanilla potion containers, regardless of their potion NBT. */
public final class VanillaPotionStacks {
    public static final int MAX_SIZE = 4;
    private VanillaPotionStacks() { }

    public static boolean applies(Item item) {
        return item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }
}
