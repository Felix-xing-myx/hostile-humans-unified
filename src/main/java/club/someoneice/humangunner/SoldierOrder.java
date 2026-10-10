package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Persistent owner-issued movement policy for hired humans. */
public enum SoldierOrder {
    HOLD_POSITION,
    GUARD,
    FOLLOW,
    PATROL,
    BODYGUARD;

    private static final java.util.Map<Human, net.minecraft.world.level.pathfinder.Path> ROUTES = new java.util.WeakHashMap<>();
    static final String ORDER = "hostile_humans_unified:soldier_order";
    private static final String ANCHOR = "hostile_humans_unified:order_anchor";
    private static final String PATROL_DESTINATION = "hostile_humans_unified:patrol_destination";
    private static final String NEXT_PATROL = "hostile_humans_unified:next_patrol_move";
    private static final String RETURNING_FROM_RETREAT = "hostile_humans_unified:returning_from_retreat";
    private static final double FOLLOW_COMBAT_RANGE_SQR = 48.0D * 48.0D;
    private static final double PATROL_RANGE_SQR = 32.0D * 32.0D;
    // Match the native idle stroll goals instead of using combat/return speed.
    private static final double PATROL_WALK_NAVIGATION_SPEED = 0.65D;
    private static final double HOLD_RETURN_DISTANCE_SQR = 0.8D * 0.8D;

    public static SoldierOrder get(Human human) {
        String value = human.getPersistentData().getString(ORDER);
        try {
            return value.isEmpty() ? FOLLOW : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return FOLLOW;
        }
    }

    public static void set(Human human, SoldierOrder order) {
        set(human, order, false);
    }

    static void set(Human human, SoldierOrder order, boolean preserveNavigation) {
        PursuitNavigation.clear(human);
        BodyguardProtection.clear(human);
        SoldierFollowNavigation.clear(human);
        SoldierStuckRecovery.clear(human);
        human.getPersistentData().remove(PATROL_DESTINATION);
        human.guardCombatState.reset();
        human.getPersistentData().putString(ORDER, order.name());
        human.getPersistentData().putLong(ANCHOR, human.blockPosition().asLong());
        human.getPersistentData().remove(NEXT_PATROL);
        human.getPersistentData().remove(RETURNING_FROM_RETREAT);
        if (!preserveNavigation) human.getNavigation().stop();
        if (order != FOLLOW) human.setTarget(null);
    }

    static void initialize(Human human) {
        if (!human.getPersistentData().contains(ORDER)) {
            set(human, FOLLOW);
        }
    }

    static void clear(Human human) {
        PursuitNavigation.clear(human);
        BodyguardProtection.clear(human);
        SoldierFollowNavigation.clear(human);
        SoldierStuckRecovery.clear(human);
        human.getPersistentData().remove(PATROL_DESTINATION);
        human.guardCombatState.reset();
        human.getPersistentData().remove(ORDER);
        human.getPersistentData().remove(ANCHOR);
        human.getPersistentData().remove(NEXT_PATROL);
        human.getPersistentData().remove(RETURNING_FROM_RETREAT);
        human.getNavigation().stop();
    }

    static void beginReturnFromRetreat(Human human) {
        if (!human.hasOwner()) return;
        human.getPersistentData().putBoolean(RETURNING_FROM_RETREAT, true);
        human.forgetSoldierTarget();
    }

    public static boolean isReturningFromRetreat(Human human) {
        return human.getPersistentData().getBoolean(RETURNING_FROM_RETREAT);
    }

    /** Lifecycle validation must never assign or stop a navigation route. */
    static void tick(Human human) {
        if (!human.hasOwner() || !(human.level() instanceof ServerLevel level)) return;
        SoldierOrder order = get(human);
        LivingEntity target = human.getTarget();
        ServerPlayer owner = BodyguardProtection.owner(human);
        if (order == BODYGUARD) BodyguardProtection.update(human);
        if (order == GUARD) {
            if (distanceFromAnchorSqr(human, anchor(human)) <= 4.0D) human.guardCombatState.returnedToPost();
            human.guardCombatState.tick(level.getGameTime(),
                    !insideGuardArea(human, human.getX(), human.getZ()), target != null);
        }
        if (target != null && (!target.isAlive() || HumanRelations.allied(human, target)
                || !allowsTarget(human, target)
                || human.soldierCombatMemory.timedOut(target.getUUID(), level.getGameTime())
                || (order == GUARD && human.guardCombatState.returning())
                || ((order == FOLLOW || order == BODYGUARD) && owner != null
                && human.distanceToSqr(owner) > FOLLOW_COMBAT_RANGE_SQR))) human.forgetSoldierTargetWithoutMovement();
    }

