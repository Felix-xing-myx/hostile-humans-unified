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

/** Cached classification of explicit candidates only. Never scans the item registry. */
final class FactionEquipmentPools {
    private record Candidate<T>(T value, int band, double metric) { }
    private final List<Candidate<ConfiguredEquipmentLoadout.ItemEntry>> melee = new ArrayList<>(), ranged = new ArrayList<>(), shields = new ArrayList<>();
    private final List<Candidate<ConfiguredEquipmentLoadout.ArmorSetEntry>> armor = new ArrayList<>();
    private final FactionEquipmentConfig.Profile profile;
    private record ArmorOverride(ConfiguredEquipmentLoadout.ArmorSetEntry set,double weight) {}
    private final List<ArmorOverride> armorOverrides = new ArrayList<>();

    FactionEquipmentPools(ConfiguredEquipmentLoadout loadout, FactionEquipmentConfig.Profile profile) {
        this.profile = profile;
        for(var element:list(FactionEquipmentConfig.object(profile.data,"faction_armor"),"sets")) try {
            JsonObject row=element.getAsJsonObject();
            if(row.has("weight")&&(!row.get("weight").isJsonPrimitive()||!row.get("weight").getAsJsonPrimitive().isNumber()))
                throw new IllegalArgumentException("numeric weight required");
            double weight=row.has("weight")?row.get("weight").getAsDouble():1;
            if(!Double.isFinite(weight)||weight<=0) throw new IllegalArgumentException("weight");
            ResourceLocation[] ids=new ResourceLocation[4]; int index=0;
            for(String slot:List.of("head","chest","legs","feet")) {
                String text=row.get(slot).getAsString();
                ids[index++]=text.contains(":")?ResourceLocation.tryParse(text):null;
                if(ids[index-1]==null) throw new IllegalArgumentException("missing slot");
            }
            var set=new ConfiguredEquipmentLoadout.ArmorSetEntry(1,row.has("requires_mod")?row.get("requires_mod").getAsString():null,
                    ids[0],ids[1],ids[2],ids[3]);
            if(set.available()&&Arrays.stream(ids).allMatch(id->allowed(id,false))) armorOverrides.add(new ArmorOverride(set,weight));
            else FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/armor_unavailable/"+element,
                    "Faction armor set unavailable (missing mod/item, wrong slot or denied member); skipping whole set");
        } catch(RuntimeException invalid) { FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/armor/"+element,"Invalid complete armor override; skipping whole set"); }
        if(!FactionEquipmentConfig.object(profile.data,"faction_armor").entrySet().isEmpty()&&armorOverrides.isEmpty())
            FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/armor_empty","No valid complete faction armor sets; using rank baseline");
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
                shields.add(new Candidate<>(entry,FactionEquipmentPolicy.band(stack.getMaxDamage(),profile.bands("shield_durability",200,450,1000),true),stack.getMaxDamage()));
            } else if (RangedWeaponCustody.isBowOrCrossbow(stack) || HumanUtil.isTrident(stack)) {
                // No common reliable projectile damage API: explicitly use the documented durability axis.
                ranged.add(new Candidate<>(entry,FactionEquipmentPolicy.band(stack.getMaxDamage(),profile.bands("ranged_durability",300,500,900),true),stack.getMaxDamage()));
            } else if (HumanLootManager.isDedicatedMeleeWeapon(stack)) {
                if (!profile.allowsMeleeFamily(EquipmentArchetypes.family(stack, "melee", profile))) continue;
                double damage=attribute(stack,EquipmentSlot.MAINHAND,Attributes.ATTACK_DAMAGE,1);
                if (Double.isFinite(damage)) melee.add(new Candidate<>(entry,FactionEquipmentPolicy.band(damage,profile.bands("melee_damage",4,6,8,11),false),damage));
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
        HumanGunner.LOGGER.debug("Armor rank={} faction={} available bands={}",profile.rank,profile.faction,
                armor.stream().map(c->"A"+(c.band+1)).toList());
    }

    boolean allowed(ResourceLocation id, boolean extra) { return profile.allowedItem(id,extra); }

