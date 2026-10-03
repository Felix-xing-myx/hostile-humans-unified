package club.someoneice.humangunner;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Connection-scoped presentation cache. Never writes server settings to local config files. */
@Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, value = Dist.CLIENT)
public final class RecruitmentConfigClient {
    private static final RecruitmentTermsCache CACHE = new RecruitmentTermsCache();

    private RecruitmentConfigClient() {}

    static void accept(Connection source, RecruitmentConfigNetwork.Snapshot snapshot) {
        var current = Minecraft.getInstance().getConnection();
        CACHE.accept(source, current == null ? null : current.getConnection(), snapshot);
    }

    static RecruitmentConfigNetwork.Price price(int tier) {
        var current = Minecraft.getInstance().getConnection();
        if (current == null) return RecruitmentConfigNetwork.snapshot(UnifiedConfig.get()).tier(tier);
        // Until the server sync arrives, do not display the local client's cost.
        return CACHE.price(current.getConnection(), tier);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CACHE.clear();
    }
}
