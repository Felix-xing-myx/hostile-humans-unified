package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import top.ribs.scguns.entity.throwable.ThrowableItemEntity;

/** Optional-backend boundary for direct throwable damage, before armor/resistance.
 * Native explosion/impact code, ownership, damage type and falloff remain intact.
 * Resolve the real projectile owner rather than nearby Humans or its victim.
 * Event scaling also survives projectile save/load without private-field patches.
 */
final class ScgunsThrowableDamage {
    private ScgunsThrowableDamage() { }

    static void onDamage(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getSource().getDirectEntity() instanceof ThrowableItemEntity projectile)
                || !(projectile.getOwner() instanceof Human)) return;
        ScgunsSettings settings = UnifiedConfig.get().scguns();
        if (!settings.enabled()) return;
        String id = BuiltInRegistries.ITEM.getKey(projectile.getItem().getItem()).toString();
        Double multiplier = settings.throwableDamageMultipliers().get(id);
        // No entry for gas, swarm, choke or beacon: their indirect effects remain native.
        if (multiplier != null) event.setAmount(event.getAmount() * multiplier.floatValue());
    }
}
