package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Per-piece probability, budget and compatibility; no table-enchantment or minimum-level second pass. */
final class FactionEquipmentEnchantments {
    private final FactionEquipmentConfig.Profile profile;
    private final JsonObject config;
    private final Map<String,List<Enchantment>> applicable = new HashMap<>();
    private static final Map<String,Integer> VANILLA_COSTS = vanillaCosts();

    FactionEquipmentEnchantments(FactionEquipmentConfig.Profile profile) {
        this.profile=profile; config=FactionEquipmentConfig.object(profile.data,"enchant");
        validateKeys(FactionEquipmentConfig.object(config,"bias"));
        validateKeys(FactionEquipmentConfig.object(config,"cost_overrides"));
        for (var value:FactionEquipmentPools.list(config,"blacklist")) {
            if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) validate(value.getAsString());
            else FactionEquipmentConfig.warn(value.toString(),"Invalid enchantment blacklist entry; ignoring");
        }
    }

    ItemStack apply(Human human, ItemStack stack, EquipmentSlot slot) {
        if(stack.isEmpty()) return stack;
        boolean armor=slot.getType()==EquipmentSlot.Type.ARMOR;
        double chance=FactionEquipmentConfig.number(config,armor?"armor_chance":"weapon_chance",0,0,1);
        if(human.getRandom().nextDouble()<chance) {
            int remaining=(int)FactionEquipmentConfig.number(config,"budget",0,0,10000);
            int maximum=(int)FactionEquipmentConfig.number(config,"max_enchants",0,0,32);
            Map<Enchantment,Integer> selected=new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
            // Existing item-provided enchantments count toward both caps and costs.
            for(var entry:selected.entrySet()) remaining-=cost(entry.getKey())*entry.getValue();
            List<Enchantment> initial=candidates(stack);
            while(remaining>0 && selected.size()<maximum) {
                List<Enchantment> available=new ArrayList<>();
                List<Double> weights=new ArrayList<>();
                for(Enchantment enchantment:initial) {
                    if(selected.containsKey(enchantment) || !applicable(enchantment,stack) || cost(enchantment)>remaining
                            || selected.keySet().stream().anyMatch(old->!enchantment.isCompatibleWith(old)||!old.isCompatibleWith(enchantment))) continue;
                    double weight=FactionEquipmentConfig.number(FactionEquipmentConfig.object(config,"bias"),id(enchantment),1,0,1e6);
                    if(weight>0) { available.add(enchantment); weights.add(weight); }
                }
                int index=FactionEquipmentPolicy.choose(weights.stream().mapToDouble(Double::doubleValue).toArray(),human.getRandom().nextDouble());
                if(index<0) break;
                Enchantment chosen=available.get(index);
                int cost=cost(chosen), affordable=Math.min(chosen.getMaxLevel(),remaining/cost);
                if(affordable<1) break;
                int rank=profile.rank.equals("roamer")?0:Integer.parseInt(profile.rank.substring(4));
                int level=FactionEquipmentPolicy.enchantmentLevel(rank,chosen.getMaxLevel(),remaining,cost,
                        human.getRandom().nextDouble(),human.getRandom().nextDouble());
                selected.put(chosen,level); remaining-=cost*level;
            }
            EnchantmentHelper.setEnchantments(selected,stack);
        }
        // The independent curse channel intentionally does not consult allow_curses.
        curse(human,stack,Enchantments.VANISHING_CURSE,profile.rank.equals("tier3")?0:profile.number("vanishing_chance",0,0,1));
        curse(human,stack,Enchantments.BINDING_CURSE,profile.rank.equals("tier3")?0:profile.number("binding_chance",0,0,1));
        if(SpartanEquipmentCompat.isShield(stack)) stack.getOrCreateTag().putBoolean("hostile_humans_unified:durability_enchantment_rolled",true);
        return stack;
    }

    private List<Enchantment> candidates(ItemStack stack) {
        String key=ForgeRegistries.ITEMS.getKey(stack.getItem())+"/"+GunSupport.get().gunId(stack);
        return applicable.computeIfAbsent(key,ignored->{
            List<Enchantment> result=new ArrayList<>();
            for(Enchantment enchantment:ForgeRegistries.ENCHANTMENTS) {
                ResourceLocation id=ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                if(id==null || (!UnifiedConfig.bool(config,"include_modded",true)&&!id.getNamespace().equals("minecraft"))
                        || (enchantment.isTreasureOnly()&&!UnifiedConfig.bool(config,"allow_treasure",false))
                        || (enchantment.isCurse()&&(!UnifiedConfig.bool(config,"allow_curses",false)||profile.rank.equals("tier3"))) || blacklisted(enchantment)
                        || !applicable(enchantment,stack)) continue;
                result.add(enchantment);
            }
            return List.copyOf(result);
        });
    }
    private boolean blacklisted(Enchantment enchantment) {
        for(var entry:FactionEquipmentPools.list(config,"blacklist")) if(entry.isJsonPrimitive()&&entry.getAsString().equals(id(enchantment))) return true;
        return false;
    }
    private int cost(Enchantment enchantment) {
        int fallback=VANILLA_COSTS.getOrDefault(id(enchantment),2);
        double configured=FactionEquipmentConfig.number(FactionEquipmentConfig.object(config,"cost_overrides"),id(enchantment),fallback,1,10000);
        return Math.max(1,(int)configured);
    }
    private void curse(Human human,ItemStack stack,Enchantment curse,double chance) {
        if(human.getRandom().nextDouble()>=chance || !applicable(curse,stack) || blacklisted(curse)) return;
        for(Enchantment existing:EnchantmentHelper.getEnchantments(stack).keySet())
            if(existing==curse || !existing.isCompatibleWith(curse) || !curse.isCompatibleWith(existing)) return;
        stack.enchant(curse,1);
    }
    private static boolean applicable(Enchantment enchantment,ItemStack stack) {
        return enchantment.canEnchant(stack) && stack.canApplyAtEnchantingTable(enchantment);
    }
    private static String id(Enchantment enchantment) { return String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(enchantment)); }
    private static void validateKeys(JsonObject object) { for(String key:object.keySet()) validate(key); }
    private static void validate(String key) {
        if(key.startsWith("#")) { FactionEquipmentConfig.warn(key,"Enchantment tags are unavailable in the current 1.20.1 registry; use explicit enchantment IDs"); return; }
        ResourceLocation id=ResourceLocation.tryParse(key);
        if(id==null || !ForgeRegistries.ENCHANTMENTS.containsKey(id)) FactionEquipmentConfig.warn(key,"Unknown enchantment ID; ignoring");
    }
    private static Map<String,Integer> vanillaCosts() {
        Map<String,Integer> result=new HashMap<>();
        for(String name:List.of("sharpness","smite","bane_of_arthropods","knockback","looting","sweeping","unbreaking","protection",
                "fire_protection","blast_protection","projectile_protection","respiration","aqua_affinity","feather_falling","depth_strider",
                "frost_walker","soul_speed","power","punch","quick_charge","piercing","loyalty","impaling")) result.put("minecraft:"+name,1);
        for(String name:List.of("fire_aspect","thorns","swift_sneak","flame","channeling")) result.put("minecraft:"+name,2);
        result.put("minecraft:mending",4); result.put("minecraft:infinity",5); result.put("minecraft:multishot",4); result.put("minecraft:riptide",3);
        return Map.copyOf(result);
    }
}
