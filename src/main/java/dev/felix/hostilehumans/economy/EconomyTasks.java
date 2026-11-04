package dev.felix.hostilehumans.economy;

import dev.felix.hostilehumans.faction.FactionEconomyLedger;
import dev.felix.hostilehumans.faction.StandingAccess;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * Persistent accepted definitions and one-time histories live in the existing standing player row.
 */
public final class EconomyTasks {
    private EconomyTasks() {}

    private static CompoundTag tasks(FactionEconomyLedger.Draft draft, boolean test) {
        return TaskLedger.instances(draft.account(), test);
    }

    private static String key(Currency currency, String id) {
        return TaskLedger.key(currency, id);
    }

    private static CompoundTag history(
            FactionEconomyLedger.Draft draft, Currency currency, boolean test) {
        return TaskLedger.history(draft.account(), currency, test);
    }

    private static CompoundTag accepted(
            FactionEconomyLedger.Draft draft, Currency currency, String id, boolean test) {
        return TaskLedger.read(draft.account(), currency, id, test);
    }

    private static EconomyPolicy.Task definition(CompoundTag row) {
        return new EconomyPolicy.Task(
                row.getString("id"),
                row.getString("title"),
                EconomyPolicy.TaskKind.valueOf(row.getString("kind")),
                EconomyPolicy.Objective.valueOf(row.getString("objective")),
                new ResourceLocation(row.getString("item")),
                row.getString("target_faction"),
                row.getInt("target"),
                row.getInt("minimum_standing"),
                row.getInt("standing_reward"),
                row.getLong("copper_reward"),
                row.getLong("time_limit"),
                row.getBoolean("once"));
    }

    private static boolean eligible(
            FactionEconomyLedger.Draft draft,
            Currency currency,
            EconomyPolicy.Task task,
            boolean accepted,
            boolean test) {
        int score = draft.standing(currency.faction());
        var history = history(draft, currency, test);
        return switch (task.kind()) {
            case INITIATION ->
                    !draft.permit(currency.faction()).initiated() && score >= -99 && score < 50;
            case ATONEMENT_ONE -> accepted || score < -99 && !history.getBoolean("atonement_one");
            case ATONEMENT_TWO ->
                    history.getBoolean("atonement_one") && !history.getBoolean("atonement_two");
            case BASIC ->
                    score >= Math.max(-99, task.minimumStanding())
                            && (score >= 50
                                    || draft.permit(currency.faction()).active(draft.now(), score));
            case DAILY -> score >= Math.max(50, task.minimumStanding());
            case BOUNTY -> score >= Math.max(250, task.minimumStanding());
            case SOLDIER -> false;
        };
    }

    private static String status(
            FactionEconomyLedger.Draft draft,
            Currency currency,
            EconomyPolicy.Task task,
            CompoundTag row,
            boolean test) {
        if (row == null) return "AVAILABLE";
        String status = row.getString("status");
        if (status.equals("CLAIMED")) return status;
        if (row.getLong("deadline") > 0 && draft.now() >= row.getLong("deadline")) return "EXPIRED";
        if (!eligible(draft, currency, task, true, test)) return "REVOKED";
        return status;
    }

    private static int factor(
            FactionEconomyLedger.Draft draft,
            Currency currency,
            EconomyPolicy.Task task,
            boolean test) {
        var h = history(draft, currency, test);
        long day = Math.max(h.getLong("reward_day"), Math.max(0, draft.now()) / 24000);
        int count = day == h.getLong("reward_day") ? h.getInt("repeat_" + task.kind().name()) : 0;
        return count == 0 ? 100 : count == 1 ? 50 : count == 2 ? 25 : 0;
    }

