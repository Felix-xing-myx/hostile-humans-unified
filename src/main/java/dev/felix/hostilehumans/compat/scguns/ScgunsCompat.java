package dev.felix.hostilehumans.compat.scguns;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/** Optional-module gate. Core code never links Scorched Guns types. */
public final class ScgunsCompat {
    private ScgunsCompat() {}
    public static boolean enabled() { return ModList.get().isLoaded("scguns") && UnifiedConfig.get().scguns().enabled(); }
    /** Core damage-source hook stays free of optional projectile/API classes. */
    public static boolean usesIndependentDamage(net.minecraft.world.damagesource.DamageSource source) {
        return source.getEntity() instanceof Human && enabled() && ScgunsIntegration.isHumanBullet(source);
    }
    static boolean launchFlare(ServerPlayer player) { return enabled() && ScgunsRaidBridge.launch(player); }
}
