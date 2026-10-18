package club.someoneice.humangunner;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** One deterministic band selection shared by political effects, tolerance and server rendering snapshots. */
final class IdentityBands {
    record Selection(String faction, int grade, int source, String slot) {
        static final Selection NONE = new Selection("", 0, 3, "");
    }
    private record Cached(int tick, int revision, Selection selection) {}
    private static final Map<Player, Cached> CACHE = new WeakHashMap<>();
    private IdentityBands() {}
    static Selection selected(Player player) {
        if (!UnifiedConfig.get().featureEnabled("identity_band", true)) return Selection.NONE;
        int tick = player.tickCount, revision = player.getInventory().getTimesChanged();
        Cached cached = CACHE.get(player);
        if (cached != null && cached.tick() == tick && cached.revision() == revision) return cached.selection();
        Selection result = Selection.NONE;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            result = choose(result, player.getInventory().getItem(i), 2, String.format(Locale.ROOT, "%05d", i));
        if (ModList.get().isLoaded("curios")) result = CuriosBandAccess.selected(player, result);
        CACHE.put(player, new Cached(tick, revision, result));
        return result;
    }
    static Selection choose(Selection current, ItemStack stack, int source, String slot) {
        if (!(stack.getItem() instanceof IdentityBandItem band) || stack.isEmpty()) return current;
        Selection next = new Selection(band.faction(), band.grade(), source, slot);
        if (next.grade() > current.grade() || (next.grade() == current.grade()
                && (next.source() < current.source() || (next.source() == current.source() && next.slot().compareTo(current.slot()) < 0))))
            return next;
        return current;
    }
}
