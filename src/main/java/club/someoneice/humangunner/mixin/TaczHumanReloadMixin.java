package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.TaczHumanReloadTransactions;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exact TaCZ 1.1.8-hotfix boundaries; absent TaCZ is supported by @Pseudo. */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityReload", remap = false)
public abstract class TaczHumanReloadMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, require = 1)
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
            TaczHumanReloadTransactions.forgetFinished(data);
        }
    }

    @Inject(method = "cancelReload", at = @At("HEAD"), cancellable = true, require = 1)
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
            ItemStack weapon = human.getMainHandItem();
            if (weapon.getItem() instanceof AbstractGunItem gun && gun.useInventoryAmmo(weapon)) {
                TaczHumanReloadTransactions.end(data);
                ci.cancel();
            }
        }
    }

    @Redirect(method = "lambda$reload$0", at = @At(value = "INVOKE", target =
            "Lcom/tacz/guns/api/item/gun/AbstractGunItem;useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z"), require = 1)
    private boolean humanGunner$useNpcMagazine(AbstractGunItem gun, ItemStack weapon) {
        return !(shooter instanceof Human) && gun.useInventoryAmmo(weapon);
    }

    @Redirect(method = "lambda$reload$0", at = @At(value = "INVOKE", target =
            "Lcom/tacz/guns/api/item/gun/AbstractGunItem;startReload(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"), require = 1)
    private boolean humanGunner$paidStart(AbstractGunItem gun, ShooterDataHolder holder,
                                         ItemStack weapon, LivingEntity entity) {
        return TaczHumanReloadTransactions.start(gun, holder, weapon, entity);
    }

    @Redirect(method = "tickReloadState", at = @At(value = "INVOKE", target =
            "Lcom/tacz/guns/api/item/gun/AbstractGunItem;tickReload(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/api/entity/ReloadState;"), require = 1)
    private ReloadState humanGunner$paidTick(AbstractGunItem gun, ShooterDataHolder holder,
                                            ItemStack weapon, LivingEntity entity) {
        return TaczHumanReloadTransactions.tick(gun, holder, weapon, entity);
    }

    @Inject(method = "cancelReload", at = @At("RETURN"), require = 1)
    private void humanGunner$endPayment(CallbackInfo ci) {
        if (shooter instanceof Human) TaczHumanReloadTransactions.end(data);
    }
}
