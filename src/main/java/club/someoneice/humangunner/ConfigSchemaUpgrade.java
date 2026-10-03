package club.someoneice.humangunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

/** Additive schema upgrade: settings/custom notes stay intact; obsolete stock guidance is refreshed. */
final class ConfigSchemaUpgrade {
    private static final JsonArray OLD_ROAMER_ARMOR = oldRoamerArmor();
    // Exact UTF-8 hashes of the six packaged equipment descriptions, not
    // prefix matching: user-edited notes must never be overwritten.
    private static final Set<String> OLD_EQUIPMENT_GUIDANCE = Set.of(
            "77AFCA0C51D9FFF11EE4F82F41B835CDF34EDCBA354B02AC99129335571A36BB",
            "DAEF415DE9BA74404AA4519BB8FFDC4C23D739F86486BAC800706B793AA94A77",
            "E762E384F70DDBCF9D53E5F7A081F68C81BC86F8F08403CBC642153545EE11FA",
            "D20BD5A02FF3D5FA87A7E6121EF06721CF6958393941C72262F0BBB67B311368",
            "97CF323ED5A6FF06A5ACD2987423DF68AD53260A1B860515BEDCDC40B616720F",
            "1F8BED87A5FB9091F659536407A6E36E84255CC8F65871924F934370E7A36365");
    private static final Set<String> OLD_STOCK_GUIDANCE = Set.of(
            "自然遭遇生成总开关、准入概率、流浪者额外随机判定、遭遇冷却、搜索半径和附近人类密度限制。admission_chance 与 battle_admission_chance 均支持 0–1 的小数：1=100%、0.1=10%、0.01=1%、0.001=0.1%、0=禁止对应遭遇。直接填写 JSON 数字，不加引号或百分号；这不是每 tick 的实际刷新概率。",
            "Controls natural encounters: master toggle, admission probabilities, the extra roamer roll, encounter cooldown, search radii and local human-density scaling. admission_chance and battle_admission_chance accept decimals from 0 to 1: 1=100%, 0.1=10%, 0.01=1%, 0.001=0.1%, and 0 disables the corresponding encounter. Enter JSON numbers without quotes or a percent sign; these are not actual per-tick spawn probabilities.",
            "普通自然遭遇的概率环节为 min(1, admission_chance × tiers.json 中本阶的 spawn_multiplier)。例如 0.08 × 0.1 = 0.008，即 0.8% 的基础准入机会，之后仍要通过密度、安全位置、冷却和解锁天数等条件；流浪者还额外乘 1/roamer_legacy_roll。自然大型战斗另用 battle_admission_chance，不乘本阶 spawn_multiplier，且仍要通过其原有生成条件。只减某一阶的普通自然生成，改 tiers.json；降低所有普通自然遭遇，改 admission_chance；减少自然大型战斗，改 battle_admission_chance。信号弹/敌对信标召唤不受这些准入概率影响。",
            "Ordinary natural encounters use min(1, admission_chance × that tier's spawn_multiplier in tiers.json) for their admission roll. For example, 0.08 × 0.1 = 0.008, or 0.8% base admission, followed by density, safe-position, cooldown, unlocked-day and other checks; roamers also multiply by 1/roamer_legacy_roll. Automatic large battles instead use battle_admission_chance without the tier spawn_multiplier and must still pass their original conditions. Edit tiers.json to reduce one tier's ordinary natural spawns, admission_chance to reduce all ordinary natural encounters, or battle_admission_chance to reduce automatic large battles. Flare/beacon summons are unaffected by these admission probabilities.",
            "spawn_multiplier 是本阶普通自然生成/区块初始自然生成的准入倍率，不是直接概率；允许 0–100 的整数或小数。相对于本项设为 1 时，0.5 保留 50%、0.1 保留 10%、0.01 保留 1% 的基础准入机会；可直接填写 0.005 等更小的数值，不加引号或百分号。0 禁止本阶普通自然生成。它与 spawning.json 的 admission_chance 相乘，仍受天数、地形、密度等条件限制；流浪者还经过额外的 1/roamer_legacy_roll 判定。自然大型战斗由 battle_admission_chance 单独控制，不乘此倍率；援军信号弹和敌对信标不受此项影响。",
            "spawn_multiplier scales admission for this tier's ordinary and chunk-generation natural spawns; it is not a direct probability. Integers and decimals from 0 to 100 are accepted. Compared with setting this value to 1, 0.5 retains 50%, 0.1 retains 10%, and 0.01 retains 1% of base admission chances. Smaller values such as 0.005 can be entered directly without quotes or a percent sign. 0 disables this tier's ordinary natural spawns. It multiplies admission_chance in spawning.json and remains subject to day gates, terrain, density and other checks; roamers also pass the extra 1/roamer_legacy_roll roll. Automatic large battles use battle_admission_chance separately, not this multiplier. Reinforcement flares and hostile beacons are unaffected.",
            "该阶相对于默认值的自然生成倍率；0 表示不自然生成。",
            "Natural-spawn multiplier for this tier; 0 disables its natural spawning.",
            "自然遭遇生成总开关、遭遇出现概率、流浪者旧式随机判定、遭遇冷却、搜索半径和附近人类密度限制。概率范围为 0 到 1。",
            "Controls natural encounters: master toggle, admission chances, legacy roamer roll, encounter cooldown, search radii and local human-density scaling. Chances range from 0 to 1.",
            "生命值范围以生命值点数计（2 点=1 颗心）；护甲、韧性、击退抗性、跟随范围和基础攻击属性。",
            "Health bounds use health points (2 points = 1 heart); also sets armor, toughness, knockback resistance, follow range and base attack damage.",
            "本阶人类近战、弓箭、三叉戟伤害倍率及受到伤害倍率。",
            "This tier's melee, bow and trident damage multipliers, plus its incoming-damage multiplier.",
            "全局伤害倍率，用于调整各类人类攻击、弹道速度伤害、跳跃攻击，以及人类近战对已驯服女仆造成的伤害。tamed_maid_melee_damage_multiplier 只削减人类对女仆的近战伤害，不控制女仆对人类的伤害。",
            "Global damage multipliers for human attacks, projectile-speed scaling, jump attacks, and human melee damage dealt to tamed maids. tamed_maid_melee_damage_multiplier reduces human melee damage to maids; it does not control maid damage to humans.",
            "TaCZ 人类枪械基础伤害、掠夺者枪械伤害，以及按阶伤害倍率。",
            "Base firearm damage for humans and pillagers, plus per-tier firearm damage multipliers.",
            "shield_damage_multiplier 控制人类举盾格挡 TaCZ 子弹后剩余的生命伤害比例（0.3 表示承受原伤害的 30%，即减伤 70%）；tamed_maid_damage_multiplier 控制已驯服女仆受到的 TaCZ 子弹伤害比例（默认 0.15）。女仆伤害倍率不区分攻击者，所以人类枪械攻击女仆时也会应用；此时还会先应用人类枪械基础倍率和阶级倍率。人类对女仆的近战伤害另由 damage.tamed_maid_melee_damage_multiplier 控制。",
            "shield_damage_multiplier controls the health damage a Human takes after blocking a TaCZ bullet (0.3 means 30% damage remains, i.e. 70% reduction). tamed_maid_damage_multiplier controls TaCZ bullet damage received by tamed maids (default 0.15). It does not filter by attacker, so it also applies when a Human shoots a maid; Human gun base and tier multipliers are applied beforehand. Human melee damage to maids is controlled separately by damage.tamed_maid_melee_damage_multiplier.");

