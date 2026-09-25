package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.concurrent.atomic.AtomicBoolean;

/** Optional Touhou Little Maid integration; this class is only loaded when the mod is present. */
public final class TouhouMaidCompat {
    private static final AtomicBoolean LOGGED_FIRST_GUN_HIT = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_MELEE_HIT = new AtomicBoolean();

    private TouhouMaidCompat() {
    }

    public static void register() {
        if (GunSupport.get().enabled()) TaczMaidIntegration.register();
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                true,
                MaidHurtEvent.class,
                TouhouMaidCompat::onTamedMaidHurt
        );
        HumanGunner.LOGGER.info("Human Gunner enabled optional Touhou Little Maid damage integration");
    }

    private static void onTamedMaidHurt(MaidHurtEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.getOwnerUUID() == null) {
            return;
        }

        if (isTaczBulletDamage(event)) {
            event.setCanceled(false);
            event.setAmount(event.getAmount() * (float) UnifiedConfig.get().gunSetting("tamed_maid_damage_multiplier", .15));
            if (LOGGED_FIRST_GUN_HIT.compareAndSet(false, true)) {
                HumanGunner.LOGGER.info(
                        "Human Gunner applied configured TaCZ damage multiplier to tamed maid {}",
                        maid.getUUID()
                );
            }
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Human && event.getSource().getDirectEntity() == attacker) {
            event.setAmount(event.getAmount() * (float) UnifiedConfig.get().damage("tamed_maid_melee_damage_multiplier", .25));
            if (LOGGED_FIRST_MELEE_HIT.compareAndSet(false, true)) {
                HumanGunner.LOGGER.info(
                        "Human Gunner applied configured human melee multiplier to tamed maid {}",
                        maid.getUUID()
                );
            }
        }
    }

    private static boolean isTaczBulletDamage(MaidHurtEvent event) {
        return GunSupport.get().isBullet(event.getSource());
    }
}
