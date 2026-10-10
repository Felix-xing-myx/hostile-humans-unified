package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;

/** One enchanted wear transaction and one cooldown for all shields carried by a human. */
public final class ShieldDurability {
    private static final int WEAR_COOLDOWN_TICKS = 4;
    private static final Map<Human, Long> NEXT_WEAR = new WeakHashMap<>();
    private ShieldDurability() { }

    public static void damagePassiveOffhandShield(Human human, float incomingDamage) {
        ItemStack shield = human.getOffhandItem();
        if (GunnerShieldProtection.isUsableShield(shield))
            wear(human, shield, InteractionHand.OFF_HAND, incomingDamage);
    }

    public static void damageShield(Human human, float blockedDamage) {
        InteractionHand hand = human.getUsedItemHand();
        ItemStack shield = human.getItemInHand(hand);
        if (shield.canPerformAction(ToolActions.SHIELD_BLOCK)) wear(human, shield, hand, blockedDamage);
    }

    static int wearCost(float damage) {
        if (!Float.isFinite(damage) || damage <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, 1.0D + Math.floor((double) damage * 1.5D));
    }

    static boolean actuallyWorn(int oldDamage, int oldCount, int newDamage, int newCount) {
        return newDamage > oldDamage || newCount < oldCount;
    }

    private static void wear(Human human, ItemStack shield, InteractionHand hand, float damage) {
        if (human.level().isClientSide || shield.isEmpty() || !shield.isDamageableItem()) return;
        int cost = wearCost(damage);
        long now = human.level().getGameTime();
        if (cost == 0 || now < NEXT_WEAR.getOrDefault(human, Long.MIN_VALUE)) return;
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemStack before = shield.copy();
        // Keep native/custom durability and Unbreaking rolls; do not write Damage directly.
        // Prevent integer overflow when native damage is added to existing wear.
        cost = Math.min(cost, Integer.MAX_VALUE - Math.max(0, before.getDamageValue()));
        shield.hurtAndBreak(cost, human, broken -> {
            EquipmentBreakSounds.playNow(human, slot, before);
            human.broadcastBreakEvent(slot);
        });
        if (actuallyWorn(before.getDamageValue(), before.getCount(), shield.getDamageValue(), shield.getCount()))
            NEXT_WEAR.put(human, now + WEAR_COOLDOWN_TICKS);
        if (shield.isEmpty()) {
            human.setItemSlot(slot, ItemStack.EMPTY);
            // A broken passive offhand shield must not interrupt main-hand gun use/reload.
            if (human.isUsingItem() && human.getUsedItemHand() == hand) human.stopUsingItem();
        }
    }
}

