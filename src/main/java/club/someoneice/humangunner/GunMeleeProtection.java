package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/** Final direct-melee safety net, independent of gun backend/configuration. */
public final class GunMeleeProtection {
    private GunMeleeProtection() { }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, GunMeleeProtection::onAttack);
        if (ModList.get().isLoaded("tacz")) TaczGunItemIdentity.registerMeleeProtection();
    }

    static void onAttack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide && blocks(event.getSource())) event.setCanceled(true);
    }

    public static boolean blocks(DamageSource source) {
        if (!(source.getEntity() instanceof Human human) || human.humanGunner$canMeleeAttack()) return false;
        return HumanMeleeDamage.isDirectAttack(source);
    }
}
