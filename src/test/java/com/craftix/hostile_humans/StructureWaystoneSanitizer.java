package com.craftix.hostile_humans;

import com.google.gson.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit maintenance tool, never part of normal build/check or runtime.
 * Removes Waystones from non-Waystones overlays and normalizes Farmer blocks in base structures. */
public final class StructureWaystoneSanitizer {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        boolean apply = args.length > 1 && args[1].equals("--apply");
        JsonObject report = new JsonObject();
        try (var walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".nbt")).sorted().toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                if (!name.startsWith("data/hostile_humans/structures/")
                        && !name.startsWith("datapacks/")) continue;
                if (name.startsWith("datapacks/") && name.split("/")[1].contains("waystones")) continue;
                CompoundTag nbt;
                try (var stream = Files.newInputStream(file)) { nbt = NbtIo.readCompressed(stream); }
                Set<Integer> replaced = new HashSet<>();
                cleanPalette(nbt.getList("palette", Tag.TAG_COMPOUND), replaced);
                for (Tag palette : nbt.getList("palettes", Tag.TAG_LIST))
                    cleanPalette((ListTag) palette, replaced);
                int removedNbt = 0;
                int farmerStates = name.startsWith("data/") ? normalizeBaseFarmers(nbt) : 0;
                for (Tag blockTag : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
                    CompoundTag block = (CompoundTag) blockTag;
                    if (block.contains("nbt") && (replaced.contains(block.getInt("state"))
                            || block.getCompound("nbt").getString("id").startsWith("waystones:"))) {
                        block.remove("nbt");
                        removedNbt++;
                    }
                }
                if (replaced.isEmpty() && removedNbt == 0 && farmerStates == 0) continue;
                JsonObject entry = new JsonObject();
                entry.addProperty("upstream_sha256", hash(file));
                entry.addProperty("palette_entries_replaced_with_air", replaced.size());
                entry.addProperty("waystone_block_entities_removed", removedNbt);
                entry.addProperty("farmer_palette_states_mapped_to_vanilla", farmerStates);
                if (apply) {
                    try (var stream = Files.newOutputStream(file)) { NbtIo.writeCompressed(nbt, stream); }
                    entry.addProperty("repaired_sha256", hash(file));
                }
                report.add(name, entry);
            }
        }
        System.out.println("WAYSTONE_REPAIR_REPORT=" + new Gson().toJson(report));
    }
    private static void cleanPalette(ListTag palette, Set<Integer> replaced) {
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag state = palette.getCompound(i);
            if (state.getString("Name").startsWith("waystones:")) {
                CompoundTag air = new CompoundTag();
                air.putString("Name", "minecraft:air");
                palette.set(i, air);
                replaced.add(i);
            }
        }
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static int normalizeBaseFarmers(CompoundTag nbt) {
        Map<String, String> fallback = Map.of(
                "farmersdelight:basket", "minecraft:barrel",
                "farmersdelight:cabbage_crate", "minecraft:hay_block",
                "farmersdelight:cutting_board", "minecraft:air",
                "farmersdelight:spruce_cabinet", "minecraft:barrel",
                "farmersdelight:stove", "minecraft:furnace",
                "farmersdelight:budding_tomatoes", "minecraft:wheat",
                "farmersdelight:tomatoes", "minecraft:wheat");
        Map<Integer, String> changed = new HashMap<>();
        ListTag palette = nbt.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            String old = palette.getCompound(i).getString("Name");
            if (!old.startsWith("farmersdelight:")) continue;
            String name = fallback.get(old);
            if (name == null) throw new IllegalArgumentException("No reviewed vanilla replacement: " + old);
            CompoundTag state = new CompoundTag();
            state.putString("Name", name);
            palette.set(i, state);
            changed.put(i, name);
        }
        for (Tag tag : nbt.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) tag;
            String replacement = changed.get(block.getInt("state"));
            if (replacement == null || !block.contains("nbt")) continue;
            CompoundTag old = block.getCompound("nbt");
            if (replacement.equals("minecraft:barrel")) {
                CompoundTag container = new CompoundTag();
                container.putString("id", replacement);
                for (String key : List.of("Items", "LootTable", "LootTableSeed", "CustomName", "Lock"))
                    if (old.contains(key)) container.put(key, old.get(key).copy());
                for (Tag item : container.getList("Items", Tag.TAG_COMPOUND)) {
                    CompoundTag stack = (CompoundTag) item;
                    if (stack.getString("id").equals("farmersdelight:minced_beef")) stack.putString("id", "minecraft:beef");
                    else if (stack.getString("id").startsWith("farmersdelight:"))
                        throw new IllegalArgumentException("Unreviewed container item: " + stack.getString("id"));
                }
                block.put("nbt", container);
            } else block.remove("nbt");
        }
        return changed.size();
    }
}