    public static java.util.List<EconomyView.TaskRow> rows(
            FactionEconomyLedger.Draft draft,
            Currency currency,
            EconomyPolicy policy,
            boolean enabled) {
        boolean test = policy.revision() < 0;
        var result = new ArrayList<EconomyView.TaskRow>();
        var definitions = new LinkedHashMap<String, EconomyPolicy.Task>();
        String prefix = currency.key() + "|";
        var saved = tasks(draft, test);
        for (String key : saved.getAllKeys().stream().sorted().toList()) {
            if (!key.startsWith(prefix)) continue;
            String id = key.substring(prefix.length());
            try {
                var row = accepted(draft, currency, id, test);
                var task = definition(row);
                if (task.id().equals(id) && !row.getString("status").equals("CLAIMED"))
                    definitions.put(id, task);
            } catch (RuntimeException unknown) {
                // Preserve unreadable saved entries without advertising a transaction.
            }
        }
        var offered = new HashSet<String>();
        for (var task : policy.tasks()) {
            offered.add(task.id());
            definitions.putIfAbsent(task.id(), task);
        }
        for (var configured : definitions.values())
            try {
                var row = accepted(draft, currency, configured.id(), test);
                var task = row == null ? configured : definition(row);
                String status = status(draft, currency, task, row, test);
                boolean open = status.equals("ACTIVE") || status.equals("READY");
                boolean completed = row != null && row.getInt("claims") > 0 && task.once();
                boolean eligible = enabled && offered.contains(task.id()) && eligible(draft, currency, task, false, test);
                int nominal =
                        task.kind() == EconomyPolicy.TaskKind.ATONEMENT_ONE
                                ? Math.max(0, -99 - draft.standing(currency.faction()))
                                : task.standingReward() * factor(draft, currency, task, test) / 100;
                int available =
                        task.kind() == EconomyPolicy.TaskKind.ATONEMENT_ONE
                                ? Math.max(0, -99 - draft.standing(currency.faction()))
                                : task.kind() == EconomyPolicy.TaskKind.ATONEMENT_TWO
                                        ? Math.max(
                                                0,
                                                Math.min(
                                                        50 - draft.standing(currency.faction()),
                                                        task.standingReward()))
                                        : dev.felix.hostilehumans.faction.StandingReward
                                                .positiveDelta(
                                                        draft.standing(currency.faction()),
                                                        nominal,
                                                        draft.remaining(currency.faction()));
                int progress = row == null ? 0 : row.getInt("progress");
                result.add(
                        new EconomyView.TaskRow(
                                task.id(),
                                task.title(),
                                task.kind().name(),
                                status,
                                progress,
                                task.count(),
                                nominal,
                                available,
                                task.copperReward() * factor(draft, currency, task, test) / 100,
                                eligible && !open && !completed,
                                enabled
                                        && status.equals("ACTIVE")
                                        && task.objective() == EconomyPolicy.Objective.DELIVERY,
                                enabled && status.equals("READY"),
                                enabled
                                        ? (completed
                                                ? "one-time completion retained"
                                                : "accepted definitions retained; rewards capped at"
                                                      + " current remaining quota")
                                        : policy.reason(),
                                task.objective().name(), task.item().toString(), task.targetFaction(),
                                row == null ? 0 : row.getLong("deadline"), task.timeLimit(), row != null,
                                row == null ? "" : row.getString("site_name"), currency.faction()));
            } catch (RuntimeException unknown) {
                result.add(
                        new EconomyView.TaskRow(
                                configured.id(),
                                configured.title(),
                                configured.kind().name(),
                                "UNKNOWN",
                                0,
                                configured.count(),
                                0,
                                0,
                                0,
                                false,
                                false,
                                false,
                                "unknown task payload retained"));
            }
        return List.copyOf(result);
    }

