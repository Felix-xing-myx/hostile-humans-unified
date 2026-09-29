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
 * Emits break cues only from explicit durability-break paths. Equipment slot
 * changes are cached for identifying the broken stack, but are never treated
 * as evidence that an item broke (recovery temporarily moves gear between slots).
 */
public final class EquipmentBreakSounds {
    private static final Map<Human, EnumMap<EquipmentSlot, ItemStack>> LAST_EQUIPMENT =
            new WeakHashMap<>();
    private static final Map<Human, EnumMap<EquipmentSlot, Integer>> LAST_SOUND_TICK =
            new WeakHashMap<>();

    private EquipmentBreakSounds() {
    }

    /** Cache current damageable stacks without inferring breaks from slot changes. */
    static void tick(Human human) {
        EnumMap<EquipmentSlot, ItemStack> previous = LAST_EQUIPMENT.computeIfAbsent(
                human, ignored -> new EnumMap<>(EquipmentSlot.class));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            rememberIfDamageable(previous, slot, human.getItemBySlot(slot));
        }
    }

    static void observeEquipmentChange(
            Human human, EquipmentSlot slot, ItemStack previous, ItemStack current
    ) {
        EnumMap<EquipmentSlot, ItemStack> known = LAST_EQUIPMENT.computeIfAbsent(
                human, ignored -> new EnumMap<>(EquipmentSlot.class));
        rememberIfDamageable(known, slot, current);
        if (!known.containsKey(slot) && previous != null) {
            rememberIfDamageable(known, slot, previous);
        }
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

    public static void playNow(Human human, EquipmentSlot slot, ItemStack brokenStack) {
        play(human, slot, brokenStack);
    }

    private static void rememberIfDamageable(
            EnumMap<EquipmentSlot, ItemStack> known, EquipmentSlot slot, ItemStack stack
    ) {
        if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
            known.put(slot, stack.copy());
        }
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

        if (broken == null || broken.isEmpty()) {
            return;
        }
        SoundEvent sound = SoundEvents.ITEM_BREAK;
        float volume = SpartanEquipmentCompat.isShield(broken) || isArmorSlot(slot)
                ? 1.0F
                : 4.0F;
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

}
