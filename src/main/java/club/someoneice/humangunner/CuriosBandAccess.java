package club.someoneice.humangunner;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.*;

/** Optional API adapter, loaded only after the Curios availability check. Cosmetic slots never confer identity. */
final class CuriosBandAccess {
    private CuriosBandAccess() {}
    static IdentityBands.Selection selected(Player player, IdentityBands.Selection baseline) {
        final IdentityBands.Selection[] result = {baseline};
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            inventory.getCurios().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var stacks = entry.getValue().getStacks();
                int priority = entry.getKey().equals("identity_band") ? 0 : 1;
                for (int i = 0; i < stacks.getSlots(); i++)
                    result[0] = IdentityBands.choose(result[0], stacks.getStackInSlot(i), priority,
                            entry.getKey() + ":" + String.format(Locale.ROOT, "%05d", i));
            });
        });
        return result[0];
    }
}
