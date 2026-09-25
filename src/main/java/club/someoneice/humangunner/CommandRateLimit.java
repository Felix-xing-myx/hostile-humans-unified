package club.someoneice.humangunner;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;

/** Per live connection, shared across command channels; no persistent NBT writes. */
public final class CommandRateLimit {
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private CommandRateLimit() {}
    public static boolean allow(ServerPlayer player) {
        long tick = player.serverLevel().getGameTime();
        Long previous = LAST.get(player);
        if (previous != null && tick >= previous && tick - previous < 4) return false;
        LAST.put(player, tick);
        return true;
    }
}
