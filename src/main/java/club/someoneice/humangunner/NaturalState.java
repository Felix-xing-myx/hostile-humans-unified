package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Separates author data from stable instance state. Normal loading never infers a new home. */
public final class NaturalState {
    public static final String STATE = HumanGunner.MOD_ID + ":natural_state";
    public static final String ORIGIN = HumanGunner.MOD_ID + ":natural_origin";
    public static final String RUNTIME = HumanGunner.MOD_ID + ":route_runtime";
    public static final String NEW_INSTANCE = HumanGunner.MOD_ID + ":natural_new_instance";
    private NaturalState() {}
    public static boolean hasState(Human human) { return human.getPersistentData().contains(STATE, Tag.TAG_COMPOUND); }
    /** Explicit placement context only, after vanilla transformed the entity position. Yaw is AUTHOR yaw. */
    public static boolean initializeNewInstance(Human human, float bodyYaw, int placementRotation, String placementMirror) {
        if (!hasState(human) || human.getPersistentData().contains(ORIGIN, Tag.TAG_COMPOUND)) return false;
        if (!validRotation(placementRotation) || !validMirror(placementMirror) || !Float.isFinite(bodyYaw)) return false;
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1); tag.putString("dimension", human.level().dimension().location().toString());
        tag.putDouble("x", human.getX()); tag.putDouble("y", human.getY()); tag.putDouble("z", human.getZ());
        double angle = Math.toRadians(bodyYaw);
        tag.putDouble("right_x", -Math.cos(angle)); tag.putDouble("right_z", -Math.sin(angle));
        tag.putDouble("forward_x", -Math.sin(angle)); tag.putDouble("forward_z", Math.cos(angle));
        tag.putFloat("body_yaw", bodyYaw); tag.putInt("placement_rotation", placementRotation);
        tag.putString("placement_mirror", placementMirror);
        human.getPersistentData().put(ORIGIN, tag); NaturalRoute.clearNaturalRuntime(human); return true;
    }
    /** Author tool is explicitly local and unmirrored. Existing instance origin is retained. */
    public static void set(Human human, GarrisonSettings settings) {
        if (!human.hasOwner()) HumanBehaviorControl.interruptMovement(human, "natural behavior settings changed");
        human.getPersistentData().put(STATE, settings.writeInto(human.getPersistentData().getCompound(STATE).copy()));
        if (!human.getPersistentData().contains(ORIGIN)) initializeNewInstance(human, human.yBodyRot, 0, "none");
        human.getPersistentData().remove(NEW_INSTANCE);
        NaturalRoute.clearNaturalRuntime(human); if (!human.hasOwner()) human.guardCombatState.reset();
    }
    public static void clear(Human human) {
        if (!human.hasOwner()) HumanBehaviorControl.interruptMovement(human, "natural behavior settings cleared");
        human.getPersistentData().remove(STATE); human.getPersistentData().remove(ORIGIN);
        human.getPersistentData().remove(NEW_INSTANCE);
        NaturalRoute.clearNaturalRuntime(human);
        if (!human.hasOwner()) { human.guardCombatState.reset(); SoldierOrder.stopMovement(human); }
    }
    public static OrderDistances.Context context(Human human, GarrisonSettings settings) {
        CompoundTag tag = human.getPersistentData().getCompound(ORIGIN);
        boolean valid = tag.contains("x", Tag.TAG_ANY_NUMERIC) && tag.contains("y", Tag.TAG_ANY_NUMERIC)
                && tag.contains("z", Tag.TAG_ANY_NUMERIC);
        Vec3 origin = valid ? new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")) : human.position();
        valid &= Double.isFinite(origin.x) && Double.isFinite(origin.y) && Double.isFinite(origin.z);
        String reason = !valid ? "missing_origin" : !tag.getString("dimension").equals(human.level().dimension().location().toString())
                ? "origin_other_dimension" : !human.level().hasChunkAt(net.minecraft.core.BlockPos.containing(origin))
                ? "origin_unloaded" : "ready";
        return new OrderDistances.Context(true, true, reason.equals("ready"), origin, settings, reason);
    }
    static boolean validRotation(int rotation) { return rotation == 0 || rotation == 90 || rotation == 180 || rotation == 270; }
    static boolean validMirror(String mirror) { return "none".equals(mirror) || "left_right".equals(mirror) || "front_back".equals(mirror); }
    /** Future exporter contract: pass ForgeData COPY, never ordinary save data. */
    public static CompoundTag authorOnly(CompoundTag instanceData) {
        CompoundTag result = new CompoundTag();
        if (instanceData.contains(STATE, Tag.TAG_COMPOUND)) result.put(STATE, instanceData.getCompound(STATE).copy());
        return result; // allow-list excludes player orders, ownership, growth and route runtime
    }
    public static String onJoin(Human human, boolean loadedFromDisk) {
        if (!hasState(human)) return "free";
        CompoundTag marker = human.getPersistentData().getCompound(NEW_INSTANCE);
        if (!loadedFromDisk && human.getPersistentData().contains(NEW_INSTANCE, Tag.TAG_COMPOUND)) {
            boolean ok = marker.contains("author_yaw", Tag.TAG_ANY_NUMERIC) && marker.contains("rotation", Tag.TAG_ANY_NUMERIC)
                    && marker.contains("mirror", Tag.TAG_STRING)
                    && initializeNewInstance(human, marker.getFloat("author_yaw"), marker.getInt("rotation"), marker.getString("mirror"));
            if (ok) human.getPersistentData().remove(NEW_INSTANCE);
        }
        if (!loadedFromDisk && !human.getPersistentData().contains(ORIGIN) && !human.getPersistentData().contains(NEW_INSTANCE)
                && !human.getPersistentData().getCompound(STATE).contains("route"))
            initializeNewInstance(human, human.yBodyRot, 0, "none");
        return OrderDistances.context(human).reason();
    }
}
