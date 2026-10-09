package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidHurtEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.Difficulty;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.concurrent.atomic.AtomicBoolean;

/** Optional Touhou Little Maid integration; this class is only loaded when the mod is present. */
public final class TouhouMaidCompat {
    private static final AtomicBoolean LOGGED_FIRST_GUN_HIT = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_MELEE_HIT = new AtomicBoolean();

    private TouhouMaidCompat() {
    }

    public static boolean isMaid(LivingEntity entity) { return entity instanceof EntityMaid; }

    public static void register() {
        if (GunSupport.get().enabled()) TaczMaidIntegration.register();
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TouhouMaidCompat::onMaidTick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TouhouMaidCompat::onMaidHumanAttack);
        MinecraftForge.EVENT_BUS.addListener(
                EventPriority.LOWEST,
                true,
                MaidHurtEvent.class,
                TouhouMaidCompat::onTamedMaidHurt
        );
        HumanGunner.LOGGER.info("Hostile Humans Unified enabled optional Touhou Little Maid relation and damage integration");
    }

    /** Null leaves every non-human target and untamed maid's original rules alone. */
    public static Boolean humanAttackDecision(LivingEntity entity, LivingEntity target) {
        if (!(entity instanceof EntityMaid maid) || maid.getOwnerUUID() == null
                || !(target instanceof Human human)) return null;
        if (!(maid.getTask() instanceof IAttackTask) || !human.isAlive()
                || maid.level().getDifficulty() == Difficulty.PEACEFUL
                || maid.isAlliedTo(human) || human.isAlliedTo(maid)) return false;
        if (!MaidTargetRules.permits(maid, human)) return false;
        return relationAllowsAttack(maid, human);
    }

    private static boolean relationAllowsAttack(EntityMaid maid, Human human) {
        Player owner = maid.getOwner() instanceof Player player ? player : null;
        boolean defending = owner != null && (recentAttackOn(human, maid) || recentAttackOn(human, owner));
        return MaidHumanRelationPolicy.allowsAttack(owner != null, HumanRelations.effectiveOwner(human) != null,
                owner == null ? -1 : IdentityBadgeAccess.highestClearance(owner), IdentityBadgeAccess.humanRank(human),
                owner != null && HumanRelations.isForcedHostileTo(human, owner), defending);
    }

    private static boolean recentAttackOn(Human human, LivingEntity victim) {
        int elapsed = victim.tickCount - victim.getLastHurtByMobTimestamp();
        return victim.getLastHurtByMob() == human && elapsed >= 0 && elapsed <= 600;
    }

    private static void onMaidTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide
                || maid.getOwnerUUID() == null) return;
        var brain = maid.getBrain();
        LivingEntity target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target instanceof Human && Boolean.FALSE.equals(humanAttackDecision(maid, target))) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            // Remove only pursuit/look memories pointing at that former enemy, never unrelated work paths.
            var walk = brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if (walk != null && walk.getTarget() instanceof EntityTracker tracker && tracker.getEntity() == target) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                maid.getNavigation().stop();
            }
            var look = brain.getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
            if (look instanceof EntityTracker tracker && tracker.getEntity() == target)
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            if (maid.getTarget() == target) maid.setTarget(null);
        }
        if (maid.getTarget() instanceof Human human && Boolean.FALSE.equals(humanAttackDecision(maid, human)))
            maid.setTarget(null);
    }

    /** Also guards projectiles already in flight when the owner changes badges. */
    private static void onMaidHumanAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Human human && event.getSource().getEntity() instanceof EntityMaid maid
                && maid.getOwnerUUID() != null && !relationAllowsAttack(maid, human)) event.setCanceled(true);
    }

    private static void onTamedMaidHurt(MaidHurtEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.getOwnerUUID() == null) {
            return;
        }

        if (isTaczBulletDamage(event)) {
            event.setCanceled(false);
            event.setAmount(event.getAmount() * (float) UnifiedConfig.get().gunSetting("tamed_maid_damage_multiplier", .15));
            if (LOGGED_FIRST_GUN_HIT.compareAndSet(false, true)) {
                HumanGunner.LOGGER.info(
                        "Hostile Humans Unified applied configured TaCZ damage multiplier to tamed maid {}",
                        maid.getUUID()
                );
            }
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Human && event.getSource().getDirectEntity() == attacker) {
            if (UnifiedConfig.get().vanillaMode()) return;
            event.setAmount(event.getAmount() * (float) UnifiedConfig.get().damage("human_melee_vs_tamed_maid_multiplier", .25));
            if (LOGGED_FIRST_MELEE_HIT.compareAndSet(false, true)) {
                HumanGunner.LOGGER.info(
                        "Hostile Humans Unified applied configured human melee multiplier to tamed maid {}",
                        maid.getUUID()
                );
            }
        }
    }

    private static boolean isTaczBulletDamage(MaidHurtEvent event) {
        return GunSupport.get().isBullet(event.getSource());
    }
}
