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
 * changes cache only the break cue category, but are never treated
 * as evidence that an item broke (recovery temporarily moves gear between slots).
 */
public final class EquipmentBreakSounds {
    private enum BreakCue { QUIET, NORMAL }
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static final Map<Human, EnumMap<EquipmentSlot, BreakCue>> LAST_EQUIPMENT =
            new WeakHashMap<>();
    private static final Map<Human, EnumMap<EquipmentSlot, Integer>> LAST_SOUND_TICK =
            new WeakHashMap<>();

    private EquipmentBreakSounds() {
    }

    /** Cache damageable items' cue categories without inferring breaks from slot changes. */
    static void tick(Human human) {
        EnumMap<EquipmentSlot, BreakCue> previous = LAST_EQUIPMENT.computeIfAbsent(
                human, ignored -> new EnumMap<>(EquipmentSlot.class));
        for (EquipmentSlot slot : SLOTS) {
            rememberIfDamageable(previous, slot, human.getItemBySlot(slot));
        }
    }

    static void observeEquipmentChange(
            Human human, EquipmentSlot slot, ItemStack previous, ItemStack current
    ) {
        EnumMap<EquipmentSlot, BreakCue> known = LAST_EQUIPMENT.computeIfAbsent(
                human, ignored -> new EnumMap<>(EquipmentSlot.class));
        rememberIfDamageable(known, slot, current);
        if (!known.containsKey(slot) && previous != null) {
            rememberIfDamageable(known, slot, previous);
        }
    }

    public static void playNow(Human human, EquipmentSlot slot) {
        ItemStack broken = human.getItemBySlot(slot);
        BreakCue cue = broken.isEmpty() ? null : cue(slot, broken);
        if (broken.isEmpty()) {
            EnumMap<EquipmentSlot, BreakCue> previous = LAST_EQUIPMENT.get(human);
            if (previous != null) {
                cue = previous.get(slot);
            }
        }
        play(human, slot, cue);
    }

    public static void playNow(Human human, EquipmentSlot slot, ItemStack brokenStack) {
        play(human, slot, brokenStack == null || brokenStack.isEmpty() ? null : cue(slot, brokenStack));
    }

    private static void rememberIfDamageable(
            EnumMap<EquipmentSlot, BreakCue> known, EquipmentSlot slot, ItemStack stack
    ) {
        if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
            // A break cue needs only its volume category, not a copied gun,
            // attachment, enchantment or capability NBT every entity tick.
            known.put(slot, cue(slot, stack));
        }
    }

    private static BreakCue cue(EquipmentSlot slot, ItemStack stack) {
        return isArmorSlot(slot) || SpartanEquipmentCompat.isShield(stack) ? BreakCue.QUIET : BreakCue.NORMAL;
    }

    private static void play(Human human, EquipmentSlot slot, BreakCue cue) {
        if (human.level().isClientSide || cue == null) {
            return;
        }
        EnumMap<EquipmentSlot, Integer> ticks = LAST_SOUND_TICK.computeIfAbsent(
                human, ignored -> new EnumMap<>(EquipmentSlot.class)
        );
        if (ticks.getOrDefault(slot, Integer.MIN_VALUE) == human.tickCount) {
            return;
        }
        ticks.put(slot, human.tickCount);

        SoundEvent sound = SoundEvents.ITEM_BREAK;
        float volume = cue == BreakCue.QUIET ? 1.0F : 4.0F;
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