    private void addArmor(ConfiguredEquipmentLoadout.ArmorSetEntry set, Set<String> known, boolean extra) {
        if (set.weight()<=0 || set.head()==null || set.chest()==null || set.legs()==null || set.feet()==null || !set.available()) return;
        if (!List.of(set.head(),set.chest(),set.legs(),set.feet()).stream().allMatch(id->allowed(id,extra))) return;
        String key=set.head()+"/"+set.chest()+"/"+set.legs()+"/"+set.feet();
        if (!known.add(key)) return;
        double points=0;
        ResourceLocation[] ids={set.head(),set.chest(),set.legs(),set.feet()};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for (int i=0;i<4;i++) {
            ItemStack stack=new ItemStack(ForgeRegistries.ITEMS.getValue(ids[i]));
            points+=attribute(stack,slots[i],Attributes.ARMOR,0);
        }
        if (!Double.isFinite(points)) return;
        armor.add(new Candidate<>(set,FactionEquipmentPolicy.band(points,profile.bands("armor_points",7,11,15,19),false),points));
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
        // Select doctrine family before hand preference, so rare spear allocations survive.
        return select(melee,"melee_band_weights",5,human,"melee");
    }
    ConfiguredEquipmentLoadout.ItemEntry reserveMelee(Human human) { return melee(human); }
    ConfiguredEquipmentLoadout.ItemEntry oneHandedMelee(Human human) {
        var candidates = melee.stream().filter(candidate -> singleHandedMelee(
                new ItemStack(ForgeRegistries.ITEMS.getValue(candidate.value.id())))).toList();
        return select(candidates,"melee_band_weights",5,human,"melee");
    }
    /** Same verified BC capability as combat. Without that backend only sword-class items are preferred. */
    static boolean singleHandedMelee(ItemStack stack) {
        return HumanLootManager.isDedicatedMeleeWeapon(stack)
                && (BetterCombatMeleeCombat.isConfiguredEnabled()
                    ? BetterCombatMeleeCombat.supportsOneHand(stack) : stack.getItem() instanceof SwordItem);
    }
    ConfiguredEquipmentLoadout.ItemEntry ranged(Human human) { return select(ranged,"ranged_band_weights",4,human,"ranged"); }
    ConfiguredEquipmentLoadout.ItemEntry shield(Human human) { return select(shields,"shield_band_weights",4,human,"shield"); }
    boolean matchesFactionArmor(Human human) {
        for(var override:armorOverrides) {
            var set=override.set();
            ResourceLocation[] ids={set.head(),set.chest(),set.legs(),set.feet()};
            EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
            boolean matches=true;
            for(int i=0;i<slots.length;i++) if(!ids[i].equals(ForgeRegistries.ITEMS.getKey(human.getItemBySlot(slots[i]).getItem()))) matches=false;
            if(matches) return true;
        }
        return false;
    }
    ConfiguredEquipmentLoadout.ArmorSetEntry armor(Human human) {
        if(!armorOverrides.isEmpty()) {
            int selected=FactionEquipmentPolicy.choose(armorOverrides.stream().mapToDouble(ArmorOverride::weight).toArray(),human.getRandom().nextDouble());
            return selected<0?null:armorOverrides.get(selected).set();
        }
        return select(armor,"armor_rank_band_weights",5,human,"");
    }

    private <T> T select(List<Candidate<T>> values,String key,int count,Human human,String category) {
        if(values.isEmpty()) return null;
        int requested=profile.pick(key,count,human);
        if(requested<0) return null;
        boolean[] present=new boolean[count];
        for(var value:values) present[value.band]=true;
        int actual=FactionEquipmentPolicy.nearest(requested,present);
        if(actual!=requested) FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/"+key+"/"+requested+"/"+actual,
                "Empty equipment band; using nearest available band");
        List<Candidate<T>> available=values.stream().filter(c->c.band==actual).toList();
        // Calculate the original band's center before doctrine filters candidates.
        double[] metrics=available.stream().mapToDouble(c->c.metric).sorted().toArray();
        double median=metrics.length%2==1?metrics[metrics.length/2]:(metrics[metrics.length/2-1]+metrics[metrics.length/2])/2;
        boolean coreSelected=false;
        if(!category.isEmpty() && (category.equals("melee")
                || human.getRandom().nextDouble()<profile.number("doctrine_core_chance",0,0,1))) {
            var configured=FactionEquipmentConfig.object(profile.data,category+"_archetype_weights");
            List<String> families=new ArrayList<>(); List<Double> weights=new ArrayList<>();
            List<Candidate<T>> inBand=available;
            for(var entry:configured.entrySet()) {
                double weight=FactionEquipmentConfig.number(configured,entry.getKey(),0,0,1e6);
                if(weight>0 && inBand.stream().anyMatch(c->family(c,category).equals(EquipmentArchetypes.canonical(entry.getKey())))) {
                    families.add(EquipmentArchetypes.canonical(entry.getKey())); weights.add(weight);
                } else if(weight>0) FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/"+category+"/"+actual+"/"+entry.getKey(),
                        "Core family absent in selected band; renormalizing remaining families in the same band");
            }
            int selected=FactionEquipmentPolicy.choose(weights.stream().mapToDouble(Double::doubleValue).toArray(),human.getRandom().nextDouble());
            if(selected>=0) {
                coreSelected=true;
                String family=families.get(selected);
                available=available.stream().filter(c->family(c,category).equals(family)).toList();
                HumanGunner.LOGGER.debug("Equipment core rank={} faction={} category={} band={} family={}",profile.rank,profile.faction,category,actual,family);
            } else FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/"+category+"/"+actual+"/fallback",
                    "No weighted core families in band; using random supplement without changing band");
        }
        // A one-handed axe is not a sword: capability preference must not bypass family selection.
        var reservePolicy = profile.meleeReserves();
        if (category.equals("melee") && reservePolicy != null && reservePolicy.preferOneHanded()) {
            var preferred = available.stream().filter(c -> singleHandedMelee(new ItemStack(
                    ForgeRegistries.ITEMS.getValue(((ConfiguredEquipmentLoadout.ItemEntry)c.value).id())))).toList();
            if (!preferred.isEmpty()) available = preferred;
        }
        double[] weights=new double[available.size()];
        for(int i=0;i<weights.length;i++) weights[i]=1/(1+Math.abs(available.get(i).metric-median));
        int chosen=FactionEquipmentPolicy.choose(weights,human.getRandom().nextDouble());
        if(chosen>=0&&!category.isEmpty()&&!coreSelected) HumanGunner.LOGGER.debug(
                "Equipment supplement rank={} faction={} category={} band={} family={}",
                profile.rank,profile.faction,category,actual,family(available.get(chosen),category));
        return chosen<0?null:available.get(chosen).value;
    }
    private String family(Candidate<?> candidate,String category) {
        var entry=(ConfiguredEquipmentLoadout.ItemEntry)candidate.value;
        return EquipmentArchetypes.family(new ItemStack(ForgeRegistries.ITEMS.getValue(entry.id())),category,profile);
    }

    static JsonArray list(JsonObject object,String key) {
        return object.has(key)&&object.get(key).isJsonArray()?object.getAsJsonArray(key):new JsonArray();
    }
}
