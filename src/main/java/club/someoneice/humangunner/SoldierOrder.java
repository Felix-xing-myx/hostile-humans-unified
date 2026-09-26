package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Persistent owner-issued movement policy for hired humans. */
public enum SoldierOrder {
    HOLD_POSITION,
    GUARD,
    FOLLOW,
    PATROL;

    private static final String ORDER = "humangunner:soldier_order";
    private static final String ANCHOR = "humangunner:order_anchor";
    private static final String NEXT_PATROL = "humangunner:next_patrol_move";
    private static final String RETURNING_FROM_RETREAT = "humangunner:returning_from_retreat";
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
        human.getPersistentData().putString(ORDER, order.name());
        human.getPersistentData().putLong(ANCHOR, human.blockPosition().asLong());
        human.getPersistentData().remove(NEXT_PATROL);
        human.getPersistentData().remove(RETURNING_FROM_RETREAT);
        human.getNavigation().stop();
        if (order != FOLLOW) human.setTarget(null);
    }

    static void initialize(Human human) {
        if (!human.getPersistentData().contains(ORDER)) {
            set(human, FOLLOW);
        }
    }

    static void clear(Human human) {
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

    static void tick(Human human) {
        if (!human.hasOwner() || !(human.level() instanceof ServerLevel level)) return;
        // The shore goal already owns idle water movement. This lifecycle
        // callback runs outside GoalSelector arbitration, so it must not
        // replace the shore route with a follow/guard/patrol path.
        if (human.isSeekingShore()) return;
        // Survival navigation owns movement until the retreat has actually
        // ended; neither an anchor leash nor owner-follow may pull it back.
        if (human.isFleeing) return;
        Player rawOwner = level.getPlayerByUUID(human.getOwnerUUID());
        ServerPlayer owner = rawOwner instanceof ServerPlayer player && player.isAlive() ? player : null;

        SoldierOrder order = get(human);
        if (order == PATROL && human.getTarget() == null) {
            // A previous chase can leave the sprint flag set. Patrol paths
            // use the ordinary walking attribute, including potion effects.
            MovementSpeedController.normal(human);
        }
        BlockPos anchor = anchor(human);
        if (human.getPersistentData().getBoolean(RETURNING_FROM_RETREAT)) {
            int sinceHit = human.tickCount - human.getLastHurtByMobTimestamp();
            if (human.getLastHurtByMob() != null && sinceHit >= 0 && sinceHit <= 10) {
                human.getPersistentData().remove(RETURNING_FROM_RETREAT);
            } else {
                if (human.getTarget() != null) human.forgetSoldierTarget();
                if (order == FOLLOW) {
                    if (owner != null && human.distanceToSqr(owner) > 16.0D) {
                        if (human.tickCount % 10 == 0 || human.getNavigation().isDone()) {
                            human.getNavigation().moveTo(owner, 1.15D);
                        }
                    } else if (owner != null) {
                        human.getPersistentData().remove(RETURNING_FROM_RETREAT);
                        human.getNavigation().stop();
                    }
                } else if (distanceFromAnchorSqr(human, anchor) > 4.0D) {
                    if (human.tickCount % 10 == 0 || human.getNavigation().isDone()) {
                        human.getNavigation().moveTo(
                                anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D,
                                order == PATROL ? PATROL_WALK_NAVIGATION_SPEED : 1.1D);
                    }
                } else {
                    human.getPersistentData().remove(RETURNING_FROM_RETREAT);
                    human.getNavigation().stop();
                }
                return;
            }
        }
        LivingEntity target = human.getTarget();
        if (target != null && (!target.isAlive() || HumanRelations.allied(human, target)
                || !allowsTarget(human, target)
                || human.soldierCombatMemory.timedOut(target.getUUID(), level.getGameTime()))) {
            human.forgetSoldierTarget();
            target = null;
        }

        if (order == FOLLOW) {
            if (owner == null) { human.getNavigation().stop(); return; }
            if (human.distanceToSqr(owner) > FOLLOW_COMBAT_RANGE_SQR) {
                human.forgetSoldierTarget();
                target = null;
                teleportBesideOwner(human, owner);
            }
            if (target != null && target.distanceToSqr(owner) > FOLLOW_COMBAT_RANGE_SQR) {
                human.forgetSoldierTarget();
                target = null;
            }
            if (target == null && human.tickCount % 10 == 0) {
                if (human.distanceToSqr(owner) > 16.0D) human.getNavigation().moveTo(owner, 1.15D);
                else human.getNavigation().stop();
            }
            return;
        }

        double distanceFromAnchor = horizontalDistanceSqr(human.getX(), human.getZ(), anchor);
        if (order == HOLD_POSITION) {
            if (distanceFromAnchorSqr(human, anchor) > HOLD_RETURN_DISTANCE_SQR) {
                if (human.tickCount % 5 == 0 || human.getNavigation().isDone()) {
                    human.getNavigation().moveTo(
                            anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.1D
                    );
                }
            } else {
                human.getNavigation().stop();
            }
            return;
        }
        double leash = order == GUARD ? 48.0D * 48.0D : PATROL_RANGE_SQR;
        if (target != null && !isRecentSelfAttacker(human, target)
                && horizontalDistanceSqr(target.getX(), target.getZ(), anchor) > leash) {
            human.forgetSoldierTarget();
            target = null;
        }
        if (target != null) return;

        if (order == GUARD) {
            if (distanceFromAnchor > 4.0D && human.tickCount % 10 == 0) {
                human.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.1D);
            } else if (distanceFromAnchor <= 4.0D) {
                human.getNavigation().stop();
            }
            return;
        }

        long now = level.getGameTime();
        if (distanceFromAnchor > PATROL_RANGE_SQR) {
            if (human.tickCount % 10 != 0) return;
            human.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D,
                    PATROL_WALK_NAVIGATION_SPEED);
            human.getPersistentData().putLong(NEXT_PATROL, now + 80L);
        } else if (human.getNavigation().isDone() && now >= human.getPersistentData().getLong(NEXT_PATROL)) {
            for (int attempt = 0; attempt < 8; attempt++) {
                int dx = human.getRandom().nextInt(65) - 32;
                int dz = human.getRandom().nextInt(65) - 32;
                if (dx * dx + dz * dz > 32 * 32) continue;
                BlockPos destination = SafePositions.nearFloor(level, anchor.getX() + dx, anchor.getZ() + dz, anchor.getY());
                if (destination == null) continue;
                if (human.getNavigation().moveTo(destination.getX() + 0.5D, destination.getY(),
                        destination.getZ() + 0.5D, PATROL_WALK_NAVIGATION_SPEED)) break;
            }
            human.getPersistentData().putLong(NEXT_PATROL, now + 80L + human.getRandom().nextInt(81));
        }
    }

    public static boolean allowsTarget(Human human, LivingEntity target) {
        // GoalSelector can clear a dead target between canUse and
        // canContinueToUse. A missing target is an ordinary stop condition,
        // never an exceptional soldier-order state.
        if (target == null) return false;
        if (!human.hasOwner() || human.level().isClientSide) return true;
        if (!target.isAlive() || target.level() != human.level()
                || human.distanceToSqr(target) > FOLLOW_COMBAT_RANGE_SQR
                || !human.soldierCombatMemory.allowed(target.getUUID(), human.level().getGameTime())
                || !SoldierCombatMode.allowsTarget(human, target)) return false;
        if (human.getPersistentData().getBoolean(RETURNING_FROM_RETREAT)) return false;
        if (human.isFleeing || isRecentSelfAttacker(human, target)) return true;
        SoldierOrder order = get(human);
        if (order == FOLLOW) {
            Player owner = human.level().getPlayerByUUID(human.getOwnerUUID());
            return owner != null && owner.isAlive()
                    && human.distanceToSqr(owner) <= FOLLOW_COMBAT_RANGE_SQR
                    && target.distanceToSqr(owner) <= FOLLOW_COMBAT_RANGE_SQR;
        }
        return horizontalDistanceSqr(target.getX(), target.getZ(), anchor(human))
                <= (order == GUARD || order == HOLD_POSITION
                ? FOLLOW_COMBAT_RANGE_SQR : PATROL_RANGE_SQR);
    }

    public static boolean isHoldingPosition(Human human) {
        return human.hasOwner() && get(human) == HOLD_POSITION;
    }

    public static boolean isReturningToHoldPosition(Human human) {
        return isHoldingPosition(human)
                && distanceFromAnchorSqr(human, anchor(human)) > HOLD_RETURN_DISTANCE_SQR;
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

    private static void teleportBesideOwner(Human human, ServerPlayer owner) {
        if (human.tickCount % 10 != 0) return;
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = human.getRandom().nextInt(7) - 3;
            int dz = human.getRandom().nextInt(7) - 3;
            BlockPos pos = SafePositions.nearFloor(owner.serverLevel(), owner.getBlockX() + dx,
                    owner.getBlockZ() + dz, owner.getBlockY());
            if (pos == null) continue;
            human.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            human.getNavigation().stop();
            return;
        }
        // No blind fallback into a block or onto the surface above a cave.
    }
}
