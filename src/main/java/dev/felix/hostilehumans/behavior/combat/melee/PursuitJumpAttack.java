package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.CombatAiConfig;
import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.equipment.EquipmentArchetypes;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.navigation.CombatTerrainNavigation;
import dev.felix.hostilehumans.navigation.HumanMovementModifiers;
import dev.felix.hostilehumans.navigation.HumanTerrainSafety;
import dev.felix.hostilehumans.navigation.NavigationSupport;
import dev.felix.hostilehumans.soldier.OrderDistances;
import dev.felix.hostilehumans.soldier.SoldierOrder;

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
        int started = -100;
        UUID engagement;
        boolean firstUsed;
        final MeleeJumpPolicy opportunity = new MeleeJumpPolicy();
        dev.felix.hostilehumans.behavior.ActionCoordinator.Token token;
        int orderSignature;
        boolean airborne;
        int attackLead;
    }
    private PursuitJumpAttack() { }

    public static void tryStart(Human human, LivingEntity target, int attackCooldown) {
        JumpAttackConfig jump = UnifiedConfig.get().jumpAttack();
        if (target == null || !jump.enabled() || !CombatAiConfig.forHuman(human).enabled()
                || human.level().isClientSide || !human.isAlive() || human.isRemoved() || human.isNoAi()
                || HumanInventorySession.isEditing(human) || !NpcDualWield.layoutReady(human)) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (!target.getUUID().equals(state.engagement)) {
            state.engagement = target.getUUID(); state.firstUsed = false;
        }
        if (active(human) || attackCooldown > 0 || !MeleeAttackTiming.ready(human)
                || !human.onGround() || human.isFleeing || human.isUsingItem()
                || human.isPassenger() || human.onClimbable() || human.isInWater() || human.isInLava()
                || SoldierOrder.isHoldingPosition(human) || !human.hhu$canMeleeAttack()
                || human.hhu$hasPendingMeleeAttack()
                || dev.felix.hostilehumans.entity.HumanUtil.isRangedWeapon(human.getMainHandItem())
                || dev.felix.hostilehumans.entity.HumanUtil.isTrident(human.getMainHandItem())
                || !human.canAttack(target) || !human.getSensing().hasLineOfSight(target)
                || Math.abs(target.getY() - human.getY()) > jump.maxHeightDifference()
                || CombatTerrainNavigation.hasWork(human, target)) return;
        double reach = MeleeCombatRange.reach(human, target);
        double distance = human.distanceTo(target);
        if (distance > reach + Math.min(1.25D, jump.approachExtra()) || !safeTakeoff(human, target, distance > reach)) return;
        // Preserve the existing path/closing/upswing forecast for the guaranteed first pursuit.
        boolean first = !state.firstUsed && firstApproach(human, target, attackCooldown, reach, distance, jump);
        var identity = EquipmentArchetypes.describe(human, human.getMainHandItem());
        boolean reinforced = HumanCombatDirector.strategies(human).reinforcedJump(identity.core(), identity.enabled()) && identity.category().equals("melee");
        if (!first) {
            if (!reinforced || !state.opportunity.mayRoll(human.tickCount, attackCooldown, human.onGround(), true)) return;
            state.opportunity.rolled(human.tickCount, human.getRandom().nextInt(jump.intervalMinTicks(), jump.intervalMaxTicks() + 1));
            if (human.getRandom().nextDouble() >= jump.chance()) return;
        }
        var actions = HumanBehaviorControl.actions(human);
        state.token = actions.request("melee:jump", java.util.EnumSet.of(
                dev.felix.hostilehumans.behavior.ActionCoordinator.Resource.MOVE_MODIFIER), 50, true, 35);
        if (!actions.acquire(state.token)) {
            actions.finish(state.token, dev.felix.hostilehumans.behavior.ActionCoordinator.Status.CANCELLED, "jump movement unavailable");
            state.token = null; return;
        }
        state.firstUsed = true;
        state.orderSignature = OrderDistances.signature(human);
        state.target = target.getUUID();
        state.weapon = human.getMainHandItem();
        state.started = human.tickCount;
        state.attackLead = MeleeJumpPolicy.attackLeadTicks(BetterCombatMeleeCombat.upswingTicks(human));
        state.airborne = false;
        human.setSprinting(distance > reach);
        HumanMovementModifiers.withToken(human, state.token, () -> human.getJumpControl().jump());
    }

    private static boolean safeTakeoff(Human human, LivingEntity target, boolean chasing) {
        Vec3 heading = NavigationSupport.horizontalDirection(human.position(), target.position());
        Vec3 landing = heading.scale(chasing ? 1.25 : .2);
        var context = OrderDistances.context(human);
        if (context.constrained() && (!context.available() || !context.insideCombat(human.position().add(landing)))) return false;
        return HumanTerrainSafety.safeCombatJump(human) && HumanTerrainSafety.safeStep(human, landing)
                && human.level().noCollision(human, human.getBoundingBox().move(0, 1.1, 0).expandTowards(landing));
    }
    private static boolean firstApproach(Human human, LivingEntity target, int cooldown, double reach,
                                         double distance, JumpAttackConfig jump) {
        var path = human.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        Vec3 direction = NavigationSupport.horizontalDirection(human.position(), target.position());
        double forward = human.getDeltaMovement().multiply(1, 0, 1).dot(direction);
        Vec3 route = NavigationSupport.horizontalDirection(human.position(), Vec3.atBottomCenterOf(path.getNextNodePos()));
        if (forward < .06 || route.dot(direction) < .80) return false;
        double closing = forward - target.getDeltaMovement().multiply(1, 0, 1).dot(direction);
        if (closing < .04 || distance <= Math.max(MeleeSpacing.preferredDistance(reach) + .35, reach - .5)) return false;
        int upswing = BetterCombatMeleeCombat.upswingTicks(human);
        int approachTicks = 12 - upswing - MeleeJumpPolicy.attackLeadTicks(upswing) - Math.max(0, cooldown);
        return approachTicks >= 2 && distance <= reach + Math.min(Math.min(1.25, jump.approachExtra()), closing * approachTicks);
    }
    public static void markEngaged(Human human) {
        State state = STATES.get(human);
        if (state != null) state.firstUsed = true;
    }
    private static void end(Human human, boolean completed, String reason) {
        State state = STATES.get(human);
        if (state == null) return;
        state.target = null;
        if (state.token != null) HumanBehaviorControl.actions(human).finish(state.token, completed
                ? dev.felix.hostilehumans.behavior.ActionCoordinator.Status.COMPLETED
                : dev.felix.hostilehumans.behavior.ActionCoordinator.Status.CANCELLED, reason);
        state.token = null;
    }
    public static void cancel(Human human, String reason) { end(human, false, reason); }
    /** Unload/death/session cleanup removes engagement too; target changes never reset the roll clock. */
    public static void clear(Human human, String reason) { cancel(human, reason); STATES.remove(human); }

    public static boolean active(Human human) {
        State state = STATES.get(human);
        if (state == null || state.target == null) return false;
        LivingEntity target = human.getTarget();
        if (!UnifiedConfig.get().jumpAttack().enabled() || target == null || !target.isAlive() || !state.target.equals(target.getUUID())
                || human.isFleeing || !human.isAlive() || human.isRemoved() || human.isNoAi()
                || HumanInventorySession.isEditing(human) || !human.hhu$canMeleeAttack()
                || state.weapon != human.getMainHandItem() || state.orderSignature != OrderDistances.signature(human)
                || SoldierOrder.isHoldingPosition(human) || !HumanBehaviorControl.actions(human).running(state.token)
                || human.tickCount - state.started > 30 || human.isInWater() || human.isInLava()
                || human.isPassenger() || human.onClimbable()) {
            cancel(human, "jump context changed");
            return false;
        }
        if (!human.onGround()) state.airborne = true;
        else if (state.airborne || human.tickCount - state.started > 3) {
            end(human, true, "jump landed");
            return false;
        }
        return true;
    }

    /** Only this intentional jump delays its own attack: visible takeoff precedes the weapon upswing. */
    public static boolean awaitingAttackWindow(Human human) {
        if (!active(human)) return false;
        State state = STATES.get(human);
        return MeleeJumpPolicy.waitingForAttack(human.tickCount-state.started, human.onGround(), state.attackLead);
    }

    /** Evaluate at actual impact, including Better Combat's delayed upswing and each sweep victim. */
    public static boolean strike(Human human, BooleanSupplier attack) {
        if (!active(human) || human.onGround() || UnifiedConfig.get().vanillaMode())
            return attack.getAsBoolean();
        var damage = human.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) return attack.getAsBoolean();
        double multiplier = UnifiedConfig.get().jumpAttack().damageMultiplier();
        damage.addTransientModifier(new AttributeModifier(CRITICAL, "Pursuit jump critical", multiplier - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        try { return attack.getAsBoolean(); }
        finally { damage.removeModifier(CRITICAL); }
    }
}
