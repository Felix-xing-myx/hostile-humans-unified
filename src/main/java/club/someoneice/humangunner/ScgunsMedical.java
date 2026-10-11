package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/** Registry/config adapter for native Player-only bandages. Never reflects private medicine fields. */
final class ScgunsMedical {
    private static final String COOLDOWN = HumanGunner.MOD_ID + ":scguns_medical_ticks";
    private ScgunsMedical() {}
    static ScgunsSettings.Medicine profile(ItemStack stack) {
        if (!ModList.get().isLoaded("scguns") || !UnifiedConfig.get().scguns().enabled() || stack.isEmpty()
                || !ScgunsMedicalBridge.supports(stack)) return null;
        return UnifiedConfig.get().scguns().medicines().get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    static boolean isSupply(ItemStack stack) { return profile(stack) != null; }
    static boolean canUse(Human human, ItemStack stack) {
        var profile = profile(stack);
        return profile != null && human.getPersistentData().getInt(COOLDOWN) <= 0
                && profile.effects().stream().filter(ScgunsSettings.Effect::isRegeneration).noneMatch(effect -> {
                    var type = BuiltInRegistries.MOB_EFFECT.get(effect.id());
                    return type != null && human.hasEffect(type);
                });
    }
    static double inventoryValue(ItemStack stack) { var p = profile(stack); return p == null ? 0 : p.quality(); }
    static int useDuration(ItemStack stack) { var p = profile(stack); return p == null ? -1 : p.useTicks(); }
    static void tick(Human human) {
        if (!human.level().isClientSide && human.getPersistentData().getInt(COOLDOWN) > 0)
            human.getPersistentData().putInt(COOLDOWN, human.getPersistentData().getInt(COOLDOWN) - 1);
    }
    static void register() {
        if (!ModList.get().isLoaded("scguns")) return;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ScgunsMedical::start);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ScgunsMedical::finish);
    }
    private static void start(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide
                || !isSupply(event.getItem()) || !RecoverySupplies.ownsSupplyUse(human, event.getItem())) return;
        if (!canUse(human, event.getItem())) { event.setCanceled(true); return; }
        event.setDuration(useDuration(event.getItem()));
    }
    private static void finish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide || !human.isAlive()
                || !RecoverySupplies.ownsSupplyUse(human, event.getItem()) || !canUse(human, event.getItem())) return;
        var profile = profile(event.getItem());
        ItemStack result = event.getResultStack();
        if (result.getItem() == event.getItem().getItem() && result.getCount() > event.getItem().getCount()) return;
        if (PotionRecoveryPolicy.needsPotionCharge(result.getItem() == event.getItem().getItem(),
                event.getItem().getCount(), result.getCount())) {
            result = result.copy(); result.shrink(1); event.setResultStack(result);
        }
        human.heal(profile.healing());
        var lacerated = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation("scguns", "lacerated"));
        if (lacerated != null) human.removeEffect(lacerated);
        for (var effect : profile.effects()) {
            var type = BuiltInRegistries.MOB_EFFECT.get(effect.id());
            if (type != null) human.addEffect(new MobEffectInstance(type, effect.durationTicks(), effect.amplifier()));
        }
        human.getPersistentData().putInt(COOLDOWN, profile.cooldownTicks());
    }
}
