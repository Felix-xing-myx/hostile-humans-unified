package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.inventory.HumanLootManager;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.Locale;

/** Server-side item identity, recomputed for the current weapon; never an entity profession. */
public final class EquipmentArchetypes {
    private EquipmentArchetypes() {}
    public record Identity(String category, String family, String backend, boolean core, boolean enabled) {}

    public static Identity describe(Human human, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new Identity("empty", "other", "", false, false);
        var profile = FactionEquipmentConfig.of(human);
        if (GunSupport.isGunItem(stack)) {
            var support = GunSupport.get();
            String family = FactionEquipmentGuns.category(new GunSupport.GunChoice(support.backendId(stack),
                    support.gunId(stack)), support.gunType(stack), profile);
            boolean core = !profile.faction.equals("roamer") && !profile.faction.equals("neutral")
                    && !family.equals("other") && FactionEquipmentConfig.number(
                    FactionEquipmentConfig.object(profile.data, "gun_core"), family, 0, 0, 1e6) > 0;
            return new Identity("gun", family, support.backendId(stack), core, support.isGun(stack));
        }
        String category = SpartanEquipmentCompat.isShield(stack) ? "shield"
                : RangedWeaponCustody.isBowOrCrossbow(stack) || stack.getItem() instanceof TridentItem ? "ranged"
                : HumanLootManager.isDedicatedMeleeWeapon(stack) ? "melee" : "other";
        if(category.equals("other")) return new Identity("other","other","",false,false);
        String family = family(stack, category, profile);
        var weights=FactionEquipmentConfig.object(profile.data,category + "_archetype_weights");
        boolean core = FactionEquipmentConfig.number(weights,family,0,0,1e6)>0
                || family.equals("buckler")&&FactionEquipmentConfig.number(weights,"small",0,0,1e6)>0;
        return new Identity(category, family, "", core, true);
    }

    static String family(ItemStack stack, String category, FactionEquipmentConfig.Profile profile) {
        var mapping = FactionEquipmentConfig.object(profile.data, "equipment_archetypes");
        var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        var grouped=FactionEquipmentConfig.object(mapping,category);
        // Grouped mappings follow the documented category/family/selector schema: tags before IDs.
        for(boolean tags:new boolean[]{true,false}) for(var entry:grouped.entrySet()) {
            if(!entry.getValue().isJsonArray()) continue;
            for(var selector:entry.getValue().getAsJsonArray()) {
                String text=selector.getAsString();
                if(tags&&text.startsWith("#")) {
                    var tag=ResourceLocation.tryParse(text.substring(1));
                    if(tag!=null&&stack.is(TagKey.create(Registries.ITEM,tag))) return canonical(entry.getKey());
                } else if(!tags&&!text.startsWith("#")&&id!=null&&text.equals(id.toString())) return canonical(entry.getKey());
            }
        }
        if (id != null && mapping.has(id.toString())) return canonical(mapping.get(id.toString()).getAsString());
        for (var entry : mapping.entrySet()) if (entry.getKey().startsWith("#")) {
            var tag = ResourceLocation.tryParse(entry.getKey().substring(1));
            if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return canonical(entry.getValue().getAsString());
        }
        // Public item tags precede ID heuristics, avoiding mandatory optional-mod class linkage.
        for (var tag : stack.getTags().toList()) {
            String family = named(tag.location().getPath(), category);
            if (!family.equals("other")) return family;
        }
        String inferred = named(id == null ? "" : id.getPath(), category);
        if (!inferred.equals("other")) return inferred;
        if (category.equals("ranged")) {
            if (stack.getItem() instanceof CrossbowItem) return "crossbow";
            if (stack.getItem() instanceof BowItem) return "bow";
            if (stack.getItem() instanceof TridentItem) return "trident";
        } else if (category.equals("melee")) {
            if (stack.getItem() instanceof AxeItem) return "axe";
            if (stack.getItem() instanceof SwordItem) return "sword";
        } else if (category.equals("shield")) {
            return stack.getMaxDamage() >= 450 ? "tower" : stack.getMaxDamage() < 200 ? "buckler" : "round";
        }
        FactionEquipmentConfig.warn("archetype/" + id, "No reliable equipment family; using other");
        return "other";
    }

    static String canonical(String family) { return family.equals("small")?"buckler":family; }

    private static String named(String raw, String category) {
        String name = raw.toLowerCase(Locale.ROOT);
        if (category.equals("shield")) {
            if (has(name, "tower", "pavise")) return "tower";
            if (has(name, "buckler", "small_shield")) return "buckler";
            if (has(name, "round", "heater", "kite")) return "round";
        } else if (category.equals("ranged")) {
            if (name.contains("crossbow")) return "crossbow";
            if (name.contains("bow")) return "bow";
            if (name.contains("trident")) return "trident";
        } else {
            if (has(name, "dagger", "knife")) return "dagger";
            if (has(name, "spear", "pike", "halberd", "glaive", "lance", "polearm", "quarterstaff")) return "spear";
            if (has(name, "hammer", "mace", "club", "warhammer")) return "mace";
            if (has(name, "axe", "hatchet", "tomahawk")) return "axe";
            if (has(name, "sword", "saber", "sabre", "rapier", "katana")) return "sword";
        }
        return "other";
    }
    private static boolean has(String name, String... parts) {
        return List.of(parts).stream().anyMatch(name::contains);
    }
}
