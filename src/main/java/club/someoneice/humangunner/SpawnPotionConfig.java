package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import java.util.ArrayList;
import java.util.List;

/** Per-rank recovery and tactical reserves; optional registrations resolve at spawn time. */
final class SpawnPotionConfig {
    private static final java.util.Set<String> UNAVAILABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    record Entry(ResourceLocation item, ResourceLocation potion, int weight) {
        ItemStack resolve() {
            var registeredItem = BuiltInRegistries.ITEM.getOptional(item);
            if (potion == null) {
                if (registeredItem.isEmpty()) return ItemStack.EMPTY; // Optional mod absent.
                ItemStack stack = new ItemStack(registeredItem.get());
                var settings = UnifiedConfig.get().scguns();
                return TacticalThrowables.supported(stack) && settings.throwablesEnabled()
                        && settings.allowedThrowables().contains(item.toString()) ? stack : ItemStack.EMPTY;
            }
            var registeredPotion = BuiltInRegistries.POTION.getOptional(potion);
            if (registeredItem.isEmpty() || !(registeredItem.get() instanceof PotionItem)
                    || registeredPotion.isEmpty()) {
                if (UNAVAILABLE.add(item + "/" + potion)) HumanGunner.LOGGER.warn(
                        "Cannot grant configured spawn potion item={} potion={}: unregistered ID or item is not a PotionItem",
                        item, potion);
                return ItemStack.EMPTY;
            }
            return PotionUtils.setPotion(new ItemStack(registeredItem.get()), registeredPotion.get());
        }
    }
    record Pool(int minimum, int maximum, List<Entry> entries) {
        int rollCount(RandomSource random) { return random.nextInt(minimum, maximum + 1); }
    }
    record Granted(int recovery, int tactical) { }
    private record Available(ItemStack stack, int weight, int category) { }
    final boolean enabled;
    final Pool recovery, tactical;

    SpawnPotionConfig(JsonObject root) {
        enabled = UnifiedConfig.bool(root, "enabled", true);
        recovery = parsePool(UnifiedConfig.object(root, "recovery"));
        tactical = parsePool(UnifiedConfig.object(root, "tactical"), true);
    }

    static Pool parsePool(JsonObject root) {
        return parsePool(root, false);
    }

    static Pool parsePool(JsonObject root, boolean tactical) {
        int low = count(root, "count_min"), high = count(root, "count_max");
        List<Entry> entries = new ArrayList<>();
        if (root.has("pool") && root.get("pool").isJsonArray()) {
            int inspected = 0;
            for (var element : root.getAsJsonArray("pool")) {
                if (inspected++ >= 128) break;
                if (!element.isJsonObject()) continue;
                Entry entry = parseEntry(element.getAsJsonObject(), tactical);
                if (entry != null) entries.add(entry);
            }
        }
        return new Pool(Math.min(low, high), Math.max(low, high), List.copyOf(entries));
    }

    private static int count(JsonObject root, String key) {
        try {
            var value = root.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
            return Math.max(0, Math.min(30, value.getAsBigDecimal().intValueExact()));
        } catch (RuntimeException invalid) { return 0; }
    }

    private static Entry parseEntry(JsonObject root, boolean tactical) {
        try {
            String item = root.get("item").getAsString();
            ResourceLocation itemId = ResourceLocation.tryParse(item), potionId = null;
            if (!item.contains(":") || itemId == null) return null;
            if (root.has("potion")) {
                String potion = root.get("potion").getAsString();
                potionId = ResourceLocation.tryParse(potion);
                if (!potion.contains(":") || potionId == null) return null;
            } else if (!tactical) return null;
            if (root.has("weight") && (!root.get("weight").isJsonPrimitive()
                    || !root.get("weight").getAsJsonPrimitive().isNumber())) return null;
            int weight = root.has("weight") ? root.get("weight").getAsBigDecimal().intValueExact() : 1;
            return weight > 0 && weight <= 1_000_000 ? new Entry(itemId, potionId, weight) : null;
        } catch (RuntimeException invalid) { return null; }
    }

