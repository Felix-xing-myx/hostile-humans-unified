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
        var formation=new GroupResistanceAbility(new JsonObject());
        check(formation.needsFormationCheck(0));
        check(formation.needsFormationCheck(100));
        check(!formation.needsFormationCheck(101));
        check(!formation.needsFormationCheck(-1));
        check(formation.extendsResistance(0,100));
        check(formation.extendsResistance(0,300)); // Collective renewal includes members above five seconds.
        check(!formation.extendsResistance(0,400));
        check(!formation.extendsResistance(0,600));
        check(!formation.extendsResistance(0,-1));
        check(!formation.extendsResistance(1,50)); // Never overwrite or extend a stronger external effect.
        var defaults = new UnifiedConfig(new JsonObject());
        check(defaults.factionAbilities().forFaction("hostile_humans_unified:ashen").size() == 1);
        check(defaults.factionAbilities().forFaction("hostile_humans_unified:crimson").isEmpty());
        var disabled = new UnifiedConfig(json("{\"faction_abilities\":{\"assignments\":{}}}"));
        check(disabled.factionAbilities().forFaction("hostile_humans_unified:ashen").isEmpty());
        check(disabled.factionAbilities().forFaction("hostile_humans_unified:azure").isEmpty());
        JsonObject legacyInput = json("{\"faction_abilities\":{\"assignments\":{\"hostile_humans_unified:ashen\":[\"dodge\"]}}}");
        JsonObject original = legacyInput.deepCopy();
        var legacy = new UnifiedConfig(legacyInput);
        check(legacy.factionAbilities().forFaction("hostile_humans_unified:azure").get(0) instanceof GroupResistanceAbility);
        check(legacy.factionAbilities().forFaction("hostile_humans_unified:ashen").size() == 1);
        check(legacyInput.equals(original)); // Runtime inheritance must not rewrite the user's JSON.
        var selective = new UnifiedConfig(json("{\"faction_abilities\":{\"assignments\":{\"hostile_humans_unified:azure\":[],\"test:custom\":[\"dodge\"]}}}"));
        check(selective.factionAbilities().forFaction("hostile_humans_unified:azure").isEmpty());
        check(selective.factionAbilities().forFaction("hostile_humans_unified:ashen").size() == 1);
        check(selective.factionAbilities().forFaction("test:custom").size() == 1);
        var reusable = new FactionAbilities(json("{\"definitions\":{\"sidestep\":{\"type\":\"dodge\",\"chance_by_tier\":{\"tier1\":1}}},"
                + "\"assignments\":{\"test:a\":[\"sidestep\",\"sidestep\"],\"test:b\":[\"sidestep\"]}}"));
        check(reusable.forFaction("test:a").size() == 1);
        check(reusable.forFaction("test:a").get(0) == reusable.forFaction("test:b").get(0));
        rejects("{\"definitions\":{\"bad\":{\"type\":\"unknown\"}}}");
        rejects("{\"assignments\":{\"test:a\":[\"missing\"]}}");
        rejects("{\"definitions\":{\"bad\":{\"type\":\"dodge\",\"chance_by_tier\":{\"tier1\":1.1}}}}");
    }
}
