package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Emits the vanilla tool-breaking cue and catches direct slot clears which do
 * not invoke LivingEntity#broadcastBreakEvent.
 */
public final class EquipmentBreakSounds {
    private static final Map<Human, EnumMap<EquipmentSlot, ItemStack>> LAST_EQUIPMENT =
            new WeakHashMap<>();
    private static final Map<Human, EnumMap<EquipmentSlot, ItemStack>> BEFORE_DAMAGE =
            new WeakHashMap<>();
    private static final Map<Human, EnumMap<EquipmentSlot, Integer>> LAST_SOUND_TICK =
            new WeakHashMap<>();

    private EquipmentBreakSounds() {
    }

    static void captureBeforeDamage(Human human) {
        BEFORE_DAMAGE.put(human, snapshot(human));
    }

    static void detectAfterDamage(Human human) {
        EnumMap<EquipmentSlot, ItemStack> before = BEFORE_DAMAGE.remove(human);
        if (before != null) {
            compare(human, before, snapshot(human));
        }
        LAST_EQUIPMENT.put(human, snapshot(human));
    }

    /** Run before recovery and weapon custody can refill an emptied hand. */
    static void tick(Human human) {
        EnumMap<EquipmentSlot, ItemStack> current = snapshot(human);
        EnumMap<EquipmentSlot, ItemStack> previous = LAST_EQUIPMENT.put(human, current);
        if (previous != null) {
            compare(human, previous, current);
        }
    }

    static void observeEquipmentChange(
            Human human, EquipmentSlot slot, ItemStack previous, ItemStack current
    ) {
        if (isBrokenTransition(previous, current)) {
            play(human, slot, previous);
        }
        LAST_EQUIPMENT.computeIfAbsent(human, ignored -> snapshot(human))
                .put(slot, current.copy());
    }

    public static void playNow(Human human, EquipmentSlot slot) {
        ItemStack broken = human.getItemBySlot(slot);
        if (broken.isEmpty()) {
            EnumMap<EquipmentSlot, ItemStack> previous = LAST_EQUIPMENT.get(human);
            if (previous != null) {
                broken = previous.getOrDefault(slot, ItemStack.EMPTY);
            }
        }
        play(human, slot, broken);
    }

    private static void compare(
            Human human,
            EnumMap<EquipmentSlot, ItemStack> before,
            EnumMap<EquipmentSlot, ItemStack> after
    ) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack previous = before.get(slot);
            if (isBrokenTransition(previous, after.get(slot))) {
                play(human, slot, previous);
            }
        }
    }

    private static boolean isBrokenTransition(ItemStack before, ItemStack after) {
        return before != null
                && !before.isEmpty()
                && before.isDamageableItem()
                && (after == null || after.isEmpty());
    }

    private static void play(Human human, EquipmentSlot slot, ItemStack broken) {
        if (human.level().isClientSide) {
            return;
        }
        EnumMap<EquipmentSlot, Integer> ticks = LAST_SOUND_TICK.computeIfAbsent(
                human, ignored -> new EnumMap<>(EquipmentSlot.class)
        );
        if (ticks.getOrDefault(slot, Integer.MIN_VALUE) == human.tickCount) {
            return;
        }
        ticks.put(slot, human.tickCount);

        SoundEvent sound;
        float volume;
        if (SpartanEquipmentCompat.isShield(broken)) {
            sound = SoundEvents.ANVIL_LAND;
            volume = 6.0F;
        } else if (isArmorSlot(slot)) {
            sound = SoundEvents.LANTERN_BREAK;
            volume = 8.0F;
        } else {
            sound = SoundEvents.ITEM_BREAK;
            volume = 4.0F;
        }
        // The authoritative callback and the direct-clear fallback share this
        // per-slot/tick gate, so one break can never create two sound packets.
        human.level().playSound(
                null, human.getX(), human.getY(), human.getZ(),
                sound, SoundSource.PLAYERS, volume,
                0.90F + human.getRandom().nextFloat() * 0.20F
        );
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }

    private static EnumMap<EquipmentSlot, ItemStack> snapshot(Human human) {
        EnumMap<EquipmentSlot, ItemStack> result = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            result.put(slot, human.getItemBySlot(slot).copy());
        }
        return result;
    }
}
