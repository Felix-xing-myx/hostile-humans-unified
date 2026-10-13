package club.someoneice.humangunner;

/**
 * TaCZ's internal ammo boundaries, kept together for upstream compatibility review.
 * These strings have no runtime TaCZ linkage, so the @Pseudo mixins remain optional.
 * A missing or duplicated required invocation must fail Mixin application: silently
 * skipping any of them could grant free ammo or announce an unpaid reload.
 */
public final class TaczAmmoMixinContract {
    private TaczAmmoMixinContract() {}

    public static final String RELOAD = "reload()V";
    public static final String CANCEL_RELOAD = "cancelReload()V";
    public static final String TICK_RELOAD = "tickReloadState()Lcom/tacz/guns/api/entity/ReloadState;";
    // Mixin 0.8.5 MemberInfo supports an omitted name with a descriptor and '*'
    // quantifier. Search every matching body, then require exactly one invocation
    // per redirect, rather than choosing the first method with this signature.
    // Reload redirects also search RELOAD itself in case upstream inlines the
    // body. The exact INVOKE target and require=1/allow=1 apply across both paths.
    public static final String RELOAD_BODY = "*(Lcom/tacz/guns/api/item/gun/AbstractGunItem;"
            + "Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/index/CommonGunIndex;)V";
    public static final String SHOOT = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;JFZ)"
            + "Lcom/tacz/guns/api/entity/ShootResult;";
    // TaCZ 1.1.5 through 1.1.7 use the same ammo boundary with a shorter entry signature.
    public static final String SHOOT_LEGACY = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)"
            + "Lcom/tacz/guns/api/entity/ShootResult;";
    public static final String SCRIPT_INVENTORY_AMMO = "useInventoryAmmo()Z";
    public static final String SCRIPT_SHOOT_ONCE = "shootOnce(Z)V";

    public static final String RELOAD_INVENTORY_AMMO = "Lcom/tacz/guns/api/item/gun/AbstractGunItem;"
            + "useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z";
    public static final String SHOOT_INVENTORY_AMMO = "Lcom/tacz/guns/api/item/IGun;"
            + "useInventoryAmmo(Lnet/minecraft/world/item/ItemStack;)Z";
    public static final String START_RELOAD = "Lcom/tacz/guns/api/item/gun/AbstractGunItem;startReload("
            + "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/entity/LivingEntity;)Z";
    public static final String RELOAD_NOTIFICATION = "Lcom/tacz/guns/network/NetworkHandler;"
            + "sendToTrackingEntity(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V";
    public static final String GUN_TICK_RELOAD = "Lcom/tacz/guns/api/item/gun/AbstractGunItem;tickReload("
            + "Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/api/entity/ReloadState;";
}
