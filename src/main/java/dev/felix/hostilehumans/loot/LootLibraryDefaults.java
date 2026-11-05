package dev.felix.hostilehumans.loot;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Official files are a readable/exportable mirror, never an override of packaged defaults. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class LootLibraryDefaults {
    private LootLibraryDefaults() {}
    @SubscribeEvent public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) LootLibrary::mirrorOfficial);
    }
}
