package club.someoneice.humangunner;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent true standing and per-faction reward allowance, independent of equipment and client state. */
final class FactionStandingData extends SavedData {
    private record Entry(int value, long rewardDay, int rewardEarned) {}
    private final Map<UUID, Map<String, Entry>> players = new HashMap<>();
    static FactionStandingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FactionStandingData::load,
                FactionStandingData::new, "hostile_humans_unified_standing");
    }
    int standing(UUID player, String faction) { return entry(player, faction).value(); }
    private Entry entry(UUID player, String faction) {
        Map<String, Entry> values = players.computeIfAbsent(player, ignored -> new HashMap<>());
        Entry stored = values.get(faction);
        if (stored != null) return stored;
        var definition = FactionDefinitions.find(faction);
        stored = new Entry(definition == null ? 100 : definition.startingStanding(), -1, 0);
        values.put(faction, stored);
        setDirty();
        return stored;
    }
    /** Rewards use overworld gameTime, never mutable dayTime; rollback cannot replenish an allowance. */
    int change(UUID player, String faction, int amount, long gameTime) {
        Entry previous = entry(player, faction);
        long day = Math.max(previous.rewardDay(), Math.max(0, gameTime) / 24000);
        int earned = day > previous.rewardDay() ? 0 : previous.rewardEarned();
        int applied = amount > 0 ? Math.min(amount, Math.max(0, 30 - earned)) : amount;
        int next = (int) Math.max(-500L, Math.min(500L, (long) previous.value() + applied));
        if (applied > 0) earned += applied;
        players.get(player).put(faction, new Entry(next, day, earned));
        setDirty();
        return next - previous.value();
    }
    static FactionStandingData load(CompoundTag root) {
        FactionStandingData result = new FactionStandingData();
        for (Tag raw : root.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) raw;
            if (!player.hasUUID("uuid")) continue;
            Map<String, Entry> factions = new HashMap<>();
            for (Tag value : player.getList("factions", Tag.TAG_COMPOUND)) {
                CompoundTag faction = (CompoundTag) value;
                var id = FactionDefinitions.id(faction.getString("id"));
                if (id == null) continue;
                factions.put(id.toString(), new Entry(Math.max(-500, Math.min(500, faction.getInt("standing"))),
                        Math.max(-1, faction.getLong("reward_day")), Math.max(0, Math.min(30, faction.getInt("earned")))));
            }
            result.players.put(player.getUUID("uuid"), factions);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(player -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", player.getKey());
            ListTag factions = new ListTag();
            player.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value -> {
                CompoundTag f = new CompoundTag();
                f.putString("id", value.getKey()); f.putInt("standing", value.getValue().value());
                f.putLong("reward_day", value.getValue().rewardDay()); f.putInt("earned", value.getValue().rewardEarned());
                factions.add(f);
            });
            tag.put("factions", factions); list.add(tag);
        });
        root.put("players", list);
        return root;
    }
}
