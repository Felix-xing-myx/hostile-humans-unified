package dev.felix.hostilehumans.faction.ability;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.faction.HumanFactions;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/** One capability registry and one server event boundary. Assignment is independent of AI doctrine. */
public final class FactionAbilities {
    private static final Map<String, Function<JsonObject, FactionAbility>> TYPES = Map.of("dodge", DodgeAbility::new);
    private final Map<String, List<FactionAbility>> assignments;

    public FactionAbilities(JsonObject config) {
        JsonObject definitions = object(config, "definitions"), assigned = object(config, "assignments");
        Map<String, FactionAbility> prepared = new java.util.LinkedHashMap<>();
        for (var entry : definitions.entrySet()) {
            JsonObject definition = entry.getValue().getAsJsonObject();
            String type = definition.get("type").getAsString();
            var factory = TYPES.get(type);
            if (factory == null) throw new IllegalArgumentException("Unknown faction ability type: " + type);
            prepared.put(entry.getKey(), factory.apply(definition));
        }
        Map<String, List<FactionAbility>> result = new java.util.LinkedHashMap<>();
        for (var entry : assigned.entrySet()) {
            var abilities = new java.util.LinkedHashSet<FactionAbility>();
            for (var name : entry.getValue().getAsJsonArray()) {
                FactionAbility ability = prepared.get(name.getAsString());
                if (ability == null) throw new IllegalArgumentException("Unknown faction ability: " + name);
                abilities.add(ability);
            }
            result.put(entry.getKey(), List.copyOf(abilities));
        }
        assignments = Map.copyOf(result);
    }
    private static JsonObject object(JsonObject value, String key) {
        return value.has(key) ? value.getAsJsonObject(key) : new JsonObject();
    }
    List<FactionAbility> forFaction(String id) { return assignments.getOrDefault(id, List.of()); }
    public static String describe(Human h) {
        var abilities = UnifiedConfig.get().factionAbilities().forFaction(HumanFactions.id(h));
        return abilities.isEmpty() ? "未分配阵营能力" : abilities.stream().map(a -> a.describe(h))
                .collect(java.util.stream.Collectors.joining("；"));
    }
    public static void incoming(LivingAttackEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Human h) || h.level().isClientSide
                || !h.isAlive() || h.isRemoved() || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0
                || h.isInvulnerableTo(event.getSource()) || !HumanFactions.enabled()) return;
        var hit = new FactionAbility.IncomingAttack(h, event.getSource(), event.getAmount());
        for (FactionAbility ability : UnifiedConfig.get().factionAbilities().forFaction(HumanFactions.id(h))) {
            if (ability.evade(hit)) { event.setCanceled(true); return; }
        }
    }
}
