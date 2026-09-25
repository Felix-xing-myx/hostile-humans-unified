package club.someoneice.humangunner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Player/loot insertion policy; spawn weighting remains a separate concern. */
public final class HumanGunAcceptance {
    private HumanGunAcceptance() {}

    public static boolean accepts(ItemStack stack) {
        GunSupport support = GunSupport.get();
        if (!support.isGun(stack)) return true;
        return acceptsGunId(HumanGunnerConfig.get().gunWhitelist(), support.gunId(stack));
    }

    /**
     * Owner inventory input uses the wild whitelist as its base and allows the
     * hired-only list to add further guns. An empty base whitelist deliberately
     * retains the legacy "all guns" meaning; an empty additional list adds none.
     */
    public static boolean acceptsOwnerInput(ItemStack stack) {
        GunSupport support = GunSupport.get();
        if (!support.isGun(stack)) return true;
        HumanGunnerConfig config = HumanGunnerConfig.get();
        return acceptsHiredGunId(
                config.gunWhitelist(), config.hiredGunAdditionalWhitelist(), support.gunId(stack)
        );
    }

    static boolean acceptsGunId(Map<ResourceLocation, Integer> whitelist, ResourceLocation gunId) {
        if (whitelist.isEmpty()) return gunId != null;
        return gunId != null && whitelist.getOrDefault(gunId, 0) > 0;
    }

    static boolean acceptsHiredGunId(
            Map<ResourceLocation, Integer> wildWhitelist,
            Map<ResourceLocation, Integer> hiredAdditionalWhitelist,
            ResourceLocation gunId
    ) {
        if (gunId == null) return false;
        if (wildWhitelist.isEmpty()) return true;
        return wildWhitelist.getOrDefault(gunId, 0) > 0
                || hiredAdditionalWhitelist.getOrDefault(gunId, 0) > 0;
    }
}
