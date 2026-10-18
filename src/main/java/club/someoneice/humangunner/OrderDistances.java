package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Single geometry authority for orders. Consumers must choose daily or combat explicitly. */
public final class OrderDistances {
    public static final String RADII = HumanGunner.MOD_ID + ":order_radii";
    public record Defaults(int followDistance, int garrisonRadius, double returnDistance,
            double leaveThreshold, double walkSpeed, int chaseExtraRadius, int chaseMaxRadius,
            int chaseDurationTicks, int chaseReturnCooldownTicks, double routeVerticalTolerance,
            int routeFailureBackoffTicks, double routeReachDistance, int routeTimeout,
            int routeMaxPoints, int routeWaitDefault) {
        public Defaults {
            followDistance = radius(followDistance); garrisonRadius = radius(garrisonRadius);
            returnDistance = finite(returnDistance, .8, .3, 3); leaveThreshold = finite(leaveThreshold, 4, 2, 16);
            walkSpeed = finite(walkSpeed, .65, .3, 1); chaseExtraRadius = clamp(chaseExtraRadius, 0, 32);
            chaseMaxRadius = radius(chaseMaxRadius); chaseDurationTicks = clamp(chaseDurationTicks, 40, 1200);
            chaseReturnCooldownTicks = clamp(chaseReturnCooldownTicks, 0, 400);
            routeVerticalTolerance = finite(routeVerticalTolerance, .75, .1, 1);
            routeFailureBackoffTicks = clamp(routeFailureBackoffTicks, 20, 1200);
            routeReachDistance = finite(routeReachDistance, 1.5, .5, 4);
            routeTimeout = clamp(routeTimeout, 20, 1200); routeMaxPoints = clamp(routeMaxPoints, 2, 256);
            routeWaitDefault = clamp(routeWaitDefault, 0, 1200);
        }
    }
    private static volatile Defaults defaults = new Defaults(48, 32, .8, 4, .65, 16, 96,
            400, 80, .75, 100, 1.5, 200, 64, 0);
    public static Defaults defaults() { return defaults; }
    public static void configure(Defaults value) { defaults = java.util.Objects.requireNonNull(value); }
    public record Context(boolean constrained, boolean natural, boolean available, Vec3 origin,
                          GarrisonSettings settings, String reason) {
        public double dailyRadius() { return settings.radius(); }
        public double combatRadius() {
            return settings.chase() ? Math.min(Math.max(dailyRadius(), defaults.chaseMaxRadius()),
                    dailyRadius() + defaults.chaseExtraRadius()) : dailyRadius();
        }
        public double horizontalDistanceSquared(Vec3 point) {
            double x = point.x - origin.x, z = point.z - origin.z; return x*x + z*z;
        }
        public boolean insideDaily(Vec3 point) { return available && horizontalDistanceSquared(point) <= dailyRadius()*dailyRadius(); }
        public boolean insideCombat(Vec3 point) { return available && horizontalDistanceSquared(point) <= combatRadius()*combatRadius(); }
    }
    private OrderDistances() {}
    public static int follow(Human human) {
        CompoundTag radii = human.getPersistentData().getCompound(RADII);
        return radii.contains("follow", Tag.TAG_ANY_NUMERIC) ? radius(radii.getInt("follow")) : defaults.followDistance();
    }
    public static double followSquared(Human human) { double r = follow(human); return r*r; }
    public static int signature(Human human) {
        Context c=context(human);
        return java.util.Objects.hash(SoldierOrder.get(human),follow(human),c.constrained(),c.natural(),c.available(),
                c.constrained()?c.origin():null,c.settings(),c.combatRadius());
    }
    public static Context context(Human human) {
        boolean natural = !human.hasOwner();
        GarrisonSettings settings = natural ? GarrisonSettings.natural(human) : GarrisonSettings.player(human);
        boolean constrained = natural ? NaturalState.hasState(human)
                && !human.getPersistentData().hasUUID(HumanRelations.TEMP_OWNER) : SoldierOrder.get(human) == SoldierOrder.GARRISON;
        if (!constrained) return new Context(false, natural, true, human.position(), settings, "free");
        if (natural) return NaturalState.context(human, settings);
        boolean exists = human.getPersistentData().contains(SoldierOrder.ANCHOR, Tag.TAG_LONG);
        Vec3 anchor = exists ? Vec3.atBottomCenterOf(net.minecraft.core.BlockPos.of(
                human.getPersistentData().getLong(SoldierOrder.ANCHOR))) : human.position();
        return new Context(true, false, exists, anchor, settings, exists ? "ready" : "missing_player_anchor");
    }
    public static void setFollow(Human human, int radius) {
        CompoundTag tag = human.getPersistentData().getCompound(RADII).copy();
        tag.putInt("follow", radius(radius)); human.getPersistentData().put(RADII, tag);
    }
    static int radius(int radius) { return clamp(radius, 8, 96); }
    static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    static double finite(double value, double fallback, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