    private ConfigSchemaUpgrade() {}

    static boolean userMap(String path) {
        return path.equals("tacz.gun_whitelist") || path.equals("tacz.hired_gun_additional_whitelist")
                || path.matches("tacz\\.tier_gun_type_weights\\.(roamer|tier1|tier2|tier3)");
    }

    static boolean fill(JsonObject data, JsonObject defaults, String path) {
        boolean changed = false;
        for (var entry : defaults.entrySet()) {
            String key = entry.getKey();
            String child = path.isEmpty() ? key : path + "." + key;
            if (!data.has(key)) {
                data.add(key, entry.getValue().deepCopy());
                changed = true;
            } else if (child.equals("equipment_loadouts.roamer.armor_sets")
                    && data.get(key).equals(OLD_ROAMER_ARMOR)
                    && !data.get(key).equals(entry.getValue())) {
                // Migrate only the exact former packaged pool, not customized
                // weights, entries, empty pools or any other rank's equipment.
                data.add(key, entry.getValue().deepCopy());
                changed = true;
            } else if (obsoleteGuidance(path, key, data.get(key))
                    && !data.get(key).equals(entry.getValue())) {
                // Only exact, known old packaged descriptions are replaced.
                // User-written notes and every gameplay value stay untouched.
                data.add(key, entry.getValue().deepCopy());
                changed = true;
            } else if (!userMap(child) && data.get(key).isJsonObject() && entry.getValue().isJsonObject()) {
                changed |= fill(data.getAsJsonObject(key), entry.getValue().getAsJsonObject(), child);
            }
        }
        return changed;
    }