    /** An available owner grants surface crossing to follow and protection orders. */
    public static boolean shouldFollowThroughWater(Human human) {
        if (human.isFleeing || human.isInLava()) return false;
        if (human.hasOwner()) return (get(human) == FOLLOW || get(human) == BODYGUARD)
                && BodyguardProtection.owner(human) != null;
        if (!human.getPersistentData().hasUUID(HumanRelations.TEMP_OWNER)) return false;
        var player = human.level().getPlayerByUUID(human.getPersistentData().getUUID(HumanRelations.TEMP_OWNER));
        return player != null && player.isAlive() && !player.isSpectator();
    }

    static void tickMovement(Human human) {
        if (!human.hasOwner() || !(human.level() instanceof ServerLevel level)) return;
        // LavaEscapeGoal owns its route without setting the retreat flag.
        // Neither follow nor a guard anchor may replace that emergency route.
        if (human.isInLava()) {
            SoldierFollowNavigation.clear(human);
            return;
        }
        // The shore goal already owns idle water movement. This lifecycle
        // callback runs outside GoalSelector arbitration, so it must not
        // replace the shore route with a follow/guard/patrol path.
        if (human.isSeekingShore()) return;
        // Survival navigation owns movement until the retreat has actually
        // ended; neither an anchor leash nor owner-follow may pull it back.
        if (human.isFleeing) return;
        ServerPlayer owner = BodyguardProtection.owner(human);

        SoldierOrder order = get(human);
        if (order == PATROL && human.getTarget() == null) {
            // A previous chase can leave the sprint flag set. Patrol paths
            // use the ordinary walking attribute, including potion effects.
            MovementSpeedController.normal(human);
        }
        BlockPos anchor = anchor(human);
        if (order == BODYGUARD) {
            BodyguardProtection.update(human);
            if (BodyguardProtection.protectingFromExplosion(human, BodyguardProtection.threat(human)))
                human.getPersistentData().remove(RETURNING_FROM_RETREAT);
        }
        if (human.getPersistentData().getBoolean(RETURNING_FROM_RETREAT)) {
            int sinceHit = human.tickCount - human.getLastHurtByMobTimestamp();
            if (human.getLastHurtByMob() != null && sinceHit >= 0 && sinceHit <= 10) {
                human.getPersistentData().remove(RETURNING_FROM_RETREAT);
            } else {
                if (human.getTarget() != null) human.forgetSoldierTarget();
                if (order == FOLLOW || order == BODYGUARD) {
                    if (owner != null && human.distanceToSqr(owner) > 16.0D) {
                        SoldierFollowNavigation.tick(human, owner);
                    } else if (owner != null) {
                        human.getPersistentData().remove(RETURNING_FROM_RETREAT);
                        stopMovement(human);
                    }
                } else if (distanceFromAnchorSqr(human, anchor) > 4.0D) {
                    if (human.tickCount % 10 == 0 || human.getNavigation().isDone()) {
                        moveToPost(human, anchor, order == PATROL ? PATROL_WALK_NAVIGATION_SPEED : 1.1D);
                    }
                } else {
                    human.getPersistentData().remove(RETURNING_FROM_RETREAT);
                    stopMovement(human);
                }
                return;
            }
        }
        LivingEntity target = human.getTarget();
        if (order == BODYGUARD) {
            if (owner == null) {
                if (target != null && !allowsTarget(human, target)) human.forgetSoldierTarget();
                BodyguardProtection.clear(human);
                if (human.getTarget() == null) {
                    stopMovement(human);
                    MovementSpeedController.normal(human);
                }
                return;
            }
            // Temporary offline/dimension standby uses the last protection
            // position, without replacing the stored BODYGUARD order.
            human.getPersistentData().putLong(ANCHOR, human.blockPosition().asLong());
            if (human.distanceToSqr(owner) > FOLLOW_COMBAT_RANGE_SQR) {
                human.forgetSoldierTarget();
                teleportBesideOwner(human, owner);
            }
            if (target != null && !BodyguardProtection.allowsTarget(human, target)) human.forgetSoldierTarget();
            BodyguardProtection.update(human);
            return; // The MOVE goal owns protection routes, not this lifecycle callback.
        }
        if (order == GUARD) {
            if (human.guardCombatState.returning() && target != null) {
                human.forgetSoldierTarget();
                target = null;
            }
        }
        if (target != null && (!target.isAlive() || HumanRelations.allied(human, target)
                || !allowsTarget(human, target)
                || human.soldierCombatMemory.timedOut(target.getUUID(), level.getGameTime()))) {
            human.forgetSoldierTarget();
            target = null;
        }

        // All idle orders yield to an authorized pickup route. Holding a
        // fixed position never starts a pickup route, only nearby collection.
        if (target == null && human.isActivelyCollectingLoot()
                && SoldierPickupPolicy.canCollectNow(human)) return;

        if (order == FOLLOW) {
            // Preserve combat navigation when following has no available owner.
            if (owner == null) {
                if (target == null) stopMovement(human);
                return;
            }
            if (human.distanceToSqr(owner) > FOLLOW_COMBAT_RANGE_SQR) {
                human.forgetSoldierTarget();
                target = null;
                teleportBesideOwner(human, owner);
            }
            if (target != null && target.distanceToSqr(owner) > FOLLOW_COMBAT_RANGE_SQR) {
                human.forgetSoldierTarget();
                target = null;
            }
            if (target == null) SoldierFollowNavigation.tick(human, owner);
            else SoldierFollowNavigation.clear(human);
            return;
        }

        double distanceFromAnchor = distanceFromAnchorSqr(human, anchor);
        if (order == HOLD_POSITION) {
            if (distanceFromAnchorSqr(human, anchor) > HOLD_RETURN_DISTANCE_SQR) {
                if (human.tickCount % 5 == 0 || human.getNavigation().isDone()) {
                    moveToPost(human, anchor, 1.1D);
                }
            } else {
                stopMovement(human);
            }
            return;
        }
        double leash = order == GUARD ? 48.0D * 48.0D : PATROL_RANGE_SQR;
        if (order != GUARD && target != null && !isRecentSelfAttacker(human, target)
                && horizontalDistanceSqr(target.getX(), target.getZ(), anchor) > leash) {
            human.forgetSoldierTarget();
            target = null;
        }
        if (target != null) return;

        if (order == GUARD) {
            if (distanceFromAnchor > 4.0D && human.tickCount % 10 == 0) {
                moveToPost(human, anchor, 1.1D);
            } else if (distanceFromAnchor <= 4.0D) {
                stopMovement(human);
            }
            return;
        }

        long now = level.getGameTime();
        if (distanceFromAnchor > PATROL_RANGE_SQR) {
            if (human.tickCount % 10 != 0) return;
            moveToPost(human, anchor, PATROL_WALK_NAVIGATION_SPEED);
            human.getPersistentData().putLong(NEXT_PATROL, now + 80L);
        } else if (human.getNavigation().isDone() && now >= human.getPersistentData().getLong(NEXT_PATROL)) {
            if (!BasicNavigationBudget.tryAcquire(human, 4)) return;
            int paths = 0;
            for (int attempt = 0; attempt < 8 && paths < 4; attempt++) {
                int dx = human.getRandom().nextInt(65) - 32;
                int dz = human.getRandom().nextInt(65) - 32;
                if (dx * dx + dz * dz > 32 * 32) continue;
                BlockPos destination = SafePositions.nearFloor(level, anchor.getX() + dx, anchor.getZ() + dz, anchor.getY());
                if (destination == null) continue;
                paths++;
                if (moveToPostWithGrant(human, destination, PATROL_WALK_NAVIGATION_SPEED)) break;
            }
            BasicNavigationBudget.cancel(human);
            human.getPersistentData().putLong(NEXT_PATROL, now + 80L + human.getRandom().nextInt(81));
        }
    }

