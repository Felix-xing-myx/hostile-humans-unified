package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.entity.TierThreeHuman;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import dev.felix.hostilehumans.progression.TierAttributes;

import dev.felix.hostilehumans.entity.HumanUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Thin spawn consumer of the unified faction equipment selector. */
public final class ConfiguredHumanEquipment {
    private ConfiguredHumanEquipment() { }
    public static ConfiguredEquipmentLoadout of(Human human) { return UnifiedConfig.get().equipmentLoadout(TierAttributes.key(human)); }

    static void generate(Human human,boolean forceRanged,boolean nativeRangedRoll) {
        var loadout=of(human);
        if(TierThreeHuman.isTierThree(human)) {
            human.getPersistentData().putBoolean(TierThreeHuman.MARKER,true);
            human.team="human_level3";
        }
        boolean useRanged=!loadout.rangedInBackpack && nativeRangedRoll && HumanSpawnEquipment.wantsRanged(human);
        var entry=useRanged?loadout.pools(human).ranged(human):loadout.pools(human).melee(human);
        ItemStack main=stack(human,entry,EquipmentSlot.MAINHAND);
        // An empty or denied category is intentional. Never reconstruct gear behind the whitelist.
        equip(human,EquipmentSlot.MAINHAND,main);
        if(human.getRandom().nextDouble()<loadout.offhandChance) {
            var off=loadout.offhand.roll(human.getRandom(),candidate-> {
                ItemStack item=new ItemStack(ForgeRegistries.ITEMS.getValue(candidate.id()));
                return !item.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
                        && !SpartanEquipmentCompat.isShield(item) && !weapon(item) && loadout.pools(human).allowed(candidate.id(),false);
            });
            equip(human,EquipmentSlot.OFFHAND,stack(human,off,EquipmentSlot.OFFHAND));
        }
        if(nonGunRanged(main) && weaponCount(human)<loadout.weaponMax) {
            ItemStack backup=backupMelee(human);
            if(!backup.isEmpty()) RecoverySupplies.insertInventory(human.getData(),backup);
        }
        var armor=loadout.pools(human).armor(human);
        if(armor!=null) {
            armor(human,EquipmentSlot.HEAD,armor.head()); armor(human,EquipmentSlot.CHEST,armor.chest());
            armor(human,EquipmentSlot.LEGS,armor.legs()); armor(human,EquipmentSlot.FEET,armor.feet());
        }
    }
    static void finishRanged(Human human,boolean forceRanged) {
        var loadout=of(human);
        if(GunCustody.hasOwnedGun(human) || !loadout.rangedInBackpack || hasRanged(human) || weaponCount(human)>=loadout.weaponMax
                || (!forceRanged&&!HumanSpawnEquipment.wantsRanged(human))) return;
        ItemStack ranged=stack(human,loadout.pools(human).ranged(human),EquipmentSlot.MAINHAND);
        if(ranged.isEmpty()) return;
        RangedWeaponCustody.registerPreferred(human,ranged);
        RecoverySupplies.insertInventory(human.getData(),ranged);
    }
    private static boolean hasRanged(Human human) {
        if(nonGunRanged(human.getMainHandItem())||nonGunRanged(human.getOffhandItem())) return true;
        for(int i=0;i<human.getData().getInventoryItemsSize();i++) if(nonGunRanged(human.getData().getInventoryItem(i))) return true;
        return false;
    }
    static ItemStack backupMelee(Human human) { return stack(human,of(human).pools(human).reserveMelee(human),EquipmentSlot.MAINHAND); }
    static ItemStack shield(Human human) { return stack(human,of(human).pools(human).shield(human),EquipmentSlot.OFFHAND); }
    static void generateReserves(Human human) {
        var loadout=of(human);
        generateMeleeReserves(human, loadout);
        int target=SpawnWeaponCountPolicy.roll(loadout.weaponMax,loadout.secondWeaponChance,loadout.thirdWeaponChance,
                human.getRandom().nextDouble(),human.getRandom().nextDouble());
        for(int owned=weaponCount(human);owned<target;owned++) {
            ItemStack spare=backupMelee(human);
            if(spare.isEmpty() || !RecoverySupplies.insertInventory(human.getData(),spare)) break;
        }
    }
    private static void generateMeleeReserves(Human human, ConfiguredEquipmentLoadout loadout) {
        var policy = FactionEquipmentConfig.of(human).meleeReserves();
        if (policy == null) return;
        int target = policy.target(loadout.weaponMax, human.getRandom().nextDouble(), human.getRandom().nextDouble());
        int owned = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
            if (reserveMatches(human.getItemBySlot(slot), policy)) owned += human.getItemBySlot(slot).getCount();
        for (int i = 0; i < human.getData().getInventoryItemsSize(); i++) {
            ItemStack item = human.getData().getInventoryItem(i);
            if (reserveMatches(item, policy)) owned += item.getCount();
        }
        int additional = policy.additional(target, owned, weaponCount(human), loadout.weaponMax);
        for (int i = 0; i < additional; i++) {
            var entry = policy.preferOneHanded() ? loadout.pools(human).oneHandedMelee(human)
                    : loadout.pools(human).reserveMelee(human);
            ItemStack item = stack(human, entry, EquipmentSlot.MAINHAND);
            if (item.isEmpty() || !RecoverySupplies.insertInventory(human.getData(), item)) break;
        }
    }
    private static boolean reserveMatches(ItemStack item, SpawnMeleeReservePolicy policy) {
        return policy.preferOneHanded() ? FactionEquipmentPools.singleHandedMelee(item)
                : HumanLootManager.isDedicatedMeleeWeapon(item);
    }
    static int weaponCount(Human human) {
        int count=0;
        for(EquipmentSlot slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND})
            if(weapon(human.getItemBySlot(slot))) count+=human.getItemBySlot(slot).getCount();
        for(int i=0;i<human.getData().getInventoryItemsSize();i++) if(weapon(human.getData().getInventoryItem(i))) count+=human.getData().getInventoryItem(i).getCount();
        return count;
    }
    private static boolean weapon(ItemStack stack) {
        return !stack.isEmpty() && (GunSupport.isGunItem(stack)||nonGunRanged(stack)||HumanLootManager.isDedicatedMeleeWeapon(stack));
    }
    private static boolean nonGunRanged(ItemStack stack) {
        return !stack.isEmpty() && !GunSupport.isGunItem(stack) && (RangedWeaponCustody.isBowOrCrossbow(stack)||HumanUtil.isTrident(stack));
    }
    private static void armor(Human human,EquipmentSlot slot,ResourceLocation id) {
        equip(human,slot,prepare(human,new ItemStack(ForgeRegistries.ITEMS.getValue(id)),slot));
    }
    private static ItemStack stack(Human human,ConfiguredEquipmentLoadout.ItemEntry entry,EquipmentSlot slot) {
        if(entry==null || !entry.available()) return ItemStack.EMPTY;
        ItemStack stack=new ItemStack(ForgeRegistries.ITEMS.getValue(entry.id()),entry.count());
        if(GunSupport.isGunItem(stack)) return ItemStack.EMPTY;
        if(weapon(stack)||SpartanEquipmentCompat.isShield(stack)) stack.setCount(1);
        stack=prepare(human,stack,slot);
        if(RangedWeaponCustody.isBowOrCrossbow(stack)) RangedWeaponCustody.registerPreferred(human,stack);
        return stack;
    }
    static ItemStack prepare(Human human,ItemStack stack,EquipmentSlot slot) {
        if(stack.isEmpty()) return stack;
        var profile=FactionEquipmentConfig.of(human);
        var durability=FactionEquipmentConfig.object(profile.data,"durability");
        double min=FactionEquipmentConfig.number(durability,"min",0,0,1), max=FactionEquipmentConfig.number(durability,"max",0,0,1);
        if(stack.isDamageableItem()) {
            double wear=Math.min(min,max)+human.getRandom().nextDouble()*Math.abs(max-min);
            stack.setDamageValue(Math.min(stack.getMaxDamage()-1,(int)(stack.getMaxDamage()*wear)));
        }
        if(SpartanEquipmentCompat.isShield(stack)&&!of(human).shieldEnchantments) {
            stack.getOrCreateTag().putBoolean("hostile_humans_unified:durability_enchantment_rolled",true);
            return stack;
        }
        return of(human).enchantments(human).apply(human,stack,slot);
    }
    private static void equip(Human human,EquipmentSlot slot,ItemStack stack) { if(!stack.isEmpty()) human.setItemSlot(slot,stack); }
}
