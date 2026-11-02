package dev.felix.hostilehumans.economy;

import com.google.gson.*;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/**
 * All definitions validate before a new revision is published. Invalid reloads retain the previous
 * working policy.
 */
public final class EconomyTables extends SimplePreparableReloadListener<JsonObject> {
    public static final EconomyTables INSTANCE = new EconomyTables();
    private static volatile EconomyPolicy current = EconomyPolicy.disabled();

    private EconomyTables() {}

    public static EconomyPolicy current() {
        return current;
    }

    @Override
    protected JsonObject prepare(ResourceManager manager, ProfilerFiller profiler) {
        var resource =
                manager.getResource(
                        new ResourceLocation(HumanRuntime.MOD_ID, "economy/policy.json"));
        if (resource.isEmpty()) return new JsonObject();
        try (var reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception invalid) {
            var result = new JsonObject();
            result.addProperty("invalid", invalid.toString());
            return result;
        }
    }

    @Override
    protected void apply(JsonObject json, ResourceManager manager, ProfilerFiller profiler) {
        try {
            if (json.has("invalid"))
                throw new IllegalArgumentException(json.get("invalid").getAsString());
            current = parse(json, current.revision() + 1);
        } catch (RuntimeException bad) {
            HumanRuntime.LOGGER.warn(
                    "Economy policy rejected; old revision retained: {}", bad.getMessage());
        }
    }

    private static long integer(JsonObject object, String key, long min, long max) {
        long value = object.get(key).getAsBigDecimal().longValueExact();
        if (value < min || value > max)
            throw new IllegalArgumentException("economy numeric bound: " + key);
        return value;
    }

    private static String text(JsonObject object, String key, int max) {
        String value = object.get(key).getAsString();
        if (value.length() > max) throw new IllegalArgumentException("economy text bound");
        return value;
    }

    public static EconomyPolicy parse(JsonObject json, long revision) {
        if (json.size() == 0 || !json.has("enabled") || !json.get("enabled").getAsBoolean())
            return new EconomyPolicy(
                    revision,
                    false,
                    Map.of(),
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    "Formal economy disabled/unconfigured; explicit creative test only.");
        if (integer(json, "schema", 1, 1) != 1)
            throw new IllegalArgumentException("unknown economy policy schema");
        var rates = new EnumMap<Currency, MoneyRate>(Currency.class);
        var coefficients = json.getAsJsonObject("coefficients");
        for (Currency currency : Currency.values()) {
            var rate = coefficients.getAsJsonObject(currency.key());
            rates.put(
                    currency,
                    new MoneyRate(
                            integer(rate, "numerator", 1, 1_000_000_000),
                            integer(rate, "denominator", 1, 1_000_000_000)));
        }
        if (!rates.get(Currency.AZURE).equals(new MoneyRate(1, 1)))
            throw new IllegalArgumentException("Azure is the neutral common base");
        long cap = integer(json, "exchange_daily_azure", 1, 1_000_000_000),
                deposit = integer(json, "deposit_base_azure", 1, 1_000_000_000),
                value = integer(json, "contribution_value_per_standing", 1, 1_000_000_000);
        var offers = new ArrayList<EconomyPolicy.Offer>();
        var offerIds = new HashSet<String>();
        var offerItems = new HashSet<ResourceLocation>();
        for (var element : json.getAsJsonArray("offers")) {
            if (offers.size() >= 64) throw new IllegalArgumentException("too many offers");
            var row = element.getAsJsonObject();
            String id = text(row, "id", 128);
            var item = ResourceLocation.tryParse(text(row, "item", 128));
            if (ResourceLocation.tryParse(id) == null
                    || item == null
                    || item.equals(new ResourceLocation("minecraft:air"))
                    || ForgeRegistries.ITEMS.getValue(item) instanceof CurrencyItem
                    || ForgeRegistries.ITEMS.getValue(item) instanceof WalletItem
                    || ForgeRegistries.ITEMS.getValue(item) == null
                    || !offerIds.add(id)
                    || !offerItems.add(item))
                throw new IllegalArgumentException("invalid/duplicate offer");
            var category = EconomyPolicy.Category.valueOf(text(row, "category", 32));
            boolean buy = row.get("buy").getAsBoolean(), sell = row.get("sell").getAsBoolean();
            if (category != EconomyPolicy.Category.FOOD
                    && category != EconomyPolicy.Category.MATERIAL)
                throw new IllegalArgumentException(
                        "first trader only food/material; equipment shops deferred");
            offers.add(
                    new EconomyPolicy.Offer(
                            id,
                            item,
                            category,
                            integer(row, "base_azure", 1, 1_000_000_000),
                            buy,
                            sell));
        }
        var tasks = new ArrayList<EconomyPolicy.Task>();
        var taskIds = new HashSet<String>();
        var uniqueKinds = java.util.EnumSet.noneOf(EconomyPolicy.TaskKind.class);
        for (var element : json.getAsJsonArray("tasks")) {
            if (tasks.size() >= 64) throw new IllegalArgumentException("too many tasks");
            var row = element.getAsJsonObject();
            String id = text(row, "id", 128);
            if (ResourceLocation.tryParse(id) == null || !taskIds.add(id))
                throw new IllegalArgumentException("invalid/duplicate task ID");
            var kind = EconomyPolicy.TaskKind.valueOf(text(row, "kind", 32));
            if (kind == EconomyPolicy.TaskKind.SOLDIER)
                throw new IllegalArgumentException("soldier tasks deferred");
            var objective = EconomyPolicy.Objective.valueOf(text(row, "objective", 32));
            var item = row.has("item") ? ResourceLocation.tryParse(text(row, "item", 128)) : null;
            if (item == null) item = new ResourceLocation("minecraft:air");
            if (objective == EconomyPolicy.Objective.DELIVERY
                    && (item.equals(new ResourceLocation("minecraft:air"))
                            || ForgeRegistries.ITEMS.getValue(item) == null))
                throw new IllegalArgumentException("unknown delivery item");
            String target = row.has("target_faction") ? text(row, "target_faction", 128) : "";
            int minimum = (int) integer(row, "minimum_standing", -500, 500);
            if (kind == EconomyPolicy.TaskKind.BASIC) minimum = Math.max(-99, minimum);
            if (kind == EconomyPolicy.TaskKind.DAILY) minimum = Math.max(50, minimum);
            if (kind == EconomyPolicy.TaskKind.BOUNTY) minimum = Math.max(250, minimum);
            boolean once = row.has("once") && row.get("once").getAsBoolean();
            if (kind == EconomyPolicy.TaskKind.INITIATION
                    || kind == EconomyPolicy.TaskKind.ATONEMENT_ONE
                    || kind == EconomyPolicy.TaskKind.ATONEMENT_TWO
                    || kind == EconomyPolicy.TaskKind.BOUNTY) once = true;
            int reward = (int) integer(row, "standing", 0, 1000);
            if (kind == EconomyPolicy.TaskKind.INITIATION
                    || kind == EconomyPolicy.TaskKind.ATONEMENT_ONE
                    || kind == EconomyPolicy.TaskKind.ATONEMENT_TWO) {
                if (!uniqueKinds.add(kind))
                    throw new IllegalArgumentException("duplicate one-time political task kind");
            }
            if (kind == EconomyPolicy.TaskKind.INITIATION
                    && (objective != EconomyPolicy.Objective.DELIVERY || reward != 0))
                throw new IllegalArgumentException(
                        "initiation requires real delivery and no invented standing reward");
            if (kind == EconomyPolicy.TaskKind.ATONEMENT_ONE
                    && objective != EconomyPolicy.Objective.KILL_ROAMER
                    && objective != EconomyPolicy.Objective.KILL_FACTION)
                throw new IllegalArgumentException(
                        "first atonement requires actual enemy clearance");
            if (kind == EconomyPolicy.TaskKind.ATONEMENT_TWO
                    && (objective != EconomyPolicy.Objective.DELIVERY
                            || reward < 80
                            || reward > 120))
                throw new IllegalArgumentException(
                        "second atonement requires delivery and nominal 80..120");
            tasks.add(
                    new EconomyPolicy.Task(
                            id,
                            text(row, "title", 128),
                            kind,
                            objective,
                            item,
                            target,
                            (int) integer(row, "count", 1, 4096),
                            minimum,
                            reward,
                            integer(row, "copper_reward", 0, 1_000_000_000),
                            integer(row, "time_limit", 0, 1_728_000),
                            once));
        }
        if (offers.isEmpty() || tasks.isEmpty())
            throw new IllegalArgumentException(
                    "enabled economy requires complete prices and minimal task table");
        return new EconomyPolicy(
                revision,
                true,
                rates,
                cap,
                deposit,
                value,
                offers,
                tasks,
                "Validated formal economy");
    }