    static void stopMovement(Human human) {
        SoldierFollowNavigation.stopOwned(human);
        var route = ROUTES.remove(human);
        if (MovementContinuity.ownsRoute(human, route)) human.getNavigation().stop();
        BasicNavigationBudget.cancel(human);
    }
    private static void moveToPost(Human human, BlockPos anchor, double speed) {
        if (!BasicNavigationBudget.tryAcquire(human, 1)) return;
        moveToPostWithGrant(human, anchor, speed);
        BasicNavigationBudget.cancel(human);
    }
    private static boolean moveToPostWithGrant(Human human, BlockPos anchor, double speed) {
        var old = human.getNavigation().getPath();
        var path = human.getNavigation().createPath(anchor, 0);
        if (path != null && path.canReach() && human.getNavigation().moveTo(path, speed)) {
            ROUTES.put(human, path);
            if (get(human) == PATROL) human.getPersistentData().putLong(PATROL_DESTINATION, anchor.asLong());
            return true;
        }
        if (old != null && !old.isDone()) human.getNavigation().moveTo(old, speed);
        return false;
    }

    public static boolean allowsTarget(Human human, LivingEntity target) {
        // GoalSelector can clear a dead target between canUse and
        // canContinueToUse. A missing target is an ordinary stop condition,
        // never an exceptional soldier-order state.
        if (target == null) return false;
        if (human.hasOwner() && SoldierStuckRecovery.recovering(human)
                && !BodyguardProtection.protectingFromExplosion(human, target)) return false;
        if (!human.hasOwner() || human.level().isClientSide) return true;
        SoldierOrder order = get(human);
        boolean explosionRescue = BodyguardProtection.protectingFromExplosion(human, target);
        boolean guardRetaliation = order == GUARD && human.guardCombatState
                .retaliating(target.getUUID(), human.level().getGameTime());
        boolean guardChase = order == GUARD && (target == human.getTarget()
                || human.guardCombatState.retainsTarget(target.getUUID()))
                && !human.guardCombatState.returning();
        if (!target.isAlive() || target.level() != human.level()
                || (order != BODYGUARD && !guardRetaliation && !guardChase
                && human.distanceToSqr(target) > FOLLOW_COMBAT_RANGE_SQR)
                || (!explosionRescue && !human.soldierCombatMemory.allowed(target.getUUID(), human.level().getGameTime()))
                || !SoldierCombatMode.allowsTarget(human, target)) return false;
        if (human.getPersistentData().getBoolean(RETURNING_FROM_RETREAT)
                && !explosionRescue) return false;
        if (order == GUARD && human.guardCombatState.returning()) return false;
        if (guardRetaliation || guardChase) return true;
        if (order == BODYGUARD) {
            if (BodyguardProtection.owner(human) == null)
                return distanceFromAnchorSqr(human, anchor(human)) <= BodyguardPolicy.CHASE_SQUARED
                        && horizontalDistanceSqr(target.getX(), target.getZ(), anchor(human)) <= BodyguardPolicy.CHASE_SQUARED;
            return BodyguardProtection.allowsTarget(human, target);
        }
        if (human.isFleeing || isRecentSelfAttacker(human, target)) return true;
        if (order == FOLLOW) {
            Player owner = human.level().getPlayerByUUID(human.getOwnerUUID());
            if (owner == null || !owner.isAlive()) return true;
            return owner != null && owner.isAlive()
                    && human.distanceToSqr(owner) <= FOLLOW_COMBAT_RANGE_SQR
                    && target.distanceToSqr(owner) <= FOLLOW_COMBAT_RANGE_SQR;
        }
        return horizontalDistanceSqr(target.getX(), target.getZ(), anchor(human))
                <= (order == GUARD || order == HOLD_POSITION
                ? FOLLOW_COMBAT_RANGE_SQR : PATROL_RANGE_SQR);
    }

