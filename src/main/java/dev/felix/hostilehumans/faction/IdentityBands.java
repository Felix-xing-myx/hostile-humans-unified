package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.faction.visual.CuriosBandAccess;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** One deterministic band selection shared by political effects, tolerance and server rendering snapshots. */
public final class IdentityBands {
    public record Selection(String faction, int grade, int source, String slot) {
        static final Selection NONE = new Selection("", 0, 3, "");
    }
    private record Cached(int tick, int revision, Selection selection) {}
    private static final Map<Player, Cached> CACHE = new WeakHashMap<>();
    private IdentityBands() {}
    public static Selection selected(Player player) {
        if (player.level().isClientSide || !UnifiedConfig.get().featureEnabled("identity_band", true)) return Selection.NONE;
        int tick = player.tickCount, revision = player.getInventory().getTimesChanged();
        Cached cached = CACHE.get(player);
        if (cached != null && cached.tick() == tick && cached.revision() == revision) return cached.selection();
        Selection result = Selection.NONE;
        if (ModList.get().isLoaded("curios")) result = CuriosBandAccess.selected(player, Selection.NONE);
        if (result.grade() == 0) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++)
                result = choose(result, player.getInventory().getItem(i), 2, String.format(Locale.ROOT, "%05d", i));
        }
        CACHE.put(player, new Cached(tick, revision, result));
        return result;
    }
    public static Selection choose(Selection current, ItemStack stack, int source, String slot) {
        if (!(stack.getItem() instanceof IdentityBandItem band) || stack.isEmpty()) return current;
        Selection next = new Selection(band.faction(), band.grade(), source, slot);
        return prefers(next,current) ? next : current;
    }
    static boolean prefers(Selection next,Selection current) {
        int nextGroup=next.source()<2?0:next.source(),currentGroup=current.source()<2?0:current.source();
        return nextGroup<currentGroup||nextGroup==currentGroup&&(next.grade()>current.grade()||next.grade()==current.grade()
                &&(next.source()<current.source()||next.source()==current.source()&&next.slot().compareTo(current.slot())<0));
    }
}