    public static EconomyPolicy test() {
        var rates = new EnumMap<Currency, MoneyRate>(Currency.class);
        rates.put(Currency.CRIMSON, new MoneyRate(3, 4));
        rates.put(Currency.AZURE, new MoneyRate(1, 1));
        rates.put(Currency.VERDANT, new MoneyRate(7, 5));
        rates.put(Currency.ASHEN, new MoneyRate(9, 5));
        var bread = new ResourceLocation("minecraft:bread");
        var iron = new ResourceLocation("minecraft:iron_ingot");
        return new EconomyPolicy(
                -1,
                true,
                rates,
                100_000,
                100,
                10,
                List.of(
                        new EconomyPolicy.Offer(
                                "hostile_humans_unified:test_bread",
                                bread,
                                EconomyPolicy.Category.FOOD,
                                100,
                                true,
                                true),
                        new EconomyPolicy.Offer(
                                "hostile_humans_unified:test_iron",
                                iron,
                                EconomyPolicy.Category.MATERIAL,
                                1000,
                                true,
                                true)),
                List.of(
                        new EconomyPolicy.Task(
                                "hostile_humans_unified:initiation",
                                "Test initiation: deliver bread",
                                EconomyPolicy.TaskKind.INITIATION,
                                EconomyPolicy.Objective.DELIVERY,
                                bread,
                                "",
                                2,
                                -99,
                                0,
                                0,
                                0,
                                true),
                        new EconomyPolicy.Task(
                                "hostile_humans_unified:atonement_one",
                                "Test atonement: defeat one roamer",
                                EconomyPolicy.TaskKind.ATONEMENT_ONE,
                                EconomyPolicy.Objective.KILL_ROAMER,
                                null,
                                "",
                                1,
                                -500,
                                0,
                                0,
                                0,
                                true),
                        new EconomyPolicy.Task(
                                "hostile_humans_unified:atonement_two",
                                "Test atonement: strategic iron",
                                EconomyPolicy.TaskKind.ATONEMENT_TWO,
                                EconomyPolicy.Objective.DELIVERY,
                                iron,
                                "",
                                1,
                                -500,
                                100,
                                0,
                                0,
                                true),
                        new EconomyPolicy.Task(
                                "hostile_humans_unified:basic_delivery",
                                "Test cautious delivery",
                                EconomyPolicy.TaskKind.BASIC,
                                EconomyPolicy.Objective.DELIVERY,
                                bread,
                                "",
                                1,
                                -99,
                                10,
                                25,
                                24000,
                                false)),
                "CREATIVE TEST VALUES: not a balanced formal economy");
    }
}
