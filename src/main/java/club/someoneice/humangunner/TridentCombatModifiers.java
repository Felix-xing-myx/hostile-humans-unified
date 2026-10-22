package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Trident's native hit method uses a fixed damage literal, not AbstractArrow.baseDamage. */
@Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID)
public final class TridentCombatModifiers {
    private static final String MULTIPLIER = "hostile_humans_unified:trident_damage_multiplier";
    private TridentCombatModifiers() { }
    public static void markThrown(Human human, ThrownTrident projectile) {
        if (!human.level().isClientSide)
            projectile.getPersistentData().putDouble(MULTIPLIER,
                    RangedCombatProfile.of(human, human.getMainHandItem()).damage());
    }
    public static int knockbackLevel(Human human) {
        return RangedCombatProfile.of(human, human.getMainHandItem()).damage() > 1 ? 2 : 1;
    }
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) return;
        var direct = event.getSource().getDirectEntity();
        double multiplier = 1;
        if (direct instanceof ThrownTrident projectile && projectile.getOwner() instanceof Human
                && projectile.getPersistentData().contains(MULTIPLIER)) {
            multiplier = projectile.getPersistentData().getDouble(MULTIPLIER);
        } else if (direct instanceof Human human && human.getMainHandItem().getItem() instanceof TridentItem) {
            multiplier = RangedCombatProfile.of(human, human.getMainHandItem()).damage();
        }
        if (multiplier > 1) event.setAmount((float) (event.getAmount() * multiplier));
    }
}
