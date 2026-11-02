package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.observer.NavigationObserverPreferences;
import dev.felix.hostilehumans.observer.NavigationObserverState;

import dev.felix.hostilehumans.observer.NavigationObserverSelection;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;

/** File restart/overwrite, corrupt input, and dimension-scoped bookmark round trips; no live game. */
public final class NavigationObserverPersistenceTest {
    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("observer-preferences-");
        Path file = directory.resolve("preferences.json");
        try {
            check(NavigationObserverPreferences.load(file).equals(NavigationObserverPreferences.DEFAULTS), "missing settings use defaults");
            var settings = new NavigationObserverPreferences.Settings(List.of(0, 2, 1, 0), 5);
            NavigationObserverPreferences.save(file, settings);
            check(NavigationObserverPreferences.load(file).equals(settings), "display scope and page survive a fresh read");
            NavigationObserverPreferences.save(file, NavigationObserverPreferences.DEFAULTS);
            check(NavigationObserverPreferences.load(file).equals(NavigationObserverPreferences.DEFAULTS), "atomic replacement of existing settings");
            try (var files = Files.list(directory)) { check(files.count() == 1, "no stale temporary files"); }
            for (String malformed : List.of("{", "null", "{\"version\":2}",
                    "{\"version\":1,\"layers\":[0,1,2,3],\"page\":0}",
                    "{\"version\":1,\"layers\":[0,1,2,0],\"page\":7}",
                    "{\"version\":1,\"layers\":[0,1,2,0.5],\"page\":0}",
                    "{\"version\":1,\"layers\":[0,1,2],\"page\":0}")) {
                Files.writeString(file, malformed);
                try { NavigationObserverPreferences.load(file); throw new AssertionError("invalid settings accepted: " + malformed); }
                catch (IOException expected) { check(Files.readString(file).equals(malformed), "invalid files are not silently rewritten on read"); }
            }
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var selection = new NavigationObserverSelection<UUID>(8);
        selection.add(a); selection.add(b); selection.add(c); selection.remove(b); selection.add(a);
        var storage = new CompoundTag();
        NavigationObserverState.save(storage, "minecraft:overworld", selection);
        var reloaded = NavigationObserverState.load(storage.copy(), "minecraft:overworld");
        check(reloaded.entries().equals(selection.entries()) && a.equals(reloaded.focused()), "restart keeps UUID order, sparse colors and explicit focus");
        check(c.equals(reloaded.cycle(id -> id.equals(c))), "focus cycling skips pending members without deleting them");
        check(reloaded.contains(a) && reloaded.entries().equals(selection.entries()), "pending UUID and stable colors remain");
        check(c.equals(reloaded.cycle(id -> false)), "no available member retains the saved focus");
        check(NavigationObserverState.load(storage, "minecraft:the_nether").isEmpty(), "no cross-dimension observation");
        check(NavigationObserverState.load(new CompoundTag(), "minecraft:overworld").isEmpty(), "another player/world has no selections");
        NavigationObserverState.save(storage, "minecraft:the_nether", selection);
        NavigationObserverState.save(storage, "minecraft:overworld", new NavigationObserverSelection<>(8));
        check(NavigationObserverState.load(storage, "minecraft:overworld").isEmpty(), "manual clear survives restart");
        check(NavigationObserverState.load(storage, "minecraft:the_nether").entries().equals(selection.entries()), "clear leaves other dimensions untouched");
        var tag = storage.getCompound("minecraft:the_nether");
        var list = tag.getList("members", Tag.TAG_COMPOUND);
        CompoundTag duplicateSlot = new CompoundTag(); duplicateSlot.putUUID("id", b); duplicateSlot.putInt("slot", 0); list.add(duplicateSlot);
        CompoundTag invalidSlot = new CompoundTag(); invalidSlot.putUUID("id", b); invalidSlot.putInt("slot", 99); list.add(invalidSlot);
        CompoundTag malformedId = new CompoundTag(); malformedId.putString("id", "invalid"); malformedId.putInt("slot", 1); list.add(malformedId);
        tag.putUUID("focus", UUID.randomUUID());
        reloaded = NavigationObserverState.load(storage, "minecraft:the_nether");
        check(reloaded.entries().equals(selection.entries()), "invalid and duplicate entries cannot replace a member or its color");
        check(a.equals(reloaded.focused()), "missing focus falls back to first member");
        tag.putInt("version", 999);
        check(NavigationObserverState.load(storage, "minecraft:the_nether").isEmpty(), "unknown future format is not interpreted");
        check(!storage.toString().contains("destination") && !storage.toString().contains("deadline"), "bookmarks never contain movement tests");
        System.out.println("NavigationObserverPersistenceTest passed");
    }
}
