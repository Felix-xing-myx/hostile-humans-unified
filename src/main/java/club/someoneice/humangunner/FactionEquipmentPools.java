package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Cached classification of explicit candidates only. Never scans the item registry. */
final class FactionEquipmentPools {
    private record Candidate<T>(T value, int band, int toughness, double metric) { }
    private final List<Candidate<ConfiguredEquipmentLoadout.ItemEntry>> melee = new ArrayList<>(), ranged = new ArrayList<>(), shields = new ArrayList<>();
    private final List<Candidate<ConfiguredEquipmentLoadout.ArmorSetEntry>> armor = new ArrayList<>();
    private final FactionEquipmentConfig.Profile profile;
    private final ConfiguredEquipmentLoadout loadout;

    FactionEquipmentPools(ConfiguredEquipmentLoadout loadout, FactionEquipmentConfig.Profile profile) {
        this.profile = profile; this.loadout=loadout;
        Map<ResourceLocation,ConfiguredEquipmentLoadout.ItemEntry> items = new LinkedHashMap<>();
        for (var pool : List.of(loadout.mainhand,loadout.inventory,loadout.bonusMainhand,loadout.rangedMainhand,loadout.offhand,loadout.shields))
            for (var item : pool.entries()) if (item.weight()>0 && item.available() && allowed(item.id(),false)) items.putIfAbsent(item.id(),item);
        JsonObject itemPool = FactionEquipmentConfig.object(profile.data,"item_pool");
        for (JsonElement extra : list(itemPool,"extra")) try {
            ResourceLocation id = ResourceLocation.tryParse(extra.getAsString());
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                FactionEquipmentConfig.warn(extra.toString(),"Skipping missing explicit equipment item"); continue;
            }
            if (allowed(id,true)) items.put(id,new ConfiguredEquipmentLoadout.ItemEntry(id,1,null,1,Map.of()));
        } catch (RuntimeException invalid) { FactionEquipmentConfig.warn(extra.toString(),"Invalid explicit equipment ID"); }
        for (var entry : items.values()) {
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(entry.id()));
            if (GunSupport.isGunItem(stack)) continue;
            if (SpartanEquipmentCompat.isShield(stack)) {
                if (stack.getMaxDamage()<=0) { FactionEquipmentConfig.warn(entry.id().toString(),"Cannot classify non-damageable shield; skipping"); continue; }
                shields.add(new Candidate<>(entry,FactionEquipmentPolicy.band(stack.getMaxDamage(),profile.bands("shield_durability",200,450,1000),true),0,stack.getMaxDamage()));
            } else if (RangedWeaponCustody.isBowOrCrossbow(stack) || HumanUtil.isTrident(stack)) {
                // No common reliable projectile damage API: explicitly use the documented durability axis.
                ranged.add(new Candidate<>(entry,FactionEquipmentPolicy.band(stack.getMaxDamage(),profile.bands("ranged_durability",300,500,900),true),0,stack.getMaxDamage()));
            } else if (HumanLootManager.isDedicatedMeleeWeapon(stack)) {
                double damage=attribute(stack,EquipmentSlot.MAINHAND,Attributes.ATTACK_DAMAGE,1);
                if (Double.isFinite(damage)) melee.add(new Candidate<>(entry,FactionEquipmentPolicy.band(damage,profile.bands("melee_damage",4,6,8,11),false),0,damage));
            }
        }
        Set<String> known = new HashSet<>();
        for (var set : loadout.armorSets.entries()) addArmor(set,known,false);
        // Extra armor only forms a set when the same registered material has exactly one candidate per slot.
        Map<Object,Map<EquipmentSlot,List<ResourceLocation>>> extraArmor = new LinkedHashMap<>();
        for (var entry : items.values()) if (ForgeRegistries.ITEMS.getValue(entry.id()) instanceof ArmorItem item)
            extraArmor.computeIfAbsent(List.of(entry.id().getNamespace(),item.getMaterial()),ignored->new EnumMap<>(EquipmentSlot.class))
                    .computeIfAbsent(item.getEquipmentSlot(),ignored->new ArrayList<>()).add(entry.id());
        for (var slots : extraArmor.values()) if (List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)
                .stream().allMatch(slot->slots.containsKey(slot) && slots.get(slot).size()==1))
            addArmor(new ConfiguredEquipmentLoadout.ArmorSetEntry(1,null,slots.get(EquipmentSlot.HEAD).get(0),slots.get(EquipmentSlot.CHEST).get(0),
                    slots.get(EquipmentSlot.LEGS).get(0),slots.get(EquipmentSlot.FEET).get(0)),known,true);
        HumanGunner.LOGGER.debug("Equipment A×T matrix rank={} faction={}: {}",profile.rank,profile.faction,
                armor.stream().map(c->"A"+(c.band+1)+"/T"+c.toughness).toList());
    }

    boolean allowed(ResourceLocation id, boolean extra) {
        if (id==null || id.equals(new ResourceLocation("minecraft:air"))) return false;
        JsonObject pool=FactionEquipmentConfig.object(profile.data,"item_pool");
        if (!extra && !UnifiedConfig.bool(pool,"integrations",true) && !id.getNamespace().equals("minecraft")) return false;
        for (var deny : list(pool,"deny")) try {
            String text=deny.getAsString();
            if (text.equals(id.toString()) || text.equals(id.getNamespace()) || text.equals(id.getNamespace()+":*")) return false;
        } catch (RuntimeException invalid) { FactionEquipmentConfig.warn(deny.toString(),"Invalid equipment deny entry"); }
        return true;
    }

    private void addArmor(ConfiguredEquipmentLoadout.ArmorSetEntry set, Set<String> known, boolean extra) {
        if (set.weight()<=0 || set.head()==null || set.chest()==null || set.legs()==null || set.feet()==null || !set.available()) return;
        if (!List.of(set.head(),set.chest(),set.legs(),set.feet()).stream().allMatch(id->allowed(id,extra))) return;
        String key=set.head()+"/"+set.chest()+"/"+set.legs()+"/"+set.feet();
        if (!known.add(key)) return;
        double points=0,toughness=0;
        ResourceLocation[] ids={set.head(),set.chest(),set.legs(),set.feet()};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for (int i=0;i<4;i++) {
            ItemStack stack=new ItemStack(ForgeRegistries.ITEMS.getValue(ids[i]));
            points+=attribute(stack,slots[i],Attributes.ARMOR,0);
            toughness+=attribute(stack,slots[i],Attributes.ARMOR_TOUGHNESS,0);
        }
        if (!Double.isFinite(points) || !Double.isFinite(toughness)) return;
        armor.add(new Candidate<>(set,FactionEquipmentPolicy.band(points,profile.bands("armor_points",7,11,15,19),false),
                FactionEquipmentPolicy.band(toughness,profile.bands("toughness",0,4,8),false),points));
    }

    static double attribute(ItemStack stack, EquipmentSlot slot, Attribute attribute, double base) {
        var modifiers=stack.getAttributeModifiers(slot).get(attribute);
        double addition=0,multiplyBase=0,multiplyTotal=1;
        for (var modifier : modifiers) switch (modifier.getOperation()) {
            case ADDITION -> addition+=modifier.getAmount();
            case MULTIPLY_BASE -> multiplyBase+=modifier.getAmount();
            case MULTIPLY_TOTAL -> multiplyTotal*=1+modifier.getAmount();
        }
        double value=base+addition;
        return (value+value*multiplyBase)*multiplyTotal;
    }

    ConfiguredEquipmentLoadout.ItemEntry melee(Human human) {
        return !profile.factionBands()?legacyMelee(loadout.mainhand,human):select(melee,"melee_band_weights",5,human,false);
    }
    ConfiguredEquipmentLoadout.ItemEntry reserveMelee(Human human) {
        return !profile.factionBands()?legacyMelee(loadout.inventory,human):select(melee,"melee_band_weights",5,human,false);
    }
    private ConfiguredEquipmentLoadout.ItemEntry legacyMelee(ConfiguredEquipmentLoadout.Pool<ConfiguredEquipmentLoadout.ItemEntry> pool,Human human) {
        return pool.roll(human.getRandom(),entry->allowed(entry.id(),false)&&HumanLootManager.isDedicatedMeleeWeapon(new ItemStack(ForgeRegistries.ITEMS.getValue(entry.id()))));
    }
    ConfiguredEquipmentLoadout.ItemEntry ranged(Human human) {
        return !profile.factionBands()?loadout.rangedMainhand.roll(human.getRandom(),entry->allowed(entry.id(),false))
                :select(ranged,"ranged_band_weights",4,human,false);
    }
    ConfiguredEquipmentLoadout.ItemEntry shield(Human human) { return select(shields,"shield_band_weights",4,human,false); }
    ConfiguredEquipmentLoadout.ArmorSetEntry armor(Human human) { return select(armor,"armor_band_weights",5,human,true); }

    private <T> T select(List<Candidate<T>> values,String key,int count,Human human,boolean conditionToughness) {
        if (values.isEmpty()) return null; // The original rank pools were already considered, with deny applied.
        List<Candidate<T>> available=values;
        if (profile.factionBands()) {
            int requested=profile.pick(key,count,human);
            if (requested<0) return null;
            boolean[] present=new boolean[count];
            for (var value:values) present[value.band]=true;
            int actual=FactionEquipmentPolicy.nearest(requested,present);
            if (actual!=requested) FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/"+key+"/"+requested+"/"+actual,
                    "Empty equipment band; using nearest available band");
            available=values.stream().filter(c->c.band==actual).toList();
            if (conditionToughness) {
                double[] weights=profile.weights("toughness_band_weights",4);
                boolean[] toughnessPresent=new boolean[4];
                for(var value:available) toughnessPresent[value.toughness]=true;
                for(int i=0;i<4;i++) if(!toughnessPresent[i]) weights[i]=0;
                int chosen=FactionEquipmentPolicy.choose(weights,human.getRandom().nextDouble());
                if(chosen<0) {
                    // A real in-band value, favoring the lowest; never synthesize A/T combinations.
                    for(int i=0;i<4;i++) if(toughnessPresent[i]) { chosen=i; break; }
                    FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/toughness/"+actual,"Zero available toughness weights; choosing lowest real toughness");
                }
                int selected=chosen;
                available=available.stream().filter(c->c.toughness==selected).toList();
            }
        }
        // Finite candidate median works for both bounded and unbounded terminal bands.
        double[] metrics=available.stream().mapToDouble(c->c.metric).sorted().toArray();
        double median=metrics.length%2==1?metrics[metrics.length/2]:(metrics[metrics.length/2-1]+metrics[metrics.length/2])/2;
        double[] weights=new double[available.size()];
        for(int i=0;i<weights.length;i++) weights[i]=profile.factionBands()?1/(1+Math.abs(available.get(i).metric-median))
                : ((ConfiguredEquipmentLoadout.WeightedEntry)available.get(i).value).weight();
        int chosen=FactionEquipmentPolicy.choose(weights,human.getRandom().nextDouble());
        return chosen<0?null:available.get(chosen).value;
    }

    static JsonArray list(JsonObject object,String key) {
        return object.has(key)&&object.get(key).isJsonArray()?object.getAsJsonArray(key):new JsonArray();
    }
}
