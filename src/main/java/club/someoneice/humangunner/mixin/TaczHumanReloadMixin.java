package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.TaczAmmoMixinContract;

import club.someoneice.humangunner.TaczHumanReloadTransactions;
import club.someoneice.humangunner.HumanAmmo;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.event.ServerMessageGunReload;
import net.minecraft.world.entity.Entity;
import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Required TaCZ ammo boundaries; absent TaCZ is supported by @Pseudo. */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityReload", remap = false)
public abstract class TaczHumanReloadMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private boolean humanGunner$reboundReload;

    @Inject(method = TaczAmmoMixinContract.TICK_RELOAD, at = @At("HEAD"), cancellable = true, require = 1)
    private void humanGunner$bindBackgroundReload(CallbackInfoReturnable<ReloadState> cir) {
        humanGunner$reboundReload = false;
        if (shooter instanceof Human human && !human.level().isClientSide) {
            boolean active = data.reloadTimestamp != -1L;
            if (!TaczHumanReloadTransactions.prepareNativeTick(human, data)) {
                cir.setReturnValue(new ReloadState());
            } else {
                humanGunner$reboundReload = active;
            }
        }
    }

    @Inject(method = TaczAmmoMixinContract.TICK_RELOAD, at = @At("RETURN"), require = 1)
    private void humanGunner$restoreHeldBinding(CallbackInfoReturnable<ReloadState> cir) {
        if (humanGunner$reboundReload && shooter instanceof Human human && !human.level().isClientSide)
            TaczHumanReloadTransactions.restoreHeldBinding(human, data);
        humanGunner$reboundReload = false;
    }

    @Inject(method = TaczAmmoMixinContract.RELOAD, at = @At("HEAD"), cancellable = true, require = 1)
    private void humanGunner$discardFinishedPayment(CallbackInfo ci) {
        if (shooter instanceof Human human) {
            var server = human.getServer();
            if (server == null || human.level().isClientSide) { ci.cancel(); return; }
            if (!server.isSameThread()) {
                server.execute(() -> {
                    if (human.isAlive()) IGunOperator.fromLivingEntity(human).reload();
                });
                ci.cancel();
                return;
            }
            if (human.isUsingItem() || (!data.reloadStateType.isReloading()
                    && !HumanAmmo.canReload(human, human.getMainHandItem()))) {
                ci.cancel();
                return;
            }
            TaczHumanReloadTransactions.forgetFinished(data);
        }
    }

    @Inject(method = TaczAmmoMixinContract.CANCEL_RELOAD, at = @At("HEAD"), cancellable = true, require = 1)
    private void humanGunner$cancelOnServerThread(CallbackInfo ci) {
        if (shooter instanceof Human human) {
            var server = human.getServer();
            if (server == null || human.level().isClientSide) { ci.cancel(); return; }
            if (!server.isSameThread()) {
                server.execute(() -> IGunOperator.fromLivingEntity(human).cancelReload());
                ci.cancel();
                return;
            }
            // Inventory-feed cancellation belongs to the NPC transaction, not
            // a gun-pack script designed for an endless inventory supply.
            if (!TaczHumanReloadTransactions.prepareNativeTick(human, data)) {
                ci.cancel();
                return;
            }
            ItemStack weapon = data.currentGunItem == null ? human.getMainHandItem() : data.currentGunItem.get();
            if (weapon.getItem() instanceof AbstractGunItem gun && gun.useInventoryAmmo(weapon)) {
                TaczHumanReloadTransactions.end(data);
                TaczHumanReloadTransactions.restoreHeldBinding(human, data);
                ci.cancel();
            }
        }
    }

    @Redirect(method = {TaczAmmoMixinContract.RELOAD, TaczAmmoMixinContract.RELOAD_BODY}, at = @At(value = "INVOKE", target =
            TaczAmmoMixinContract.RELOAD_INVENTORY_AMMO), require = 1, allow = 1)
    private boolean humanGunner$useNpcMagazine(AbstractGunItem gun, ItemStack weapon) {
        return !(shooter instanceof Human) && gun.useInventoryAmmo(weapon);
    }

    @Redirect(method = {TaczAmmoMixinContract.RELOAD, TaczAmmoMixinContract.RELOAD_BODY}, at = @At(value = "INVOKE", target =
            TaczAmmoMixinContract.START_RELOAD), require = 1, allow = 1)
    private boolean humanGunner$paidStart(AbstractGunItem gun, ShooterDataHolder holder,
                                         ItemStack weapon, LivingEntity entity) {
        boolean started = TaczHumanReloadTransactions.start(gun, holder, weapon, entity);
        if (started && entity instanceof Human) {
            // Native TaCZ sends this before startReload. NPC authorization may
            // refuse the cycle, so publish animation/sound only after success.
            NetworkHandler.sendToTrackingEntity(new ServerMessageGunReload(entity.getId(), weapon), entity);
        }
        return started;
    }

    @Redirect(method = {TaczAmmoMixinContract.RELOAD, TaczAmmoMixinContract.RELOAD_BODY}, at = @At(value = "INVOKE", target =
            TaczAmmoMixinContract.RELOAD_NOTIFICATION), require = 1, allow = 1)
    private void humanGunner$deferReloadNotification(Object message, Entity entity) {
        if (!(shooter instanceof Human)) NetworkHandler.sendToTrackingEntity(message, entity);
    }

    @Redirect(method = TaczAmmoMixinContract.TICK_RELOAD, at = @At(value = "INVOKE", target =
            TaczAmmoMixinContract.GUN_TICK_RELOAD), require = 1, allow = 1)
    private ReloadState humanGunner$paidTick(AbstractGunItem gun, ShooterDataHolder holder,
                                            ItemStack weapon, LivingEntity entity) {
        return TaczHumanReloadTransactions.tick(gun, holder, weapon, entity);
    }

    @Inject(method = TaczAmmoMixinContract.CANCEL_RELOAD, at = @At("RETURN"), require = 1)
    private void humanGunner$endPayment(CallbackInfo ci) {
        if (shooter instanceof Human human) {
            TaczHumanReloadTransactions.end(data);
            TaczHumanReloadTransactions.restoreHeldBinding(human, data);
        }
    }
}
