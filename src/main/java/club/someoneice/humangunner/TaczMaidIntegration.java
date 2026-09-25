package club.someoneice.humangunner;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/** Requires both integrations; never registered by the base mod alone. */
final class TaczMaidIntegration {
    static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                EntityHurtByGunEvent.Pre.class, TaczMaidIntegration::onDamage);
    }
    private static void onDamage(EntityHurtByGunEvent.Pre event) {
        if (event.getHurtEntity() instanceof EntityMaid maid && maid.getOwnerUUID() != null)
            event.setCanceled(false);
    }
}
