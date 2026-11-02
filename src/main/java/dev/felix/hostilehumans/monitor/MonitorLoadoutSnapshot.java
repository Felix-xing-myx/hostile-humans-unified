package dev.felix.hostilehumans.monitor;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Small display-only menu: no item NBT, damage, inventory coordinates or client-owned layout. */
public record MonitorLoadoutSnapshot(UUID token, long generation, long revision, List<Option> options, UUID selected) {
    public static final UUID UNARMED = new UUID(0, 0);
    public record Option(UUID id, String name, int kind) { }
    public MonitorLoadoutSnapshot { options = List.copyOf(options); }
    static void encode(MonitorLoadoutSnapshot view, FriendlyByteBuf out) {
        out.writeUUID(view.token); out.writeVarLong(view.generation); out.writeVarLong(view.revision);
        out.writeVarInt(view.options.size());
        for (Option option : view.options) { out.writeUUID(option.id); out.writeUtf(option.name, 80); out.writeVarInt(option.kind); }
        out.writeUUID(view.selected);
    }
    static MonitorLoadoutSnapshot decode(FriendlyByteBuf in) {
        UUID token = in.readUUID(); long generation = in.readVarLong(), revision = in.readVarLong();
        int count = in.readVarInt();
        if (count < 0 || count > 33 || generation <= 0 || revision < 0) throw new IllegalArgumentException("Invalid monitor loadout");
        var options = new java.util.ArrayList<Option>(); var ids = new java.util.HashSet<UUID>();
        for (int i = 0; i < count; i++) {
            var option = new Option(in.readUUID(), in.readUtf(80), in.readVarInt());
            if (option.kind < 0 || option.kind > 4 || !ids.add(option.id)) throw new IllegalArgumentException("Invalid monitor option");
            options.add(option);
        }
        var view = new MonitorLoadoutSnapshot(token, generation, revision, options, in.readUUID());
        if (!ids.contains(view.selected) || in.isReadable()) throw new IllegalArgumentException("Invalid selected monitor option");
        return view;
    }
}
