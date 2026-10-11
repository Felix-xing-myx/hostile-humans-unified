package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Final firing permission, shared by ordinary fire, retreat fire and native events. */
public final class GunFireAuthorization {
    private GunFireAuthorization() { }

    public static boolean allows(Human human, ItemStack weapon, LivingEntity target) {
        if (!(human.level() instanceof ServerLevel level) || !human.isAlive() || human.isRemoved()
                || human.isNoAi() || human.isSleeping() || human.isActivelyCollectingLoot()
                || HumanGunner.isManualInventoryOpen(human)
                || weapon != human.getMainHandItem() || !HumanGunAcceptance.accepts(weapon)
                || !GunSupport.isGunItem(weapon)) return false;
        if (target == null || target == human || target != human.getTarget()
                || !target.isAlive() || target.isRemoved() || target.isSpectator()
                || target.level() != level || level.getEntity(target.getUUID()) != target
                || !human.canAttack(target) || HumanRelations.allied(human, target)) return false;
        if (BodyguardProtection.isBodyguard(human)) {
            BodyguardProtection.update(human);
            if (BodyguardProtection.attack(human) != target) return false;
        }
        double reach = GunRangePolicy.firingReach(GunRangePolicy.forType(GunSupport.get().gunType(weapon)),
                human.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE));
        return human.distanceToSqr(target) <= reach * reach && RangedShotClearance.canShoot(human, target);
    }
}
