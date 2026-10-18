package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.*;
import java.util.*;

/** Single runtime resolver shared by combat, attributes, recovery and server detail snapshots. */
public final class EffectiveAttributes {
    private EffectiveAttributes() { }
    /** Allocation and the independent boss baseline are separate factors. */
    public static double multiplier(Human human, SpecialtyAttributes.Dimension dimension) {
        return SpecialtyAttributes.multiplier(human, dimension) * bossMultiplier(human, dimension);
    }
    public static double bossMultiplier(Human human, SpecialtyAttributes.Dimension dimension) {
        return HumanFactions.bossBase(human, dimension.key());
    }
    public static double healthBase(Human human) { return TierAttributes.of(human).maxHealth() * multiplier(human, SpecialtyAttributes.Dimension.HEALTH); }
    public static double armorBase(Human human) { return TierAttributes.of(human).armor(); }
    public static double toughnessBase(Human human) { return TierAttributes.of(human).armorToughness(); }
    static void applyDefenseMultipliers(Human human) {
        if(human.level().isClientSide) return;
        DefenseAttributes.apply(human.getAttribute(Attributes.ARMOR),DefenseAttributes.ARMOR,multiplier(human,SpecialtyAttributes.Dimension.ARMOR));
        DefenseAttributes.apply(human.getAttribute(Attributes.ARMOR_TOUGHNESS),DefenseAttributes.TOUGHNESS,multiplier(human,SpecialtyAttributes.Dimension.TOUGHNESS));
    }
    public static double knockbackBase(Human human) {
        return Math.max(0, Math.min(1, TierAttributes.of(human).knockbackResistance()
                * multiplier(human, SpecialtyAttributes.Dimension.KNOCKBACK_RESISTANCE) + HumanFactions.bossAdd(human, "knockback_resistance")));
    }
    public static double speedBase(Human human) { return TierAttributes.of(human).baseMovementSpeed() * multiplier(human, SpecialtyAttributes.Dimension.SPEED); }
    public static double detectionBase(Human human) { return TierAttributes.of(human).followRange() * multiplier(human, SpecialtyAttributes.Dimension.DETECTION); }
    public static double meleeMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.MELEE); }
    public static double projectileMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.PROJECTILE); }
    public static double gunMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.GUN); }
    public static double accuracyMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.ACCURACY); }
    public static double spreadMultiplier(Human human) { return 1 / accuracyMultiplier(human); }
    public static double regenerationMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.REGENERATION); }
    /** Base recovery only; callers own health clamps and must not feed the scaled amount back here. */
    public static float healing(Human human, float baseAmount) {
        return baseAmount > 0 && Float.isFinite(baseAmount) ? (float) (baseAmount * regenerationMultiplier(human)) : baseAmount;
    }
    /** Snapshot assembly lives beside the runtime resolver. It performs no writes or entity scans. */
    public static AttributeDetailSnapshot detailSnapshot(Human human) {
        var tier = TierAttributes.of(human);
        var growth = HumanGrowth.readOnlyLayers(human);
        List<AttributeDetailSnapshot.Row> rows = new ArrayList<>(11);
        rows.add(physical(human, SpecialtyAttributes.Dimension.HEALTH, Attributes.MAX_HEALTH, tier.maxHealth(), "hp", growth));
        rows.add(physical(human, SpecialtyAttributes.Dimension.ARMOR, Attributes.ARMOR, tier.armor(), "points", growth));
        rows.add(physical(human, SpecialtyAttributes.Dimension.TOUGHNESS, Attributes.ARMOR_TOUGHNESS, tier.armorToughness(), "points", growth));
        rows.add(physical(human, SpecialtyAttributes.Dimension.KNOCKBACK_RESISTANCE, Attributes.KNOCKBACK_RESISTANCE, tier.knockbackResistance(), "percent", growth));
        rows.add(physical(human, SpecialtyAttributes.Dimension.SPEED, Attributes.MOVEMENT_SPEED, tier.baseMovementSpeed(), "attribute", growth));
        rows.add(physical(human, SpecialtyAttributes.Dimension.DETECTION, Attributes.FOLLOW_RANGE, tier.followRange(), "blocks", growth));
        ItemStack weapon = human.getMainHandItem();
        boolean gun = GunSupport.isGunItem(weapon);
        var melee = SpecialtyAttributes.Dimension.MELEE;
        List<AttributeDetailSnapshot.Layer> layers = allocationLayers(human, melee);
        layers.add(layer("tier_damage", "multiply", tier.meleeDamage()));
        growthDamage(layers, growth);
        double meleeFactor = tier.meleeDamage() * meleeMultiplier(human) * growth.damageMultiplier();
        double meleeBaseline = tier.meleeDamage() * growth.damageMultiplier();
        if (weapon.getItem() instanceof TridentItem) {
            double branch = UnifiedConfig.get().damage("trident_stab_damage_multiplier", .42);
            layers.add(layer("trident_stab", "multiply", branch)); meleeFactor *= branch; meleeBaseline *= branch;
        }
        if (UnifiedConfig.get().vanillaMode()) { layers.add(layer("vanilla_melee", "set", 1)); meleeFactor = 1; meleeBaseline=1; }
        List<AttributeDetailSnapshot.Note> notes = new ArrayList<>();
        notes.add(note("damage_limit", ""));
        if (!gun && !weapon.isEmpty()) notes.add(note("melee_preview", format(human.getAttributeValue(Attributes.ATTACK_DAMAGE) * meleeFactor)));
        else notes.add(note("no_melee_weapon", ""));
        rows.add(row(human, melee, 1, meleeFactor, growth.known() || UnifiedConfig.get().vanillaMode(), meleeBaseline, "factor", layers, notes));

        var gunDimension = SpecialtyAttributes.Dimension.GUN;
        layers = allocationLayers(human, gunDimension);
        double configured = 1;
        notes = new ArrayList<>();
        if (gun) {
            configured = GunSupport.isTaczGunItem(weapon) ? UnifiedConfig.get().tierGunDamage(TierAttributes.key(human))
                    : UnifiedConfig.get().scguns().damageMultiplier() * UnifiedConfig.get().tierScgunsDamage(TierAttributes.key(human));
            layers.add(layer("backend_config", "multiply", configured));
            notes.add(note("gun_native_unknown", ""));
        } else notes.add(note("no_gun", ""));
        layers.add(layer("gun_experience", "multiply", growth.gunDamage()));
        notes.add(note("damage_limit", ""));
        rows.add(row(human, gunDimension, 1, configured * growth.gunDamage() * gunMultiplier(human), growth.known(), configured*growth.gunDamage(), "factor", layers, notes));

        var projectile = SpecialtyAttributes.Dimension.PROJECTILE;
        layers = allocationLayers(human, projectile); growthDamage(layers, growth);
        double branch = 1; notes = new ArrayList<>();
        if (weapon.getItem() instanceof BowItem) { branch = tier.bowDamage(); notes.add(note("branch_bow", "")); }
        else if (weapon.getItem() instanceof CrossbowItem) { branch = tier.crossbowDamage(); notes.add(note("branch_crossbow", "")); }
        else if (weapon.getItem() instanceof TridentItem) { branch = tier.tridentDamage(); notes.add(note("branch_trident", "")); }
        else notes.add(note("no_projectile_weapon", ""));
        layers.add(layer("projectile_branch", "multiply", branch));
        notes.add(note("projectile_native_unknown", "")); notes.add(note("damage_limit", ""));
        rows.add(row(human, projectile, 1, branch * growth.damageMultiplier() * projectileMultiplier(human), growth.known(), branch*growth.damageMultiplier(), "factor", layers, notes));

        var accuracy = SpecialtyAttributes.Dimension.ACCURACY;
        notes = new ArrayList<>(); notes.add(note("inverse_spread", format(spreadMultiplier(human))));
        if (gun) {
            notes.add(note("gun_growth_spread", format(growth.gunSpread())));
            if (!GunSupport.get().ownsShotSpread(weapon))
                notes.add(note("spread_degrees", format(tier.gunSpreadDegrees(GunSupport.get().gunType(weapon)) * growth.gunSpread() * spreadMultiplier(human))));
            else notes.add(note("native_spread", ""));
        } else if (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem || weapon.getItem() instanceof TridentItem) {
            String type = weapon.getItem() instanceof TridentItem ? "trident" : weapon.getItem() instanceof CrossbowItem ? "crossbow" : "bow";
            notes.add(note("spread_degrees", format(tier.projectileSpreadDegrees(type) * spreadMultiplier(human))));
        }
        rows.add(row(human, accuracy, 1, accuracyMultiplier(human), true, 1, "percent", allocationLayers(human, accuracy), notes));
        var regeneration = SpecialtyAttributes.Dimension.REGENERATION;
        rows.add(row(human, regeneration, 1, regenerationMultiplier(human), true, 1, "factor", allocationLayers(human, regeneration),
                List.of(note("healing_example", format(healing(human, 1))), note("healing_limit", ""))));
        var labels = SpecialtyLabels.of(human).stream().map(match -> new AttributeDetailSnapshot.Label(match.id().substring(0, Math.min(64,match.id().length())),
                match.dimensions().stream().map(SpecialtyAttributes.Dimension::key).toList())).toList();
        String name = human.getName().getString(); if (name.length()>128) name=name.substring(0,128);
        String faction=HumanFactions.id(human);if(faction.length()>256)faction="";
        return new AttributeDetailSnapshot(human.getUUID(), human.getId(), name, faction, HumanFactions.isBoss(human),
                SpecialtyConfig.get().enabled(), human.getHealth(), (float) SpecialtyAttributes.budget(human), human.level().getGameTime(), labels, rows);
    }
    private static AttributeDetailSnapshot.Row physical(Human human, SpecialtyAttributes.Dimension dimension, Attribute attribute,
                                                        double base, String unit, HumanGrowth.Layers growth) {
        if(dimension==SpecialtyAttributes.Dimension.ARMOR||dimension==SpecialtyAttributes.Dimension.TOUGHNESS)
            return defense(human,dimension,attribute,base,unit);
        List<AttributeDetailSnapshot.Layer> layers = allocationLayers(human, dimension);
        double expected = base * multiplier(human, dimension), baseline = base;
        if (dimension == SpecialtyAttributes.Dimension.HEALTH) {
            layers.add(layer("health_stage", "multiply", growth.healthStage()));
            layers.add(layer("experience", "multiply", growth.experience()));
            expected *= growth.healthMultiplier(); baseline *= growth.healthMultiplier();
        }
        if (dimension == SpecialtyAttributes.Dimension.KNOCKBACK_RESISTANCE) {
            double add = HumanFactions.bossAdd(human, dimension.key());
            layers.add(layer("boss_add", "add", add)); expected += add;
            double clamped = knockbackBase(human);
            layers.add(layer("physical_limit", "add", clamped - expected)); expected = clamped;
        }
        var live = human.getAttribute(attribute);
        double actualBase = live == null ? expected : live.getBaseValue(), value = live == null ? expected : live.getValue();
        if(dimension==SpecialtyAttributes.Dimension.HEALTH)value=human.getMaxHealth();
        layers.add(layer("base_adjustment", "add", actualBase - expected));
        layers.add(layer("equipment_effects", "add", value - actualBase));
        List<AttributeDetailSnapshot.Note> notes = new ArrayList<>();
        if (dimension == SpecialtyAttributes.Dimension.SPEED) notes.add(note("movement_unit", ""));
        if (!growth.known()) notes.add(note("growth_unknown", ""));
        return row(human, dimension, base, value, true, baseline, unit, layers, notes);
    }
    private static AttributeDetailSnapshot.Row defense(Human human,SpecialtyAttributes.Dimension dimension,Attribute attribute,double base,String unit) {
        var live=human.getAttribute(attribute);
        java.util.UUID owned=dimension==SpecialtyAttributes.Dimension.ARMOR?DefenseAttributes.ARMOR:DefenseAttributes.TOUGHNESS;
        double actualBase=live==null?base:live.getBaseValue();
        double raw=live==null?base:DefenseAttributes.rawWithoutOwned(live,owned);
        double allocated=raw*multiplier(human,dimension);
        double sanitized=attribute.sanitizeValue(allocated);
        double actual=live==null?sanitized:live.getValue();
        List<AttributeDetailSnapshot.Layer> layers=new ArrayList<>();
        layers.add(layer("base_adjustment","add",actualBase-base));
        layers.add(layer("equipment_effects","add",raw-actualBase));
        layers.addAll(allocationLayers(human,dimension));
        layers.add(layer("physical_limit","add",sanitized-allocated));
        if(Math.abs(actual-sanitized)>1e-6) layers.add(layer("defense_live_adjustment","add",actual-sanitized));
        return row(human,dimension,base,actual,true,attribute.sanitizeValue(raw),unit,layers,List.of());
    }
    private static List<AttributeDetailSnapshot.Layer> allocationLayers(Human human, SpecialtyAttributes.Dimension dimension) {
        List<AttributeDetailSnapshot.Layer> result = new ArrayList<>();
        result.add(layer("specialty", "multiply", SpecialtyAttributes.multiplier(human, dimension)));
        result.add(layer("boss_base", "multiply", bossMultiplier(human, dimension)));
        return result;
    }
    private static void growthDamage(List<AttributeDetailSnapshot.Layer> layers, HumanGrowth.Layers growth) {
        layers.add(layer("damage_stage", "multiply", growth.damageStage())); layers.add(layer("experience", "multiply", growth.experience()));
    }
    private static AttributeDetailSnapshot.Row row(Human human, SpecialtyAttributes.Dimension dimension, double base, double value,
            boolean known, double baseline, String unit, List<AttributeDetailSnapshot.Layer> layers, List<AttributeDetailSnapshot.Note> sourceNotes) {
        List<AttributeDetailSnapshot.Note> notes = new ArrayList<>(sourceNotes);
        boolean finite = Double.isFinite(base)&&Double.isFinite(value)&&Double.isFinite(baseline);
        List<AttributeDetailSnapshot.Layer> finiteLayers = new ArrayList<>();
        for(var layer:layers) {
            if(Double.isFinite(layer.value()))finiteLayers.add(layer); else finite=false;
        }
        if (!finite) {
            if(!Double.isFinite(base))base=0;
            if(!Double.isFinite(value))value=0;
            if(!Double.isFinite(baseline))baseline=0;
            known=false;notes.add(note("value_unknown",""));
        }
        try {
            var config = SpecialtyConfig.get(); var definitions = SpecialtyData.get();
            double[] centers = SpecialtyAllocation.center(HumanFactions.preference(human, HumanFactions.isBoss(human)), definitions.costs());
            int i = dimension.ordinal(); double cap = definitions.caps(config.deviationCap())[i];
            double scale = Math.pow(SpecialtyAttributes.budget(human), config.capBudgetExponent());
            double lower = Math.max(config.multiplierMin(), centers[i]*(1-cap)*scale), upper = Math.min(config.multiplierMax(),centers[i]*(1+cap)*scale);
            notes.add(note(lower<=upper?"current_bounds":"bounds_unknown", lower<=upper?format(lower) + " – " + format(upper):""));
        } catch (IllegalArgumentException ignored) { notes.add(note("bounds_unknown", "")); }
        return new AttributeDetailSnapshot.Row(dimension.key(), base, SpecialtyAttributes.storedMultiplier(human,dimension),
                SpecialtyAttributes.multiplier(human,dimension), value, known, baseline, unit, finiteLayers, notes);
    }
    private static AttributeDetailSnapshot.Layer layer(String key,String operation,double value) { return new AttributeDetailSnapshot.Layer(key,operation,value); }
    private static AttributeDetailSnapshot.Note note(String key,String detail) { return new AttributeDetailSnapshot.Note(key,detail); }
    private static String format(double value) { return String.format(Locale.ROOT,"%.4f",value); }
    /** Live physical attributes; combat entries are weapon-independent factors, not predicted damage.
     * UI must add the selected projectile branch / firearm backend config and known weapon base. */
    public static double liveValue(Human human, SpecialtyAttributes.Dimension dimension) {
        return switch (dimension) {
            case HEALTH -> human.getMaxHealth();
            case ARMOR -> human.getAttributeValue(Attributes.ARMOR);
            case TOUGHNESS -> human.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            case KNOCKBACK_RESISTANCE -> human.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            case SPEED -> human.getAttributeValue(Attributes.MOVEMENT_SPEED);
            case DETECTION -> human.getAttributeValue(Attributes.FOLLOW_RANGE);
            case MELEE -> HumanGrowth.multiplier(human) * meleeMultiplier(human) * TierAttributes.of(human).meleeDamage();
            case GUN -> HumanGrowth.gunDamageMultiplier(human) * gunMultiplier(human);
            case PROJECTILE -> HumanGrowth.multiplier(human) * projectileMultiplier(human);
            case ACCURACY -> accuracyMultiplier(human);
            case REGENERATION -> regenerationMultiplier(human);
        };
    }
}
