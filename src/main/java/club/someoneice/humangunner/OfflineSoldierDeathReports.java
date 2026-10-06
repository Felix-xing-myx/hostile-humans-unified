package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Capture a proposed death, then persist only an actual KILLED removal. */
final class OfflineSoldierDeathReports {
    private static final String ID = "humangunner_offline_deaths";
    static final int MAX_REPORTS = 32;
    // Weak keys prevent cancelled deaths from retaining living entities or worlds.
    private static final Map<Human, Death> PENDING = new WeakHashMap<>();

    private OfflineSoldierDeathReports() {}

    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide) return;
        PENDING.remove(human);
        MinecraftServer server = human.getServer();
        UUID owner = human.getOwnerUUID();
        if (event.isCanceled() || server == null || owner == null
                || server.getPlayerList().getPlayer(owner) != null) return;
        Component cause = event.getSource().getLocalizedDeathMessage(human);
        PENDING.put(human, new Death(human.getUUID(), owner, human.getDisplayName().getString(),
                Component.Serializer.toJson(cause), human.level().dimension().location().toString(),
                human.getBlockX(), human.getBlockY(), human.getBlockZ()));
    }

    static void onEntityLeave(Human human) {
        Death proposed = PENDING.remove(human);
        if (human.level().isClientSide || proposed == null
                || human.getRemovalReason() != Entity.RemovalReason.KILLED) return;
        MinecraftServer server = human.getServer();
        if (server == null) return;
        Store store = get(server);
        store.add(proposed);
        // An owner may log in between the death event and the end of its animation.
        // Deliver immediately once KILLED is confirmed instead of waiting for another login.
        ServerPlayer owner = server.getPlayerList().getPlayer(proposed.owner());
        if (owner != null) deliver(owner, store);
    }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) deliver(player, get(player.server));
    }

    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.keySet().removeIf(human -> human.getServer() == event.getServer());
    }

    private static Store get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Store::load, Store::new, ID);
    }

    private static void deliver(ServerPlayer player, Store store) {
        Batch batch = store.peek(player.getUUID());
        for (Death death : batch.deaths()) {
            Component cause;
            try {
                cause = Component.Serializer.fromJson(death.causeJson());
            } catch (RuntimeException malformed) {
                cause = null;
            }
            if (cause == null) cause = Component.translatable("message.humangunner.offline_death.unknown");
            player.displayClientMessage(Component.translatable("message.humangunner.offline_death",
                    death.name(), cause, death.dimension(), death.x(), death.y(), death.z()), false);
        }
        if (batch.overflow() > 0) player.displayClientMessage(Component.translatable(
                "message.humangunner.offline_death.overflow", batch.overflow()), false);
        store.clear(player.getUUID());
    }

    record Death(UUID human, UUID owner, String name, String causeJson,
                 String dimension, int x, int y, int z) {}
    record Batch(List<Death> deaths, int overflow) {}

    static final class Store extends SavedData {
        private final Map<UUID, LinkedHashMap<UUID, Death>> reports = new LinkedHashMap<>();
        private final Map<UUID, Integer> overflow = new LinkedHashMap<>();

        void add(Death death) {
            LinkedHashMap<UUID, Death> inbox = reports.computeIfAbsent(death.owner(), ignored -> new LinkedHashMap<>());
            if (inbox.containsKey(death.human())) return;
            if (inbox.size() < MAX_REPORTS) inbox.put(death.human(), death);
            else overflow.merge(death.owner(), 1, (a, b) -> a == Integer.MAX_VALUE ? a : a + b);
            setDirty();
        }

        Batch peek(UUID owner) {
            Map<UUID, Death> inbox = reports.get(owner);
            return new Batch(inbox == null ? List.of() : List.copyOf(inbox.values()), overflow.getOrDefault(owner, 0));
        }

        void clear(UUID owner) {
            boolean changed = reports.remove(owner) != null;
            changed |= overflow.remove(owner) != null;
            if (changed) setDirty();
        }

        static Store load(CompoundTag root) {
            Store store = new Store();
            ListTag owners = root.getList("Owners", Tag.TAG_COMPOUND);
            for (int i = 0; i < owners.size(); i++) {
                CompoundTag inbox = owners.getCompound(i);
                if (!inbox.hasUUID("Owner")) continue;
                UUID owner = inbox.getUUID("Owner");
                ListTag entries = inbox.getList("Deaths", Tag.TAG_COMPOUND);
                for (int j = 0; j < entries.size(); j++) {
                    CompoundTag entry = entries.getCompound(j);
                    if (!entry.hasUUID("Human")) continue;
                    store.add(new Death(entry.getUUID("Human"), owner, entry.getString("Name"),
                            entry.getString("Cause"), entry.getString("Dimension"), entry.getInt("X"),
                            entry.getInt("Y"), entry.getInt("Z")));
                }
                int count = Math.max(0, inbox.getInt("Overflow"));
                if (count > 0) store.overflow.merge(owner, count,
                        (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
            }
            return store;
        }

        @Override public CompoundTag save(CompoundTag root) {
            ListTag owners = new ListTag();
            for (UUID owner : reports.keySet()) {
                CompoundTag inbox = new CompoundTag();
                inbox.putUUID("Owner", owner);
                inbox.putInt("Overflow", overflow.getOrDefault(owner, 0));
                ListTag entries = new ListTag();
                for (Death death : reports.get(owner).values()) {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Human", death.human());
                    entry.putString("Name", death.name());
                    entry.putString("Cause", death.causeJson());
                    entry.putString("Dimension", death.dimension());
                    entry.putInt("X", death.x()); entry.putInt("Y", death.y()); entry.putInt("Z", death.z());
                    entries.add(entry);
                }
                inbox.put("Deaths", entries);
                owners.add(inbox);
            }
            root.put("Owners", owners);
            return root;
        }
    }
}