    public static String accept(
            FactionEconomyLedger.Draft draft, Currency currency, EconomyPolicy policy, String id, String siteName) {
        boolean test = policy.revision() < 0;
        var task =
                policy.tasks().stream()
                        .filter(t -> t.id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown task"));
        var old = accepted(draft, currency, id, test);
        if (old != null) {
            var prior = definition(old);
            String status = status(draft, currency, prior, old, test);
            if (status.equals("ACTIVE")
                    || status.equals("READY")
                    || (prior.once() || task.once()) && old.getInt("claims") > 0)
                throw new IllegalArgumentException("task already accepted or completed");
        }
        if (!eligible(draft, currency, task, false, test))
            throw new IllegalArgumentException("task prerequisites unavailable");
        var row = new CompoundTag();
        row.putInt("schema", 1);
        row.putString("source", test ? "creative_test" : "formal");
        row.putString("id", task.id());
        row.putString("title", task.title());
        row.putString("site_name", siteName);
        row.putString("kind", task.kind().name());
        row.putString("objective", task.objective().name());
        row.putString("item", task.item().toString());
        row.putString("target_faction", task.targetFaction());
        row.putInt("target", task.count());
        row.putInt("minimum_standing", task.minimumStanding());
        row.putInt("standing_reward", task.standingReward());
        row.putLong("copper_reward", task.copperReward());
        row.putLong("time_limit", task.timeLimit());
        row.putBoolean("once", task.once());
        row.putString("status", "ACTIVE");
        row.putInt("progress", 0);
        row.putInt("claims", old == null ? 0 : old.getInt("claims"));
        row.putLong(
                "deadline",
                task.timeLimit() == 0 ? 0 : Math.addExact(draft.now(), task.timeLimit()));
        tasks(draft, test).put(key(currency, id), row);
        return "task accepted with fixed definition";
    }

    public static String deliver(
            FactionEconomyLedger.Draft draft,
            InventoryTransaction inventory,
            Currency currency,
            EconomyPolicy policy,
            String id) {
        boolean test = policy.revision() < 0;
        var row = accepted(draft, currency, id, test);
        if (row == null) throw new IllegalArgumentException("task not accepted");
        var task = definition(row);
        if (!status(draft, currency, task, row, test).equals("ACTIVE")
                || task.objective() != EconomyPolicy.Objective.DELIVERY)
            throw new IllegalArgumentException("task delivery unavailable");
        int remaining = task.count() - row.getInt("progress");
        inventory.consume(ForgeRegistries.ITEMS.getValue(task.item()), remaining);
        row.putInt("progress", task.count());
        row.putString("status", "READY");
        return "required delivery consumed; claim reward separately";
    }

    public static String claim(
            FactionEconomyLedger.Draft draft,
            InventoryTransaction inventory,
            Currency currency,
            EconomyPolicy policy,
            String id) {
        boolean test = policy.revision() < 0;
        var row = accepted(draft, currency, id, test);
        if (row == null) throw new IllegalArgumentException("task not accepted");
        var task = definition(row);
        if (!status(draft, currency, task, row, test).equals("READY")
                || row.getInt("progress") < task.count())
            throw new IllegalArgumentException("task not ready");
        int multiplier = factor(draft, currency, task, test);
        int nominal = task.standingReward() * multiplier / 100;
        int actual;
        switch (task.kind()) {
            case INITIATION -> {
                draft.permit(currency.faction(), draft.permit(currency.faction()).initiate());
                actual = 0;
            }
            case ATONEMENT_ONE -> {
                actual = draft.redemption(currency.faction(), 1, 0);
                history(draft, currency, test).putBoolean("atonement_one", true);
            }
            case ATONEMENT_TWO -> {
                actual = draft.redemption(currency.faction(), 2, task.standingReward());
                history(draft, currency, test).putBoolean("atonement_two", true);
            }
            default -> actual = draft.gain(currency.faction(), nominal);
        }
        long cash = Math.multiplyExact(task.copperReward(), multiplier) / 100;
        if (cash > 0) inventory.credit(currency, cash);
        var h = history(draft, currency, test);
        long day = Math.max(h.getLong("reward_day"), Math.max(0, draft.now()) / 24000);
        if (day > h.getLong("reward_day")) {
            for (String k : List.copyOf(h.getAllKeys())) if (k.startsWith("repeat_")) h.remove(k);
            h.putLong("reward_day", day);
        }
        String repeat = "repeat_" + task.kind().name();
        h.putInt(repeat, Math.min(1_000_000, h.getInt(repeat) + 1));
        row.putString("status", "CLAIMED");
        row.putInt("claims", Math.min(1_000_000, row.getInt("claims") + 1));
        return "task claimed once: standing +" + actual + ", copper +" + cash;
    }

    /**
     * Confirmed actual deaths only; caller deduplicates victim UUID and supplies canonical
     * responsibility.
     */
    public static void killed(
            MinecraftServer server,
            UUID owner,
            String victimFaction,
            boolean boss,
            boolean owned,
            boolean parley) {
        if (parley || owned || owner == null || !server.isSameThread()) return;
        var snapshot = StandingAccess.snapshot(server, owner, Currency.AZURE.faction());
        if (!snapshot.enabled() || !snapshot.known()) return;
        try {
            var draft = FactionEconomyLedger.draft(server, owner);
            boolean changed = false;
            for (boolean test : new boolean[] {false, true}) {
                var live = server.getPlayerList().getPlayer(owner);
                if (test && (live == null || !live.isCreative())) continue;
                for (Currency currency : Currency.values())
                    for (String key : List.copyOf(tasks(draft, test).getAllKeys()))
                        if (key.startsWith(currency.key() + "|")) {
                            var row = tasks(draft, test).getCompound(key);
                            if (row.getInt("schema") != 1
                                    || !row.getString("source")
                                            .equals(test ? "creative_test" : "formal")) continue;
                            var task = definition(row);
                            if (!status(draft, currency, task, row, test).equals("ACTIVE"))
                                continue;
                            boolean match =
                                    switch (task.objective()) {
                                        case DELIVERY -> false;
                                        case KILL_ROAMER -> victimFaction.isEmpty() && !boss;
                                        case KILL_FACTION ->
                                                StandingAccess.enemies(
                                                                currency.faction(), victimFaction)
                                                        && (task.targetFaction().isEmpty()
                                                                || dev.felix.hostilehumans.economy
                                                                        .Currency.faction(
                                                                                task
                                                                                        .targetFaction())
                                                                        .equals(
                                                                                dev.felix
                                                                                        .hostilehumans
                                                                                        .economy
                                                                                        .Currency
                                                                                        .faction(
                                                                                                victimFaction)));
                                        case KILL_BOSS ->
                                                boss
                                                        && StandingAccess.enemies(
                                                                currency.faction(), victimFaction);
                                    };
                            if (match) {
                                int progress = Math.min(task.count(), row.getInt("progress") + 1);
                                row.putInt("progress", progress);
                                if (progress == task.count()) row.putString("status", "READY");
                                changed = true;
                            }
                        }
            }
            if (changed) draft.commitState();
        } catch (RuntimeException unknown) {
        }
    }
}
