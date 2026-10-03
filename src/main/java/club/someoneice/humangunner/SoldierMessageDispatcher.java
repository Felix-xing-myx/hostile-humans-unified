package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import java.util.*;

/** No entity scans. Chatter is dropped; concurrent status reports are merged into bounded batches. */
final class SoldierMessageDispatcher {
    private static final Map<ServerPlayer, Inbox> INBOXES = new WeakHashMap<>();
    private record Report(Component message, boolean urgent) { }
    private static final class Inbox {
        final SoldierChatBudget chatter = new SoldierChatBudget(), reports = new SoldierChatBudget();
        final LinkedHashMap<String, Report> pending = new LinkedHashMap<>();
        int overflow;
    }
    private SoldierMessageDispatcher() { }
    static boolean chatter(ServerPlayer owner, Human human, Component message) {
        var settings = UnifiedConfig.get().soldierMessages();
        Inbox inbox = INBOXES.computeIfAbsent(owner, ignored -> new Inbox());
        if (!inbox.chatter.allow(owner.getServer().overworld().getGameTime(), settings.chatterIntervalTicks(),
                6000, settings.chatterMaxPerFiveMinutes())) return false;
        SoldierCommandFeedback.say(owner, human, message);
        return true;
    }
    static void report(ServerPlayer owner, Human human, String key, Component message, boolean urgent) {
        Inbox inbox = INBOXES.computeIfAbsent(owner, ignored -> new Inbox());
        String id = human.getUUID() + ":" + key;
        Component named = Component.translatable("message.humangunner.soldier.speech", human.getDisplayName(), message);
        if (inbox.pending.containsKey(id) || inbox.pending.size() < 32) {
            inbox.pending.put(id, new Report(named, urgent));
        } else if (urgent) {
            String replace = inbox.pending.entrySet().stream().filter(e -> !e.getValue().urgent)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (replace != null) { inbox.pending.remove(replace); inbox.pending.put(id, new Report(named, true)); }
            inbox.overflow = Math.min(1000000, inbox.overflow + 1);
        } else inbox.overflow = Math.min(1000000, inbox.overflow + 1);
        flush(owner, inbox);
    }
    private static void flush(ServerPlayer owner, Inbox inbox) {
        if (inbox.pending.isEmpty()) return;
        var settings = UnifiedConfig.get().soldierMessages();
        if (!inbox.reports.allow(owner.getServer().overworld().getGameTime(), settings.reportIntervalTicks(),
                1200, settings.reportMaxPerMinute())) return;
        List<Report> reports = new ArrayList<>(inbox.pending.values());
        reports.sort(Comparator.comparing(Report::urgent).reversed());
        MutableComponent batch = Component.empty();
        int details = Math.min(settings.reportDetailsPerMessage(), reports.size());
        for (int i = 0; i < details; i++) {
            if (i != 0) batch.append("; ");
            batch.append(reports.get(i).message);
        }
        int extra = reports.size() - details + inbox.overflow;
        if (extra > 0) batch.append(Component.translatable("message.humangunner.soldier.reports_merged", extra));
        owner.displayClientMessage(batch, false);
        inbox.pending.clear();
        inbox.overflow = 0;
    }
    static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        for (var entry : INBOXES.entrySet()) {
            if (entry.getKey().getServer() == event.getServer()) flush(entry.getKey(), entry.getValue());
        }
    }
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) INBOXES.remove(player);
    }
    static void onStopped(ServerStoppedEvent event) {
        INBOXES.keySet().removeIf(player -> player.getServer() == event.getServer());
    }
}
