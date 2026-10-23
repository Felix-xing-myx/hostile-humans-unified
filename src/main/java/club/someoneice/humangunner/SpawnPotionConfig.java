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
            if (excludedFromSpawn(item)) return ItemStack.EMPTY;
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
    final Pool selfBuffs, throwables;

    SpawnPotionConfig(JsonObject root) {
        enabled = UnifiedConfig.bool(root, "enabled", true);
        recovery = parsePool(UnifiedConfig.object(root, "recovery"));
        tactical = parsePool(UnifiedConfig.object(root, "tactical"), true);
        selfBuffs = root.has("self_buffs") ? parsePool(UnifiedConfig.object(root,"self_buffs"),true) : null;
        throwables = root.has("throwables") ? parsePool(UnifiedConfig.object(root,"throwables"),true) : null;
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

    static Entry parseEntry(JsonObject root, boolean tactical) {
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
        int rank=switch(TierAttributes.key(human)) { case "tier1"->1; case "tier2"->2; case "tier3"->3; default->0; };
        // Old mixed-pool membership is a compatibility source only: the two totals are independent.
        Pool buffs=selfBuffs!=null?selfBuffs:new Pool(new int[]{1,1,2,3}[rank],new int[]{2,2,3,4}[rank],tactical.entries());
        Pool throwsPool=throwables!=null?throwables:new Pool(new int[]{2,5,8,12}[rank],new int[]{5,8,12,16}[rank],tactical.entries());
        int recoveryCount=FactionEquipmentPolicy.scaledCount(recovery.rollCount(human.getRandom()),profile.number("recovery_multiplier",1,0,100));
        int buffCount=FactionEquipmentPolicy.scaledCount(buffs.rollCount(human.getRandom()),profile.number("self_buff_multiplier",1,0,100));
        int throwableCount=FactionEquipmentPolicy.scaledCount(throwsPool.rollCount(human.getRandom()),profile.number("throwable_multiplier",1,0,100));
        int first=insertCategorized(human.getData(),human.getRandom(),recovery,recoveryCount,profile,"recovery");
        int buffsInserted=insertCategorized(human.getData(),human.getRandom(),buffs,buffCount,profile,"self_buff");
        int throwsInserted=insertCategorized(human.getData(),human.getRandom(),throwsPool,throwableCount,profile,"throwable");
        var tag=human.getPersistentData();
        tag.putInt("hostile_humans_unified:spawn_recovery_shortfall",recoveryCount-first);
        tag.putInt("hostile_humans_unified:spawn_self_buff_shortfall",buffCount-buffsInserted);
        tag.putInt("hostile_humans_unified:spawn_throwable_shortfall",throwableCount-throwsInserted);
        tag.putInt("hostile_humans_unified:spawn_self_buffs",buffsInserted);
        tag.putInt("hostile_humans_unified:spawn_throwables",throwsInserted);
        tag.putInt("hostile_humans_unified:spawn_potion_shortfall",recoveryCount+buffCount+throwableCount-first-buffsInserted-throwsInserted);
        return new Granted(first,buffsInserted+throwsInserted);
    }

    private static int insertCategorized(HumanData data,RandomSource random,Pool pool,int requested,
            FactionEquipmentConfig.Profile profile,String kind) {
        List<Available> available=new ArrayList<>();
        for(Entry entry:pool.entries()) {
            int category=category(entry,kind);
            if(category<0) continue;
            ItemStack stack=entry.resolve();
            if(!stack.isEmpty()) available.add(new Available(stack,entry.weight(),category));
        }
        double[] categories=profile.weights(kind+"_category_weights",kind.equals("recovery")?4:kind.equals("self_buff")?2:5);
        for(int i=0;i<categories.length;i++) {
            final int selected=i;
            if(available.stream().noneMatch(e->e.category()==selected)) categories[i]=0;
        }
        int inserted=0;
        for(int i=0;i<requested;i++) {
            int group=FactionEquipmentPolicy.choose(categories,random.nextDouble());
            if(group<0) break;
            List<Available> members=available.stream().filter(e->e.category()==group).toList();
            // User member weights remain authoritative, including custom grenade weights.
            int chosen=FactionEquipmentPolicy.choose(members.stream().mapToDouble(Available::weight).toArray(),random.nextDouble());
            if(chosen<0 || !RecoverySupplies.insertInventory(data,members.get(chosen).stack().copy())) break;
            inserted++;
        }
        return inserted;
    }

    private static int insertPool(HumanData data, RandomSource random, Pool pool, int requested) {
        List<Available> available=new ArrayList<>();
        for(Entry entry:pool.entries()) {
            ItemStack stack=entry.resolve();
            if(!stack.isEmpty()) available.add(new Available(stack,entry.weight(),0));
        }
        int inserted=0;
        for(int i=0;i<requested&&!available.isEmpty();i++) {
            int chosen=FactionEquipmentPolicy.choose(available.stream().mapToDouble(Available::weight).toArray(),random.nextDouble());
            if(chosen<0 || !RecoverySupplies.insertInventory(data,available.get(chosen).stack().copy())) break;
            inserted++;
        }
        return inserted;
    }
    static int category(Entry entry,String kind) {
        if (excludedFromSpawn(entry.item())) return -1;
        if(entry.potion()!=null) {
            String potion=entry.potion().toString();
            if(kind.equals("recovery")) {
                if(!entry.item().toString().equals("minecraft:potion")) return -1;
                return switch(potion) {
                    case "minecraft:healing" -> 0; case "minecraft:strong_healing" -> 1;
                    case "minecraft:long_regeneration" -> 2; case "minecraft:strong_regeneration" -> 3; default -> -1;
                };
            }
            if(kind.equals("self_buff")) {
                if(!entry.item().toString().equals("minecraft:potion")) return -1;
                return switch(potion) {
                    case "minecraft:long_strength","minecraft:strong_strength" -> 0;
                    case "minecraft:long_swiftness" -> 1; default -> -1;
                };
            }
            if(!entry.item().toString().equals("minecraft:splash_potion")) return -1;
            return switch(potion) {
                case "minecraft:long_slowness" -> 0; case "minecraft:long_weakness" -> 1;
                case "minecraft:strong_poison" -> 2; default -> -1;
            };
        }
        if(!kind.equals("throwable")) return -1;
        // Recognition of manually supplied tools does not admit them into natural spawn pools.
        return switch(entry.item().toString()) {
            case "scguns:nail_bomb","jeg:grenade" -> 3;
            case "scguns:gas_grenade","scguns:choke_bomb","scguns:swarm_bomb","jeg:water_bomb" -> 4;
            default -> -1;
        };
    }
    /** Only natural supplies: the native Scguns grenade can destroy blocks. Manual use stays supported.
     * Enforced during classification and resolution so old/custom and legacy mixed pools cannot grant it. */
    private static boolean excludedFromSpawn(ResourceLocation item) {
        return item.toString().equals("scguns:grenade");
    }
}
