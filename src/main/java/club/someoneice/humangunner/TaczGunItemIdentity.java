package club.someoneice.humangunner;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Only constructed when TaCZ is installed; independent of NPC gun configuration. */
final class TaczGunItemIdentity implements Predicate<ItemStack> {
    @Override public boolean test(ItemStack stack) { return IGun.getIGunOrNull(stack) != null; }

    /** Public cancellable API; register even when NPC gun integration is disabled. */
    static void registerMeleeProtection() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TaczGunItemIdentity::onGunMelee);
    }

    private static void onGunMelee(GunMeleeEvent event) {
        // NPC melee belongs to Human.doHurtTarget after switching away from the gun.
        if (event.getShooter() instanceof Human) event.setCanceled(true);
    }
}
