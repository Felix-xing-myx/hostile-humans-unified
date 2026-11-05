package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.equipment.EquipmentBreakSounds;

import java.util.Map;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

/** Aggregate every accepted hit in a short window; never discard pellet damage. */
public final class ShieldDurability {
    private static final int WEAR_WINDOW_TICKS = 2;
    private static final Map<Human, List<PendingWear>> PENDING = new IdentityHashMap<>();

    private static final class PendingWear {
        final ItemStack shield;
        final InteractionHand hand;
        final long due;
        double damage;
        PendingWear(ItemStack shield, InteractionHand hand, long due, double damage) {
            this.shield = shield;
            this.hand = hand;
            this.due = due;
            this.damage = damage;
        }
    }
    private ShieldDurability() { }

    public static void damagePassiveOffhandShield(Human human, float incomingDamage) {
        ItemStack shield = human.getOffhandItem();
        if (GunnerShieldProtection.isUsableShield(shield))
            wear(human, shield, InteractionHand.OFF_HAND, incomingDamage);
    }

    public static void damageBackShield(Human human, GunnerShieldProtection.BackShield selected, float incomingDamage) {
        if (selected.backpackSlot() >= 0) {
            damageBackpackShield(human, selected.backpackSlot(), selected.stack(), incomingDamage);
            return;
        }
        ItemStack shield = selected.stack();
        if (human.level().isClientSide || human.getOffhandItem() != shield || !shield.isDamageableItem()) return;
        applyWear(human, shield, EquipmentSlot.OFFHAND, incomingDamage, true);
        if (shield.isEmpty() && human.getOffhandItem() == shield)
            human.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    private static void damageBackpackShield(Human human, int slot, ItemStack shield, float incomingDamage) {
        var data = human.getData();
        if (human.level().isClientSide || data == null || slot < 0 || slot >= data.getInventoryItemsSize()) return;
        if (data.getInventoryItem(slot) != shield
                || !GunnerShieldProtection.isUsableShield(shield) || !shield.isDamageableItem()) return;
        int before = shield.getDamageValue();
        applyWear(human, shield, EquipmentSlot.OFFHAND, incomingDamage, false);
        if (data.getInventoryItem(slot) != shield) return;
        if (shield.isEmpty()) data.setInventoryItem(slot, ItemStack.EMPTY);
        else if (shield.getDamageValue() != before) data.markInventoryDirty();
    }

    public static void damageShield(Human human, float blockedDamage) {
        InteractionHand hand = human.getUsedItemHand();
        ItemStack shield = human.getItemInHand(hand);
        if (shield.canPerformAction(ToolActions.SHIELD_BLOCK)) wear(human, shield, hand, blockedDamage);
    }

    static int wearCost(double damage, int maximumDurability) {
        return wearCost(damage, maximumDurability, .0027);
    }

    static int wearCost(double damage, int maximumDurability, double rate) {
        if (!Double.isFinite(damage) || damage <= 0 || maximumDurability <= 0) return 0;
        if (!Double.isFinite(rate) || rate < 0) rate = .0027;
        return (int) Math.min(Integer.MAX_VALUE, 1.0D + Math.floor(damage * (1.0D + rate * maximumDurability)));
    }

    private static void wear(Human human, ItemStack shield, InteractionHand hand, float damage) {
        if (human.level().isClientSide || shield.isEmpty() || !shield.isDamageableItem()
                || !Float.isFinite(damage) || damage <= 0) return;
        long now = human.level().getGameTime();
        settle(human, now, false);
        List<PendingWear> pending = PENDING.computeIfAbsent(human, ignored -> new ArrayList<>());
        for (PendingWear entry : pending) {
            if (entry.shield == shield) {
                entry.damage = Math.min(Double.MAX_VALUE, entry.damage + damage);
                return;
            }
        }
        pending.add(new PendingWear(shield, hand, now + WEAR_WINDOW_TICKS, damage));
    }

    /** Settle before ownership, equipment or serialization can copy/move the stack. */
    public static void flush(Human human) {
        if (!human.level().isClientSide) settle(human, Long.MAX_VALUE, true);
    }

    private static void settle(Human human, long now, boolean all) {
        List<PendingWear> pending = PENDING.get(human);
        if (pending == null) return;
        List<PendingWear> ready = new ArrayList<>();
        pending.removeIf(entry -> {
            if (!all && now < entry.due) return false;
            ready.add(entry);
            return true;
        });
        // Remove debts BEFORE native callbacks, which can themselves change equipment.
        if (pending.isEmpty()) PENDING.remove(human);
        for (PendingWear entry : ready) applyWear(human, entry);
    }

    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Human human : new ArrayList<>(PENDING.keySet())) {
            if (human.getServer() == event.getServer())
                settle(human, human.level().getGameTime(), human.isRemoved());
        }
    }

    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Human human) flush(human);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Human human) flush(human);
    }

    public static void onStopping(ServerStoppingEvent event) {
        for (Human human : new ArrayList<>(PENDING.keySet())) {
            if (human.getServer() == event.getServer()) flush(human);
        }
    }

    private static void applyWear(Human human, PendingWear entry) {
        ItemStack shield = entry.shield;
        InteractionHand hand = entry.hand;
        if (shield.isEmpty() || !shield.isDamageableItem()) return;
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        applyWear(human, shield, slot, entry.damage, true);
        if (shield.isEmpty() && human.getItemInHand(hand) == shield) {
            human.setItemSlot(slot, ItemStack.EMPTY);
            // A broken passive offhand shield must not interrupt main-hand gun use/reload.
            if (human.isUsingItem() && human.getUsedItemHand() == hand) human.stopUsingItem();
        }
    }

    private static void applyWear(Human human, ItemStack shield, EquipmentSlot slot, double damage, boolean held) {
        int cost = wearCost(damage, shield.getMaxDamage(),
                UnifiedConfig.get().damage("shield_durability_rate_per_damage", .0027));
        if (cost == 0) return;
        ItemStack before = shield.copy();
        // Keep native/custom durability and Unbreaking rolls; do not write Damage directly.
        // Prevent integer overflow when native damage is added to existing wear.
        cost = Math.min(cost, Integer.MAX_VALUE - Math.max(0, before.getDamageValue()));
        shield.hurtAndBreak(cost, human, broken -> {
            EquipmentBreakSounds.playNow(human, slot, before);
            if (held) human.broadcastBreakEvent(slot);
        });
    }
}

