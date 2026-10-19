package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Native metadata -> shared category -> faction factor, without item-name guesses or registry scans. */
public final class GunSpecialization {
    public enum Category {
        PISTOL("pistol"), SMG("smg"), RIFLE("rifle"), SHOTGUN("shotgun"),
        SNIPER("sniper"), MACHINE_GUN("machine_gun"), HEAVY("heavy"), OTHER("other");
        private final String key;
        Category(String key) { this.key = key; }
        public String key() { return key; }
    }
    private GunSpecialization() {}
    public static Category category(ItemStack weapon) { return category(GunSupport.get().gunType(weapon)); }
    static Category category(String nativeType) {
        return switch (nativeType == null ? "" : nativeType.toLowerCase(Locale.ROOT)) {
            case "pistol", "handgun", "magnum" -> Category.PISTOL;
            case "smg", "submachine_gun" -> Category.SMG;
            case "rifle", "assault_rifle" -> Category.RIFLE;
            case "shotgun" -> Category.SHOTGUN;
            case "sniper", "sniper_rifle", "snipers_rifle" -> Category.SNIPER;
            case "mg", "lmg", "machine_gun", "minigun" -> Category.MACHINE_GUN;
            case "heavy", "launcher", "grenade_launcher", "rocket_launcher" -> Category.HEAVY;
            default -> Category.OTHER;
        };
    }
    /** The existing composite gun dimension remains a separate multiplier. */
    public static double damageMultiplier(Human human, ItemStack weapon) {
        if (weapon.isEmpty() || !GunSupport.get().isGun(weapon)) return 1;
        return categoryMultipliers(human).get(category(weapon).key());
    }
    public static Map<String, Double> categoryMultipliers(Human human) {
        var definition = FactionDefinitions.find(HumanFactions.id(human));
        return !SpecialtyConfig.get().enabled() || definition == null ? neutral() : definition.gunSpecialization();
    }
    static Map<String, Double> parse(JsonElement input) {
        JsonObject data = input == null ? new JsonObject() : input.getAsJsonObject();
        for (String key : data.keySet())
            if (java.util.Arrays.stream(Category.values()).noneMatch(c -> c.key().equals(key)))
                throw new IllegalArgumentException("Unknown gun specialization category " + key);
        Map<String, Double> raw = new LinkedHashMap<>();
        double sum = 0;
        for (Category category : Category.values()) {
            double value = data.has(category.key()) ? data.get(category.key()).getAsDouble() : 1;
            if (!Double.isFinite(value) || value < .1 || value > 4)
                throw new IllegalArgumentException("Invalid gun specialization " + category.key());
            if (category == Category.OTHER && value != 1)
                throw new IllegalArgumentException("Unknown weapon specialization must remain neutral");
            raw.put(category.key(), value);
            if (category != Category.OTHER) sum += value;
        }
        double mean = sum / (Category.values().length - 1);
        raw.replaceAll((key, value) -> key.equals("other") ? 1 : value / mean);
        return Collections.unmodifiableMap(raw);
    }
    private static Map<String, Double> neutral() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Category category : Category.values()) values.put(category.key(), 1.0);
        return Collections.unmodifiableMap(values);
    }
}
