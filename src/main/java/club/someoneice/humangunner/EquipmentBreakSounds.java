package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Emits break cues only from explicit durability-break paths. Equipment slot
 * changes cache only the break cue category, but are never treated
 * as evidence that an item broke (recovery temporarily moves gear between slots).
 */
public final class EquipmentBreakSounds {
    private enum BreakCue { QUIET, SHIELD, NORMAL }
    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        final EnumMap<EquipmentSlot, Item> items = new EnumMap<>(EquipmentSlot.class);
        final EnumMap<EquipmentSlot, BreakCue> cues = new EnumMap<>(EquipmentSlot.class);
        final EnumMap<EquipmentSlot, Integer> soundTicks = new EnumMap<>(EquipmentSlot.class);
    }

    private EquipmentBreakSounds() {
    }

    /** Cache damageable items' cue categories without inferring breaks from slot changes. */
    static void tick(Human human) {
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        for (EquipmentSlot slot : SLOTS) {
            rememberIfDamageable(state, slot, human.getItemBySlot(slot));
        }
    }

    static void observeEquipmentChange(
            Human human, EquipmentSlot slot, ItemStack previous, ItemStack current
    ) {
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        rememberIfDamageable(state, slot, current);
        if (!state.cues.containsKey(slot) && previous != null) {
            rememberIfDamageable(state, slot, previous);
        }
    }

    public static void playNow(Human human, EquipmentSlot slot) {
        ItemStack broken = human.getItemBySlot(slot);
        BreakCue cue = broken.isEmpty() ? null : cue(slot, broken);
        State state = STATES.get(human);
        if (broken.isEmpty() && state != null) cue = state.cues.get(slot);
        Item remembered = state == null ? null : state.items.get(slot);
        Component name = !broken.isEmpty() ? broken.getHoverName()
                : remembered == null ? null : remembered.getDescription();
        play(human, slot, cue, name);
    }

    public static void playNow(Human human, EquipmentSlot slot, ItemStack brokenStack) {
        play(human, slot, brokenStack == null || brokenStack.isEmpty() ? null : cue(slot, brokenStack),
                brokenStack == null || brokenStack.isEmpty() ? null : brokenStack.getHoverName());
    }

    private static void rememberIfDamageable(
            State state, EquipmentSlot slot, ItemStack stack
    ) {
        if (stack != null && !stack.isEmpty() && stack.isDamageableItem()) {
            // A break cue needs only its volume category, not a copied gun,
            // attachment, enchantment or capability NBT every entity tick.
            state.cues.put(slot, cue(slot, stack));
            state.items.put(slot, stack.getItem());
        }
    }

    private static BreakCue cue(EquipmentSlot slot, ItemStack stack) {
        return isArmorSlot(slot) ? BreakCue.QUIET
                : SpartanEquipmentCompat.isShield(stack) ? BreakCue.SHIELD : BreakCue.NORMAL;
    }

    private static void play(Human human, EquipmentSlot slot, BreakCue cue, Component itemName) {
        if (human.level().isClientSide || cue == null) {
            return;
        }
        var ticks = STATES.computeIfAbsent(human, ignored -> new State()).soundTicks;
        if (ticks.getOrDefault(slot, Integer.MIN_VALUE) == human.tickCount) {
            return;
        }
        ticks.put(slot, human.tickCount);
        // Only explicit break callbacks reach this gate; equipment swapping
        // does not report a break. Duplicate callbacks share the sound gate.
        if (itemName != null) SoldierDialogue.broken(human, slot, itemName, cue == BreakCue.SHIELD);

        SoundEvent sound = SoundEvents.ITEM_BREAK;
        float volume = cue == BreakCue.NORMAL ? 4.0F : 1.0F;
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
