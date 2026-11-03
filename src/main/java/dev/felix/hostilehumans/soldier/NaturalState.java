package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Separates author data from stable instance state. Normal loading never infers a new home. */
public final class NaturalState {
    public static final String STATE = HumanRuntime.MOD_ID + ":natural_state";
    public static final String ORIGIN = HumanRuntime.MOD_ID + ":natural_origin";
    public static final String RUNTIME = HumanRuntime.MOD_ID + ":route_runtime";
    public static final String NEW_INSTANCE = HumanRuntime.MOD_ID + ":natural_new_instance";
    private static final String SUSPENDED = HumanRuntime.MOD_ID + ":controller_suspended_natural";
    private NaturalState() {}
    /** A control-mode change preserves the authored route; explicit author clear remains destructive. */
    public static void suspendForController(Human h) {
        if(!hasState(h))return;
        CompoundTag saved=new CompoundTag();saved.put("state",h.getPersistentData().getCompound(STATE).copy());
        saved.put("origin",h.getPersistentData().getCompound(ORIGIN).copy());
        saved.put("runtime",h.getPersistentData().getCompound(RUNTIME).copy());
        clear(h);h.getPersistentData().put(SUSPENDED,saved);
    }
    public static void restoreForController(Human h) {
        CompoundTag saved=h.getPersistentData().getCompound(SUSPENDED);
        if(saved.contains("state",Tag.TAG_COMPOUND)){
            h.getPersistentData().put(STATE,saved.getCompound("state").copy());
            h.getPersistentData().put(ORIGIN,saved.getCompound("origin").copy());
        }
        h.getPersistentData().remove(SUSPENDED);
        set(h,new GarrisonSettings(true,false,GarrisonSettings.natural(h).radius()));
        if(saved.contains("runtime",Tag.TAG_COMPOUND))h.getPersistentData().put(RUNTIME,saved.getCompound("runtime").copy());
    }
    /** Detached authoring view, including the route retained while this human is free. */
    public record ControllerData(boolean active,CompoundTag author,CompoundTag origin,CompoundTag runtime) {}
    public static ControllerData controllerData(Human h) {
        boolean active=hasState(h);CompoundTag saved=h.getPersistentData().getCompound(SUSPENDED);
        return new ControllerData(active,(active?h.getPersistentData().getCompound(STATE):saved.getCompound("state")).copy(),
                (active?h.getPersistentData().getCompound(ORIGIN):saved.getCompound("origin")).copy(),
                (active?h.getPersistentData().getCompound(RUNTIME):saved.getCompound("runtime")).copy());
    }
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
        NaturalRoute.settingsChanged(human,true); if (!human.hasOwner()) human.guardCombatState.reset();
    }
    public static void clear(Human human) {
        if (!human.hasOwner()) HumanBehaviorControl.interruptMovement(human, "natural behavior settings cleared");
        human.getPersistentData().remove(STATE); human.getPersistentData().remove(ORIGIN);
        human.getPersistentData().remove(SUSPENDED);
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
    /** Convert a saved garrison order into owner-independent template behavior. */
    public static ControllerData structureBehavior(CompoundTag data) {
        if (data.contains(STATE, Tag.TAG_COMPOUND))
            return new ControllerData(true, data.getCompound(STATE).copy(), data.getCompound(ORIGIN).copy(),
                    new CompoundTag());
        SoldierOrder order;
        try { order = SoldierOrder.logical(SoldierOrder.valueOf(data.getString(SoldierOrder.ORDER))); }
        catch (IllegalArgumentException invalid) { return new ControllerData(false, new CompoundTag(), new CompoundTag(), new CompoundTag()); }
        if (order != SoldierOrder.GARRISON || !data.contains(SoldierOrder.ANCHOR, Tag.TAG_LONG))
            return new ControllerData(false, new CompoundTag(), new CompoundTag(), new CompoundTag());
        var author = data.getCompound(GarrisonSettings.PLAYER).copy();
        var home = data.getCompound(PatrolRoutes.PLAYER_CENTER).copy();
        var anchor = net.minecraft.core.BlockPos.of(data.getLong(SoldierOrder.ANCHOR));
        if (!home.contains("x", Tag.TAG_ANY_NUMERIC)
                || !home.getString("dimension").equals(data.getString(SoldierOrder.ANCHOR_DIMENSION))
                || !net.minecraft.core.BlockPos.containing(home.getDouble("x"), home.getDouble("y"), home.getDouble("z")).equals(anchor)) {
            home.putDouble("x", anchor.getX() + .5);
            home.putDouble("y", anchor.getY());
            home.putDouble("z", anchor.getZ() + .5);
        }
        home.putString("dimension", data.getString(SoldierOrder.ANCHOR_DIMENSION));
        home.putDouble("right_x", 1);
        home.putDouble("right_z", 0);
        home.putDouble("forward_x", 0);
        home.putDouble("forward_z", 1);
        home.putInt("placement_rotation", 0);
        home.putString("placement_mirror", "none");
        return new ControllerData(true, author, home, new CompoundTag());
    }

    /** Future exporter contract: pass ForgeData COPY, never ordinary save data. */
    public static CompoundTag authorOnly(CompoundTag instanceData) {
        CompoundTag result = new CompoundTag();
        if (instanceData.contains(STATE, Tag.TAG_COMPOUND)) {
            CompoundTag author=instanceData.getCompound(STATE).copy();
            if(author.getBoolean(PatrolRoutes.AUTHORED))
                for(String key:java.util.List.of("route","route_origin","player_route","route_revision","route_mode","route_rotation","route_mirror"))author.remove(key);
            result.put(STATE,author);
        }
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
