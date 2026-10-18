package club.someoneice.humangunner;

import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import static club.someoneice.humangunner.FactionVisualNetwork.*;

/** Bounded wire format, independent of Forge channel/event initialization. */
final class FactionVisualCodec {
    private FactionVisualCodec() {}
    static void writeDefinitions(Definitions packet, FriendlyByteBuf out) {
        if (packet.entries().size() > 256) throw new IllegalArgumentException("Faction visual count");
        out.writeLong(packet.revision()); out.writeBoolean(packet.humansVisible()); out.writeBoolean(packet.playersVisible());
        out.writeVarInt(packet.entries().size());
        for (Definition entry : packet.entries()) {
            if (ResourceLocation.tryParse(entry.id()) == null || (entry.color() & 0xFF000000) != 0
                    || (!entry.style().equals("base") && !entry.style().equals("none")))
                throw new IllegalArgumentException("Faction visual definition");
            out.writeUtf(entry.id(), 256); out.writeInt(entry.color()); out.writeUtf(entry.name(), 128); out.writeUtf(entry.style(), 8);
        }
    }
    static Definitions readDefinitions(FriendlyByteBuf in) {
        long revision = in.readLong(); boolean humans = in.readBoolean(), players = in.readBoolean();
        int count = in.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Faction visual count");
        List<Definition> entries = new ArrayList<>(count); Set<String> unique = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = in.readUtf(256); int color = in.readInt(); String name = in.readUtf(128), style = in.readUtf(8);
            if (ResourceLocation.tryParse(id) == null || !unique.add(id) || (color & 0xFF000000) != 0
                    || (!style.equals("base") && !style.equals("none"))) throw new IllegalArgumentException("Faction visual definition");
            entries.add(new Definition(id, color, name, style));
        }
        if (in.isReadable()) throw new IllegalArgumentException("Trailing faction visual data");
        return new Definitions(revision, humans, players, entries);
    }
    static void writeBand(PlayerBand packet, FriendlyByteBuf out) {
        if (packet.grade() < 0 || packet.grade() > 4 || (packet.grade() > 0 && ResourceLocation.tryParse(packet.faction()) == null))
            throw new IllegalArgumentException("Player band state");
        out.writeUUID(packet.player()); out.writeUtf(packet.faction(), 256); out.writeVarInt(packet.grade()); out.writeLong(packet.revision());
    }
    static PlayerBand readBand(FriendlyByteBuf in) {
        PlayerBand packet = new PlayerBand(in.readUUID(), in.readUtf(256), in.readVarInt(), in.readLong());
        if (packet.grade() < 0 || packet.grade() > 4 || (packet.grade() > 0 && ResourceLocation.tryParse(packet.faction()) == null)
                || in.isReadable()) throw new IllegalArgumentException("Player band state");
        return packet;
    }
}
