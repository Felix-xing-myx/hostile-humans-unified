package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/** Passive protection follows the equipment present at each effective hit, never item-use state. */
public final class GunnerShieldProtection {
    private GunnerShieldProtection() { }

    static boolean isUsableShield(ItemStack shield) {
        return !shield.isEmpty() && SpartanEquipmentCompat.isShield(shield)
                && (shield.getItem() instanceof ShieldItem
                || shield.canPerformAction(ToolActions.SHIELD_BLOCK)
                || shield.getUseAnimation() == UseAnim.BLOCK);
    }

    public static void onDamage(LivingDamageEvent event) {
        float incoming = event.getAmount();
        if (event.isCanceled() || !Float.isFinite(incoming) || incoming <= 0.0F
                || !(event.getEntity() instanceof Human human) || human.level().isClientSide
                || !GunSupport.isGunItem(human.getMainHandItem())
                || !isUsableShield(human.getOffhandItem())) {
            return;
        }
        double reduction = UnifiedConfig.get().gunSetting("offhand_shield_damage_reduction", .2);
        if (!Double.isFinite(reduction) || reduction <= 0.0D) return;
        reduction = Math.min(1.0D, reduction);

        // No source-tag exclusions: even armor/effect-bypassing damage receives
        // this equipment bonus. Running after absorption skips fully absorbed hits.
        event.setAmount(incoming * (float) (1.0D - reduction));
        ShieldDurability.damagePassiveOffhandShield(human, incoming);
    }
}