    private static JsonArray oldRoamerArmor() {
        JsonArray sets = new JsonArray();
        for (String material : List.of("iron", "diamond", "chainmail", "slime", "prismarine", "steampunk")) {
            boolean optional = sets.size() >= 3;
            String prefix = (optional ? "immersive_armors:" : "minecraft:") + material;
            JsonObject set = new JsonObject();
            set.addProperty("weight", material.equals("iron") ? 2 : 1);
            if (optional) set.addProperty("requires_mod", "immersive_armors");
            set.addProperty("head", prefix + "_helmet");
            set.addProperty("chest", prefix + "_chestplate");
            set.addProperty("legs", prefix + "_leggings");
            set.addProperty("feet", prefix + "_boots");
            sets.add(set);
        }
        return sets;
    }

    private static boolean obsoleteGuidance(String path, String key, JsonElement value) {
        if (path.equals("equipment_loadouts") && (key.startsWith("_说明") || key.startsWith("_description"))
                && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            try {
                byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(value.getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return OLD_EQUIPMENT_GUIDANCE.contains(java.util.HexFormat.of().withUpperCase().formatHex(hash));
            } catch (java.security.NoSuchAlgorithmException unavailable) {
                throw new IllegalStateException("Required SHA-256 unavailable", unavailable);
            }
        }
        return (path.equals("damage") || path.equals("tacz") || path.equals("spawning")
                || path.matches("tiers\\.(roamer|tier1|tier2|tier3)\\.(attributes|damage_multipliers|spawning)"))
                && (key.startsWith("_说明") || key.startsWith("_description"))
                && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && OLD_STOCK_GUIDANCE.contains(value.getAsString());
    }

    static boolean aliases(JsonObject root, JsonObject defaults) {
        boolean changed = false;
        JsonObject tacz = UnifiedConfig.object(root, "tacz");
        for (String[] pair : List.of(new String[]{"globe", "tier2_gun_chance"},
                new String[]{"guns", "gun_whitelist"},
                new String[]{"include_unlisted_guns", "auto_add_new_guns"},
                new String[]{"unlisted_gun_weight", "auto_added_gun_weight"},
                new String[]{"excluded_unlisted_gun_types", "excluded_auto_gun_types"})) {
            changed |= promote(tacz, pair[0], tacz, pair[1]);
        }
        JsonObject tiers = UnifiedConfig.object(root, "tiers");
        for (String rank : List.of("roamer", "tier1", "tier2", "tier3")) {
            JsonObject tier = UnifiedConfig.object(tiers, rank);
            JsonObject defaultsTier = UnifiedConfig.object(UnifiedConfig.object(defaults, "tiers"), rank);
            for (String group : List.of("attributes", "damage_multipliers", "combat", "spawning")) {
                for (String key : UnifiedConfig.object(defaultsTier, group).keySet()) {
                    if (!key.startsWith("_") && tier.has(key)) {
                        JsonObject destination = ensureObject(tier, group);
                        if (destination != null) changed |= promote(tier, key, destination, key);
                    }
                }
            }
            for (String legacy : List.of("gun_spread_degrees", "projectile_spread_degrees")) {
                if (!tier.has(legacy)) continue;
                JsonObject weapon = ensureObject(tier, "weapon_spread_degrees");
                if (weapon == null) continue;
                boolean firearm = legacy.equals("gun_spread_degrees");
                JsonObject destination = ensureObject(weapon, firearm ? "firearms" : "projectiles");
                if (destination == null) continue;
                for (String type : firearm ? GunSpreadPolicy.supportedTypes() : List.of("bow", "crossbow", "trident")) {
                    if (destination.has(type)) continue;
                    JsonElement original = tier.get(legacy);
                    try {
                        double value = original.getAsDouble();
                        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite legacy spread");
                        value = Math.max(0, Math.min(45, value));
                        destination.addProperty(type, firearm ? GunSpreadPolicy.legacyAdjustedDegrees(value, type)
                                : GunSpreadPolicy.legacyProjectileDegrees(value, type));
                    } catch (RuntimeException invalid) {
                        destination.add(type, original.deepCopy());
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static JsonObject ensureObject(JsonObject parent, String key) {
        if (!parent.has(key)) parent.add(key, new JsonObject());
        return parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static boolean promote(JsonObject source, String old, JsonObject target, String current) {
        if (!source.has(old) || target.has(current)) return false;
        target.add(current, source.get(old).deepCopy());
        return true;
    }
}
