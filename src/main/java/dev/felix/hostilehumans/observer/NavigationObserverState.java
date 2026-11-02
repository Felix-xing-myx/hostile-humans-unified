package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.observer.NavigationObserverSelection;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.*;

/** Player-owned, dimension-scoped observation bookmarks; intentionally contains no test orders. */
final class NavigationObserverState {
    static final String KEY = "HostileHumansUnifiedNavigationObserver";
    private NavigationObserverState() {}

    static NavigationObserverSelection<UUID> load(CompoundTag storage, String dimension) {
        var selection = new NavigationObserverSelection<UUID>(NavigationDebugFrame.MAX_ENTITIES);
        CompoundTag saved = storage.getCompound(dimension);
        if (saved.getInt("version") != 1) return selection;
        ListTag members = saved.getList("members", Tag.TAG_COMPOUND);
        var entries = new ArrayList<NavigationObserverSelection.Entry<UUID>>();
        for (int i = 0; i < Math.min(members.size(), NavigationDebugFrame.MAX_ENTITIES); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("id") && member.contains("slot", Tag.TAG_INT))
                entries.add(new NavigationObserverSelection.Entry<>(member.getUUID("id"), member.getInt("slot")));
        }
        selection.restore(entries, saved.hasUUID("focus") ? saved.getUUID("focus") : null);
        return selection;
    }
    static void save(CompoundTag storage, String dimension, NavigationObserverSelection<UUID> selection) {
        if (selection.isEmpty()) { storage.remove(dimension); return; }
        var saved = new CompoundTag();
        saved.putInt("version", 1);
        var members = new ListTag();
        for (var entry : selection.entries()) {
            var member = new CompoundTag();
            member.putUUID("id", entry.key()); member.putInt("slot", entry.slot()); members.add(member);
        }
        saved.put("members", members);
        if (selection.focused() != null) saved.putUUID("focus", selection.focused());
        storage.put(dimension, saved);
    }
}
