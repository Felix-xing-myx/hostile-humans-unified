package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/**
 * Optional, NPC-only First Aid adapter. Item use belongs to RecoverySupplies;
 * Forge start/finish events supply timing and settle the actual consumable.
 * No player body model, private reflection, mixin, or additional AI goal is used.
 */
public final class FirstAidCompat {
    private static final ResourceLocation PLASTER = new ResourceLocation("firstaid", "plaster");
    private static final ResourceLocation BANDAGE = new ResourceLocation("firstaid", "bandage");
    private static final ResourceLocation MORPHINE = new ResourceLocation("firstaid", "morphine");
    private static final String STATE = HumanGunner.MOD_ID + ":first_aid";

    record HealingSchedule(int pulses, int intervalTicks) {
    }

    private FirstAidCompat() {
    }

    public static void register() {
        if (!ModList.get().isLoaded("firstaid")) return;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, FirstAidCompat::onStart);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, FirstAidCompat::onUseTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, FirstAidCompat::onFinish);
    }

    private static boolean matches(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isMedicine(ItemStack stack) {
        return (matches(stack, PLASTER) || matches(stack, BANDAGE)) && UnifiedConfig.get().firstAid().enabled();
    }

    public static boolean isMorphine(ItemStack stack) {
        return matches(stack, MORPHINE) && UnifiedConfig.get().firstAid().enabled();
    }

    public static boolean isSupply(ItemStack stack) {
        return isMedicine(stack) || isMorphine(stack);
    }

    /** Unit quality; the existing inventory policy handles stack count separately. */
    public static double inventoryValue(ItemStack stack) {
        if (!isSupply(stack)) return 0.0D;
        FirstAidSettings settings = UnifiedConfig.get().firstAid();
        if (matches(stack, PLASTER)) return settings.plasterQuality();
        if (matches(stack, BANDAGE)) return settings.bandageQuality();
        return settings.morphineQuality();
    }

    public static boolean canUse(Human human, ItemStack stack) {
        CompoundTag state = human.getPersistentData().getCompound(STATE);
        if (isMedicine(stack)) return state.getInt("pulses") <= 0
                && (matches(stack, PLASTER) ? UnifiedConfig.get().firstAid().plasterHealing()
                    : UnifiedConfig.get().firstAid().bandageHealing()) > 0;
        if (isMorphine(stack)) return state.getInt("morphine_ticks") <= 0
                && !human.hasEffect(MobEffects.DAMAGE_RESISTANCE);
        return false;
    }

    /** Negative means unsupported; do not resolve the optional API for unrelated items. */
    public static int useDuration(ItemStack stack) {
        if (!isSupply(stack) || !ModList.get().isLoaded("firstaid")) return -1;
        return isMedicine(stack) ? FirstAidApiBridge.applyTicks(stack) : Math.max(1, stack.getUseDuration());
    }

    private static void onStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide
                || !isSupply(event.getItem()) || !RecoverySupplies.ownsSupplyUse(human, event.getItem())) return;
        int duration = useDuration(event.getItem());
        if (!canUse(human, event.getItem()) || duration < 1) {
            event.setCanceled(true);
            return;
        }
        event.setDuration(duration);
    }

    private static void onFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide || !human.isAlive()
                || !isSupply(event.getItem()) || !RecoverySupplies.ownsSupplyUse(human, event.getItem())
                || !canUse(human, event.getItem())) return;
        ItemStack before = event.getItem();
        HealingSchedule schedule = isMedicine(before) ? FirstAidApiBridge.schedule(before) : null;
        if (isMedicine(before) && schedule == null) return;

        // Morphine already consumes in its native finish method; ItemHealing does not.
        // Preserve other finish handlers' container/consumed results, charging only
        // an unchanged stack. Never edit a registry item or affect a player's use.
        ItemStack after = event.getResultStack();
        boolean sameItem = before.getItem() == after.getItem();
        if (sameItem && after.getCount() > before.getCount()) return;
        // A finish handler may update tags without consuming the supply.
        if (PotionRecoveryPolicy.needsPotionCharge(sameItem,
                before.getCount(), after.getCount())) {
            after = after.copy();
            after.shrink(1);
            event.setResultStack(after);
        }
        CompoundTag state = human.getPersistentData().getCompound(STATE);
        if (schedule != null) {
            FirstAidSettings settings = UnifiedConfig.get().firstAid();
            float total = matches(before, PLASTER) ? settings.plasterHealing() : settings.bandageHealing();
            state.putInt("pulses", schedule.pulses());
            state.putInt("interval", schedule.intervalTicks());
            state.putInt("next_pulse", schedule.intervalTicks());
            state.putFloat("heal_per_pulse", total / schedule.pulses());
        } else {
            FirstAidSettings settings = UnifiedConfig.get().firstAid();
            state.putInt("morphine_ticks", settings.morphineTicks());
            human.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                    settings.morphineTicks(), settings.resistanceAmplifier()));
        }
        human.getPersistentData().put(STATE, state);
    }

    private static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof Human human && !human.level().isClientSide
                && isSupply(event.getItem()) && RecoverySupplies.ownsSupplyUse(human, event.getItem())
                && !canUse(human, event.getItem())) {
            // An externally applied effect must not spend another supply.
            // Ordinary custody restores the interrupted stack and displaced gear.
            event.setCanceled(true);
        }
    }

    public static float pendingHealing(Human human) {
        CompoundTag state = human.getPersistentData().getCompound(STATE);
        return Math.max(0, state.getInt("pulses")) * Math.max(0.0F, state.getFloat("heal_per_pulse"));
    }

    /** Persistent remaining loaded ticks: unloading cannot skip healing or reset cooldowns. */
    public static void tick(Human human) {
        if (human.level().isClientSide || !human.isAlive() || !human.getPersistentData().contains(STATE)) return;
        CompoundTag state = human.getPersistentData().getCompound(STATE);
        int morphine = Math.max(0, state.getInt("morphine_ticks") - 1);
        state.putInt("morphine_ticks", morphine);
        int pulses = state.getInt("pulses");
        if (pulses > 0) {
            int remaining = state.getInt("next_pulse") - 1;
            if (remaining <= 0) {
                human.heal(Math.max(0.0F, state.getFloat("heal_per_pulse")));
                state.putInt("pulses", --pulses);
                remaining = Math.max(1, state.getInt("interval"));
            }
            state.putInt("next_pulse", remaining);
        }
        // Full health discards only that pulse, never the shared medical cooldown.
        if (pulses <= 0 && morphine <= 0) human.getPersistentData().remove(STATE);
    }
}
