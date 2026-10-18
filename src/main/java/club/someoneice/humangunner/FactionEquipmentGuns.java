package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** All category choices operate on the original, already filtered backend pool. */
final class FactionEquipmentGuns {
    private final FactionEquipmentConfig.Profile profile;
    private final Map<String,Map<GunSupport.GunChoice,Integer>> categories=new LinkedHashMap<>();

    FactionEquipmentGuns(FactionEquipmentConfig.Profile profile) {
        this.profile=profile;
        Map<GunSupport.GunChoice,Integer> available=HumanGunnerConfig.get().availableGuns(profile.rank);
        var catalogs=GunSupport.get().catalogsByBackend();
        for(var entry:available.entrySet()) {
            var choice=entry.getKey();
            if(entry.getValue()<=0 || !allowed(profile,choice.id())) continue;
            String nativeType=catalogs.getOrDefault(choice.backend(),Map.of()).getOrDefault(choice.id(),"other");
            String category=category(choice,nativeType,profile);
            categories.computeIfAbsent(category,ignored->new LinkedHashMap<>()).put(choice,entry.getValue());
        }
    }
    boolean isEmpty() { return categories.isEmpty(); }
    ItemStack roll(Human human) {
        if(categories.isEmpty()) return ItemStack.EMPTY;
        List<String> names=new ArrayList<>(categories.keySet());
        double[] weights=new double[names.size()];
        boolean core=human.getRandom().nextDouble()<profile.number("gun_core_chance",0,0,1);
        if(core) {
            JsonObject configured=FactionEquipmentConfig.object(profile.data,"gun_core");
            for(int i=0;i<names.size();i++) weights[i]=FactionEquipmentConfig.number(configured,names.get(i),0,0,1e6);
            if(Arrays.stream(weights).sum()<=0) {
                core=false;
                FactionEquipmentConfig.warn(profile.rank+"/"+profile.faction+"/gun_core","No loaded core categories; using the complete filtered fallback pool");
            }
        }
        if(!core) {
            boolean global=profile.data.has("gun_fallback_mode")&&profile.data.get("gun_fallback_mode").getAsString().equals("global_weight");
            for(int i=0;i<names.size();i++) weights[i]=global?categories.get(names.get(i)).values().stream().mapToDouble(Integer::doubleValue).sum():1;
        }
        int index=FactionEquipmentPolicy.choose(weights,human.getRandom().nextDouble());
        if(index<0) return ItemStack.EMPTY;
        var guns=categories.get(names.get(index));
        List<GunSupport.GunChoice> choices=new ArrayList<>(guns.keySet());
        boolean uniform=!core&&UnifiedConfig.bool(profile.data,"gun_fallback_uniform",false);
        double[] gunWeights=new double[choices.size()];
        for(int i=0;i<choices.size();i++) gunWeights[i]=uniform?1:guns.get(choices.get(i));
        int gun=FactionEquipmentPolicy.choose(gunWeights,human.getRandom().nextDouble());
        return gun<0?ItemStack.EMPTY:GunSupport.get().createLoadedGun(choices.get(gun));
    }

    private static boolean allowed(FactionEquipmentConfig.Profile profile,ResourceLocation id) {
        JsonObject pool=FactionEquipmentConfig.object(profile.data,"item_pool");
        boolean explicit=false;
        for(var entry:FactionEquipmentPools.list(pool,"extra")) if(entry.isJsonPrimitive()&&entry.getAsString().equals(id.toString())) explicit=true;
        if(!UnifiedConfig.bool(pool,"integrations",true)&&!id.getNamespace().equals("minecraft")&&!explicit) return false;
        for(var entry:FactionEquipmentPools.list(pool,"deny")) if(entry.isJsonPrimitive()) {
            String text=entry.getAsString();
            if(text.equals(id.toString())||text.equals(id.getNamespace())||text.equals(id.getNamespace()+":*")) return false;
        }
        return true;
    }

    private static String category(GunSupport.GunChoice choice,String nativeType,FactionEquipmentConfig.Profile profile) {
        JsonObject mapping=FactionEquipmentConfig.object(profile.data,"gun_category_map");
        if(mapping.has(choice.id().toString())&&mapping.get(choice.id().toString()).isJsonPrimitive())
            return canonical(mapping.get(choice.id().toString()).getAsString());
        // Explicit item ID wins over tags; equal-priority tags are evaluated in config insertion order.
        for(var entry:mapping.entrySet()) if(entry.getKey().startsWith("#")&&entry.getValue().isJsonPrimitive()) {
            ResourceLocation tag=ResourceLocation.tryParse(entry.getKey().substring(1));
            if(tag==null) { FactionEquipmentConfig.warn(entry.getKey(),"Invalid gun category tag"); continue; }
            ItemStack gun=GunSupport.get().createLoadedGun(choice);
            if(!gun.isEmpty()&&gun.is(TagKey.create(Registries.ITEM,tag))) return canonical(entry.getValue().getAsString());
        }
        return canonical(nativeType);
    }
    static String canonical(String nativeType) {
        return switch(nativeType.toLowerCase(Locale.ROOT)) {
            case "pistol","revolver","handgun" -> "pistol";
            case "smg","pdw","submachine_gun" -> "smg";
            case "shotgun" -> "shotgun";
            case "rifle","carbine","assault_rifle" -> "rifle";
            case "mg","lmg","hmg","machine_gun" -> "mg";
            case "sniper","sniper_rifle","anti_material_rifle" -> "sniper";
            case "launcher","rpg","rocket_launcher","grenade_launcher" -> "launcher";
            default -> "other";
        };
    }
}
