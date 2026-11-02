package dev.felix.hostilehumans.faction.ability;

import dev.felix.hostilehumans.config.UnifiedConfig;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Configuration/reuse boundaries without constructing a world or client. */
public final class FactionAbilitiesTest {
    private static JsonObject json(String raw) { return JsonParser.parseString(raw).getAsJsonObject(); }
    private static void check(boolean ok) { if (!ok) throw new AssertionError("faction ability configuration"); }
    private static void rejects(String raw) {
        try { new FactionAbilities(json(raw)); throw new AssertionError("invalid ability accepted"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        var defaults = new UnifiedConfig(new JsonObject());
        check(defaults.factionAbilities().forFaction("hostile_humans_unified:ashen").size() == 1);
        check(defaults.factionAbilities().forFaction("hostile_humans_unified:crimson").isEmpty());
        var disabled = new UnifiedConfig(json("{\"faction_abilities\":{\"assignments\":{}}}"));
        check(disabled.factionAbilities().forFaction("hostile_humans_unified:ashen").isEmpty());
        var reusable = new FactionAbilities(json("{\"definitions\":{\"sidestep\":{\"type\":\"dodge\",\"chance_by_tier\":{\"tier1\":1}}},"
                + "\"assignments\":{\"test:a\":[\"sidestep\",\"sidestep\"],\"test:b\":[\"sidestep\"]}}"));
        check(reusable.forFaction("test:a").size() == 1);
        check(reusable.forFaction("test:a").get(0) == reusable.forFaction("test:b").get(0));
        rejects("{\"definitions\":{\"bad\":{\"type\":\"unknown\"}}}");
        rejects("{\"assignments\":{\"test:a\":[\"missing\"]}}");
        rejects("{\"definitions\":{\"bad\":{\"type\":\"dodge\",\"chance_by_tier\":{\"tier1\":1.1}}}}");
    }
}
