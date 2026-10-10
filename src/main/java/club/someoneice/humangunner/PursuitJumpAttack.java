package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** A bounded pursuit opportunity, not another attack scheduler or movement goal. Server-only. */
public final class PursuitJumpAttack {
    private static final UUID CRITICAL = UUID.fromString("ca94167a-485c-442b-a82e-fbdb75db8f04");
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        UUID target;
        net.minecraft.world.item.ItemStack weapon = net.minecraft.world.item.ItemStack.EMPTY;
        int nextAttempt, started = -100;
        boolean airborne;
    }
    private PursuitJumpAttack() { }

    public static void tryStart(Human human, LivingEntity target, int attackCooldown) {
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (active(human) || human.tickCount < state.nextAttempt || attackCooldown > 2
                || !human.onGround() || human.isFleeing || human.isUsingItem()
                || human.isPassenger() || human.onClimbable() || human.isInWater() || human.isInLava()
                || SoldierOrder.isHoldingPosition(human) || !human.humanGunner$canMeleeAttack()
                || human.humanGunner$hasPendingMeleeAttack()
                || com.craftix.hostile_humans.HumanUtil.isRangedWeapon(human.getMainHandItem())
                || com.craftix.hostile_humans.HumanUtil.isTrident(human.getMainHandItem())
                || !human.canAttack(target) || !human.getSensing().hasLineOfSight(target)
                || Math.abs(target.getY() - human.getY()) > 0.75D
                || CombatTerrainNavigation.hasWork(human, target)) return;
        state.nextAttempt = human.tickCount + 4;
        var path = human.getNavigation().getPath();
        if (path == null || path.isDone()) return;
        Vec3 direction = NavigationSupport.horizontalDirection(human.position(), target.position());
        Vec3 velocity = human.getDeltaMovement().multiply(1, 0, 1);
        double forward = velocity.dot(direction);
        Vec3 route = NavigationSupport.horizontalDirection(human.position(),
                Vec3.atBottomCenterOf(path.getNextNodePos()));
        if (forward < 0.06D || route.dot(direction) < 0.80D) return;
        double closing = forward - target.getDeltaMovement().multiply(1, 0, 1).dot(direction);
        double reach = MeleeCombatRange.reach(human, target);
        double distance = human.distanceTo(target);
        if (closing < 0.04D || distance > reach + 1.5D
                || distance <= Math.max(MeleeSpacing.preferredDistance(reach) + 0.35D, reach - 0.5D)) return;
        int upswing = BetterCombatMeleeCombat.upswingTicks(human);
        // Normal jump flight is about 12 ticks. Reserve takeoff, remaining cooldown and impact time.
        int approachTicks = 12 - upswing - Math.max(0, attackCooldown) - 2;
        if (approachTicks < 2 || distance > reach + Math.min(1.5D, closing * approachTicks)) return;
        CombatAiConfig config = CombatAiConfig.forHuman(human);
        if (!config.enabled()) return;
        state.nextAttempt = human.tickCount + human.getRandom().nextInt(
                config.combatJumpIntervalMin(), config.combatJumpIntervalMax() + 1);
        if (human.getRandom().nextDouble() >= config.combatJumpChance()
                || !HumanTerrainSafety.safeCombatJump(human)) return;
        state.target = target.getUUID();
        state.weapon = human.getMainHandItem().copy();
        state.started = human.tickCount;
        state.airborne = false;
        human.getJumpControl().jump();
    }

    public static boolean active(Human human) {
        State state = STATES.get(human);
        if (state == null || state.target == null) return false;
        LivingEntity target = human.getTarget();
        if (target == null || !target.isAlive() || !state.target.equals(target.getUUID())
                || human.isFleeing || !human.humanGunner$canMeleeAttack()
                || !net.minecraft.world.item.ItemStack.isSameItemSameTags(state.weapon, human.getMainHandItem())
                || human.tickCount - state.started > 30 || human.isInWater() || human.isInLava()
                || human.isPassenger() || human.onClimbable()) {
            state.target = null;
            return false;
        }
        if (!human.onGround()) state.airborne = true;
        else if (state.airborne || human.tickCount - state.started > 3) {
            state.target = null;
            return false;
        }
        return true;
    }

    /** Give the jump controller a tick to leave the ground; never postpone an ordinary attack. */
    public static boolean awaitingTakeoff(Human human) {
        return active(human) && human.onGround();
    }

    /** Evaluate at actual impact, including Better Combat's delayed upswing and each sweep victim. */
    public static boolean strike(Human human, BooleanSupplier attack) {
        if (!active(human) || human.onGround() || UnifiedConfig.get().vanillaMode())
            return attack.getAsBoolean();
        var damage = human.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) return attack.getAsBoolean();
        double multiplier = UnifiedConfig.get().damage("jump_attack_damage_multiplier", 1.5D);
        damage.addTransientModifier(new AttributeModifier(CRITICAL, "Pursuit jump critical", multiplier - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        try { return attack.getAsBoolean(); }
        finally { damage.removeModifier(CRITICAL); }
    }
}
