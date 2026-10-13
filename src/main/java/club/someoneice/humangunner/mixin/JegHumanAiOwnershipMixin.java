package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HHU owns Human combat, equipment and ammo; native JEG mobs keep their original AI. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.faction.GunnerMobSpawner", remap = false)
public abstract class JegHumanAiOwnershipMixin {
    @Inject(method = "reassessWeaponGoal(Lnet/minecraft/world/entity/PathfinderMob;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void hostileHumans$keepOwnWeaponGoal(PathfinderMob mob, CallbackInfo ci) {
        if (mob instanceof Human) ci.cancel();
    }
    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void hostileHumans$keepOwnTick(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof Human) ci.cancel();
    }
    @Inject(method = "onEntityJoinWorld", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void hostileHumans$keepOwnJoin(EntityJoinLevelEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof Human) ci.cancel();
    }
    @Inject(method = "onSpecialSpawn", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void hostileHumans$keepOwnSpawn(MobSpawnEvent.FinalizeSpawn event, CallbackInfo ci) {
        if (event.getEntity() instanceof Human) ci.cancel();
    }
}
