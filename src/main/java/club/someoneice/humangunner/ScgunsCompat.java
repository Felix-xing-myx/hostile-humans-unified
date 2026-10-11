package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Optional-module gate. Core code never links Scorched Guns types. */
final class ScgunsCompat {
    private ScgunsCompat() {}
    static boolean enabled() { return ModList.get().isLoaded("scguns") && UnifiedConfig.get().scguns().enabled(); }
    static boolean throwItem(ItemStack stack) { return enabled() && ScgunsThrowables.supported(stack); }
    static void tick(Human human) { if (enabled()) ScgunsThrowables.tick(human); }
    static boolean launchFlare(ServerPlayer player) { return enabled() && ScgunsRaidBridge.launch(player); }
}
