package club.someoneice.humangunner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Birth whitelists never restrict equipment input. Global backend bans always win. */
public final class HumanGunAcceptance {
    private HumanGunAcceptance() {}

    public static boolean accepts(ItemStack stack) {
        if (!GunSupport.isGunItem(stack)) return true;
        GunSupport support = GunSupport.get();
        return UnifiedConfig.get().gunSpawnPools().allows(support.backendId(stack), support.gunId(stack));
    }

    public static boolean acceptsOwnerInput(ItemStack stack) { return accepts(stack); }

    static boolean acceptsGunId(GunSpawnPools pools, String backend, ResourceLocation id) {
        return pools.allows(backend, id);
    }
}