    Granted grant(HumanData data, RandomSource random) {
        if (!enabled) return new Granted(0, 0);
        int first = insertPool(data, random, recovery, recovery.rollCount(random));
        int inserted = insertPool(data, random, tactical, tactical.rollCount(random));
        return new Granted(first, inserted);
    }

    Granted grant(com.craftix.hostile_humans.entity.entities.Human human) {
        if(!enabled) return new Granted(0,0);
        var profile=FactionEquipmentConfig.of(human);
        int requestedRecovery=recovery.rollCount(human.getRandom());
        int requestedTactical=tactical.rollCount(human.getRandom());
        int first=insertPool(human.getData(),human.getRandom(),recovery,requestedRecovery,profile,false);
        int second=insertPool(human.getData(),human.getRandom(),tactical,requestedTactical,profile,true);
        human.getPersistentData().putInt("hostile_humans_unified:spawn_potion_shortfall",requestedRecovery+requestedTactical-first-second);
        return new Granted(first,second);
    }

    private static int insertPool(HumanData data, RandomSource random, Pool pool, int requested) {
        return insertPool(data,random,pool,requested,null,false);
    }
    private static int insertPool(HumanData data,RandomSource random,Pool pool,int requested,
            FactionEquipmentConfig.Profile profile,boolean tactical) {
        List<Available> available = new ArrayList<>();
        for(Entry entry:pool.entries) {
            ItemStack stack=entry.resolve();
            int category=category(entry,tactical);
            if(!stack.isEmpty() && (profile==null || category>=0)) available.add(new Available(stack,entry.weight,category));
        }
        if(available.isEmpty()) return 0;
        double[] categories=profile==null?null:profile.weights(tactical?"tactical_category_weights":"recovery_category_weights",tactical?7:3);
        if(categories!=null) for(int i=0;i<categories.length;i++) {
            final int selected=i;
            if(available.stream().noneMatch(e->e.category()==selected)) categories[i]=0;
        }
        int inserted=0;
        for(int i=0;i<requested;i++) {
            List<Available> members=available;
            if(categories!=null) {
                int group=FactionEquipmentPolicy.choose(categories,random.nextDouble());
                if(group<0) break;
                members=available.stream().filter(e->e.category()==group).toList();
            }
            // Strength 2:1; throwable members equal. Existing explicit item weights are authoritative otherwise.
            double[] weights=new double[members.size()];
            for(int j=0;j<weights.length;j++) {
                Available candidate=members.get(j);
                weights[j]=profile!=null&&tactical&&candidate.category()>=5?1:candidate.weight();
            }
            int chosen=FactionEquipmentPolicy.choose(weights,random.nextDouble());
            if(chosen<0 || !RecoverySupplies.insertInventory(data,members.get(chosen).stack().copy())) break;
            inserted++;
        }
        return inserted;
    }
    private static int category(Entry entry,boolean tactical) {
        if(entry.potion()!=null) {
            String potion=entry.potion().toString();
            if(!tactical) {
                if(!entry.item().toString().equals("minecraft:potion")) return -1;
                return switch(potion) { case "minecraft:healing","minecraft:strong_healing" -> 0;
                    case "minecraft:long_regeneration","minecraft:regeneration" -> 1;
                    case "minecraft:strong_regeneration" -> 2; default -> -1; };
            }
            boolean drink=entry.item().toString().equals("minecraft:potion");
            if(drink) return switch(potion) {
                case "minecraft:long_strength","minecraft:strong_strength" -> 0;
                case "minecraft:long_swiftness" -> 1; default -> -1; };
            if(!entry.item().toString().equals("minecraft:splash_potion")) return -1;
            return switch(potion) {
                case "minecraft:long_slowness" -> 2; case "minecraft:long_weakness" -> 3; case "minecraft:strong_poison" -> 4; default -> -1;
            };
        }
        if(!tactical) return -1;
        return switch(entry.item().toString()) {
            case "scguns:grenade","scguns:nail_bomb","scguns:hellfire_bomb","scguns:molotov_cocktail","jeg:grenade" -> 5;
            case "scguns:gas_grenade","scguns:choke_bomb","scguns:swarm_bomb","scguns:shotball","jeg:water_bomb" -> 6;
            default -> -1;
        };
    }
}
