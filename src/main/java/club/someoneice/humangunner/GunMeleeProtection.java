package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

/** Final direct-melee safety net, independent of gun backend/configuration. */
public final class GunMeleeProtection {
    private static final ResourceLocation HUMAN = new ResourceLocation("hostile_humans_unified", "human");
    private static final ResourceLocation NAMED_HUMAN = new ResourceLocation("hostile_humans_unified", "human_with_name");

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
        // Indirect arrows, gun bullets, explosions and reflected/effect damage are
        // separate actions. Never classify them by the shooter's current weapon.
        if (source.getDirectEntity() != human || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION) || GunSupport.get().isBullet(source)) return false;
        ResourceLocation type = source.typeHolder().unwrapKey().map(key -> key.location()).orElse(null);
        // DamageSourceMix replaces vanilla mobAttack with these two custom types.
        return source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || source.is(DamageTypes.PLAYER_ATTACK) || HUMAN.equals(type) || NAMED_HUMAN.equals(type);
    }
}