    /** Movement destinations share command limits; target permission alone is not a route leash. */
    public static boolean allowsCombatPosition(Human human, Vec3 point) {
        if (!human.hasOwner()) return true;
        SoldierOrder order = get(human);
        if (order == HOLD_POSITION) return false;
        if (order == BODYGUARD) {
            Player owner = BodyguardProtection.owner(human);
            return owner != null ? owner.distanceToSqr(point) <= BodyguardPolicy.CHASE_SQUARED
                    : horizontalDistanceSqr(point.x, point.z, anchor(human)) <= BodyguardPolicy.CHASE_SQUARED;
        }
        if (order == FOLLOW) {
            Player owner = human.level().getPlayerByUUID(human.getOwnerUUID());
            return owner == null || !owner.isAlive() || owner.distanceToSqr(point) <= FOLLOW_COMBAT_RANGE_SQR;
        }
        if (order == GUARD) {
            if (human.guardCombatState.returning()) return false;
            LivingEntity target = human.getTarget();
            // Retain the existing guard chase/retaliation exception.
            if (target != null && allowsTarget(human, target)) return true;
        }
        return horizontalDistanceSqr(point.x, point.z, anchor(human)) <= PATROL_RANGE_SQR;
    }

    public static boolean isHoldingPosition(Human human) {
        if (!human.hasOwner()) return false;
        if (get(human) == HOLD_POSITION) return true;
        var owner = BodyguardProtection.owner(human);
        var target = human.getTarget();
        // Existing ranged goals already understand stationary firing. Reuse
        // that gate so a distant attacker cannot lure a protector into pursuit.
        return get(human) == BODYGUARD && owner != null && target != null
                && target.distanceToSqr(owner) > BodyguardPolicy.CHASE_SQUARED;
    }

