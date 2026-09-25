package com.craftix.hostile_humans;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Reads the FINAL JAR through Minecraft's NBT and Forge's pack metadata readers.
 * No client, server, world, launcher or registry bootstrap is started. */
public final class StructureCompatTest {
    private static final Set<String> PACKS = Set.of("craftin", "craftin_farmers",
            "craftin_farmers_waystones", "craftin_waystones", "farmers", "farmers_waystones", "waystones");
    private static int checks;
    public static void main(String[] args) throws Exception {
        Set<String> selected = new HashSet<>();
        for (int mask = 0; mask < 32; mask++) {
            Set<String> installed = new HashSet<>();
            if ((mask & 1) != 0) installed.add("quark");
            if ((mask & 2) != 0) installed.add("mctb");
            if ((mask & 4) != 0) installed.add("farmersdelight");
            if ((mask & 8) != 0) installed.add("waystones");
            boolean disableWaystones = (mask & 16) != 0;
            String pack = EventHandlerMod.selectPack(installed::contains, disableWaystones).orElse("");
            check(pack.contains("waystones") == (installed.contains("waystones") && !disableWaystones),
                    "Waystones gate " + mask);
            check(pack.contains("farmers") == installed.contains("farmersdelight"), "Farmer gate " + mask);
            check(pack.contains("craftin") == (installed.contains("quark") && installed.contains("mctb")),
                    "Craftin dependency pair " + mask);
            check(pack.isEmpty() || PACKS.contains(pack), "known pack " + mask);
            if (!pack.isEmpty()) selected.add(pack);
        }
        check(selected.equals(PACKS), "all seven overlays selectable");
        check(EventHandlerMod.selectPack(mod -> false, false).isEmpty(), "vanilla has no overlay");
        SharedConstants.tryDetectVersion();
        Path artifact = Path.of(args[0]);
        int nbts = 0, jsons = 0, files = 0;
        int baseStructures = 0;
        Map<String, Set<String>> namespaces = new TreeMap<>();
        Set<String> leaks = new TreeSet<>();
        try (var fs = FileSystems.newFileSystem(artifact); var jar = new ZipFile(artifact.toFile())) {
            for (String pack : PACKS) {
                var loaded = EventHandlerMod.readAddon(fs.getPath("/datapacks/" + pack), pack);
                check(loaded.isPresent(), "real metadata reader loads " + pack);
                check(loaded.orElseThrow().getId().equals("hostile_humans:" + pack), "stable pack ID");
            }
            check(EventHandlerMod.readAddon(fs.getPath("/datapacks/not_present"), "not_present").isEmpty(),
                    "missing optional pack returns empty instead of blocking a world");
            for (var entry : Collections.list(jar.entries())) {
                if (entry.getName().startsWith("data/hostile_humans/loot_tables/") && entry.getName().endsWith(".json")) {
                    try (var stream = jar.getInputStream(entry)) {
                        String content = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        check(!content.contains("farmersdelight:") && !content.contains("waystones:"),
                                "base loot cannot require optional items: " + entry.getName());
                    }
                }
                if (entry.getName().startsWith("data/hostile_humans/structures/") && entry.getName().endsWith(".nbt")) {
                    try (var stream = jar.getInputStream(entry)) {
                        Set<String> ids = new TreeSet<>();
                        collectIds(NbtIo.readCompressed(stream), ids);
                        for (String id : ids) {
                            if (!(id.startsWith("minecraft:") || id.startsWith("forge:")
                                    || id.startsWith("hostile_humans:") || id.startsWith("humangunner:")))
                                leaks.add(entry.getName() + " -> " + id);
                        }
                        baseStructures++;
                    }
                }
                if (!entry.getName().startsWith("datapacks/") || entry.isDirectory()) continue;
                files++;
                String pack = entry.getName().split("/")[1];
                check(PACKS.contains(pack), "pack has a runtime selection rule");
                try (var stream = jar.getInputStream(entry)) {
                    if (entry.getName().endsWith(".nbt")) {
                        CompoundTag nbt = NbtIo.readCompressed(stream);
                        Set<String> ids = new TreeSet<>();
                        collectIds(nbt, ids);
                        Set<String> mods = namespaces.computeIfAbsent(pack, k -> new TreeSet<>());
                        for (String id : ids) {
                            String mod = id.split(":")[0];
                            mods.add(mod);
                            if (mod.equals("waystones") && !pack.contains("waystones")
                                    || mod.equals("farmersdelight") && !pack.contains("farmers")
                                    || (mod.equals("quark") || mod.equals("mctb")) && !pack.contains("craftin"))
                                leaks.add(entry.getName() + " -> " + id);
                        }
                        check(nbt.contains("size", Tag.TAG_LIST), "structure has size");
                        check(nbt.contains("palette", Tag.TAG_LIST) || nbt.contains("palettes", Tag.TAG_LIST), "structure has palette");
                        nbts++;
                    } else if (entry.getName().endsWith(".json") || entry.getName().endsWith(".mcmeta")) {
                        JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                        jsons++;
                    }
                }
            }
            System.out.println("Unexpected optional references: " + leaks);
            check(leaks.isEmpty(), "no optional block/entity/item leaks into unrelated overlays");
            for (String pack : PACKS) check(namespaces.get(pack).contains("waystones") == pack.contains("waystones"),
                    "Waystones structures exist exactly in enabled overlays: " + pack);
        }
        check(files == 176 && nbts == 133 && jsons == 43, "complete upstream overlays plus twelve Farmer chest-table overrides");
        Path temporary = Files.createTempDirectory(Path.of(args[1]), "structure-pack-test-");
        try {
            Files.writeString(temporary.resolve("pack.mcmeta"), "{\"pack\":{}}");
            check(EventHandlerMod.readAddon(temporary, "invalid_metadata").isEmpty(), "bad optional metadata falls back safely");
        } finally {
            Files.deleteIfExists(temporary.resolve("pack.mcmeta"));
            Files.deleteIfExists(temporary);
        }
        System.out.println("StructureCompatTest: " + checks + " checks; 32 dependency/config combinations, 7 actual packs, "
                + nbts + " overlay NBT, " + baseStructures + " base NBT, " + jsons + " JSON/metadata; missing/invalid pack fallbacks passed");
        System.out.println("Structure namespaces: " + namespaces);
    }
    private static void collectIds(Tag tag, Set<String> ids) {
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                Tag value = compound.get(key);
                if ((key.equals("Name") || key.equals("id")) && value instanceof StringTag) {
                    String text = value.getAsString();
                    if (text.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) ids.add(text);
                }
                collectIds(value, ids);
            }
        } else if (tag instanceof ListTag list) for (Tag value : list) collectIds(value, ids);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
