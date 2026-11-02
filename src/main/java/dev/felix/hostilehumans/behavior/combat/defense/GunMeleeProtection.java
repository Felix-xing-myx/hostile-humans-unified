package dev.felix.hostilehumans.behavior.combat.defense;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.tacz.TaczGunItemIdentity;
import dev.felix.hostilehumans.damage.HumanMeleeDamage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/** Final direct-melee safety net, independent of gun backend/configuration. */
public final class GunMeleeProtection {
    private GunMeleeProtection() { }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, GunMeleeProtection::onAttack);
        if (ModList.get().isLoaded("tacz")) TaczGunItemIdentity.registerMeleeProtection();
    }

    static void onAttack(LivingAttackEvent event) {
        if (!event.getEntity().level().isClientSide && blocks(event.getSource())) event.setCanceled(true);
    }

    public static boolean blocks(DamageSource source) {
        if (!(source.getEntity() instanceof Human human) || human.hhu$canMeleeAttack()) return false;
        return HumanMeleeDamage.isDirectAttack(source);
    }
}