    public static void onSelfAttacked(Human human, LivingEntity attacker) {
        if (!human.hasOwner() || get(human) != GUARD || HumanRelations.allied(human, attacker)) return;
        long now = human.level().getGameTime();
        human.guardCombatState.attacked(attacker.getUUID(), now,
                !insideGuardArea(human, human.getX(), human.getZ()));
        human.soldierCombatMemory.threatened(attacker.getUUID(), now);
        human.getPersistentData().remove(RETURNING_FROM_RETREAT);
    }

    private static boolean insideGuardArea(Human human, double x, double z) {
        return horizontalDistanceSqr(x, z, anchor(human)) <= FOLLOW_COMBAT_RANGE_SQR;
    }

    public static boolean allowsLootPosition(Human human, double x, double z) {
        if (BodyguardProtection.isBodyguard(human)) {
            Player owner = BodyguardProtection.owner(human);
            return owner != null && BodyguardProtection.threat(human) == null
                    && human.distanceToSqr(owner) <= 16.0D
                    && owner.distanceToSqr(x, owner.getY(), z) <= 16.0D;
        }
        return !human.hasOwner() || get(human) != GUARD
                || (!human.guardCombatState.returning() && insideGuardArea(human, human.getX(), human.getZ())
                && insideGuardArea(human, x, z));
    }

    public static boolean isReturningToHoldPosition(Human human) {
        if (BodyguardProtection.isBodyguard(human)) return false;
        return isHoldingPosition(human)
                && distanceFromAnchorSqr(human, anchor(human)) > HOLD_RETURN_DISTANCE_SQR;
    }

    /** Last accepted idle patrol endpoint; combat paths must never replace this destination. */
    static BlockPos recoveryPost(Human human) {
        return get(human) == PATROL && human.getPersistentData().contains(PATROL_DESTINATION)
                ? BlockPos.of(human.getPersistentData().getLong(PATROL_DESTINATION)) : anchor(human);
    }

    private static BlockPos anchor(Human human) {
        if (!human.getPersistentData().contains(ANCHOR)) {
            human.getPersistentData().putLong(ANCHOR, human.blockPosition().asLong());
        }
        return BlockPos.of(human.getPersistentData().getLong(ANCHOR));
    }

    private static double horizontalDistanceSqr(double x, double z, BlockPos pos) {
        double dx = x - (pos.getX() + 0.5D);
        double dz = z - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private static boolean isRecentSelfAttacker(Human human, LivingEntity target) {
        int sinceHit = human.tickCount - human.getLastHurtByMobTimestamp();
        return target == human.getLastHurtByMob() && sinceHit >= 0 && sinceHit <= 40;
    }

    private static double distanceFromAnchorSqr(Human human, BlockPos pos) {
        double dx = human.getX() - (pos.getX() + 0.5D);
        double dy = human.getY() - pos.getY();
        double dz = human.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    static boolean teleportBesideOwner(Human human, ServerPlayer owner) {
        if (human.tickCount % 10 != 0 || owner.level() != human.level()
                || human.isFleeing || human.isInLava() || human.isPassenger()) return false;
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = human.getRandom().nextInt(7) - 3;
            int dz = human.getRandom().nextInt(7) - 3;
            var pos = SafePositions.nearOwner(human, owner, owner.getBlockX() + dx, owner.getBlockZ() + dz);
            if (pos == null) continue;
            SoldierFollowNavigation.clear(human);
            human.teleportTo(pos.x, pos.y, pos.z);
            human.getNavigation().stop();
            return true;
        }
        // No blind fallback into a block or onto the surface above a cave.
        return false;
    }
}
