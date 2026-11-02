package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Author/player settings. Reading legacy data never rewrites a save. */
public record GarrisonSettings(boolean stay, boolean chase, int radius) {
    public static final String PLAYER = HumanRuntime.MOD_ID + ":order_garrison";
    public GarrisonSettings { radius = OrderDistances.radius(radius); }
    public static GarrisonSettings player(Human human) {
        CompoundTag data = human.getPersistentData();
        SoldierOrder old = SoldierOrder.raw(human);
        CompoundTag radii = data.getCompound(OrderDistances.RADII);
        int radius = old == SoldierOrder.GUARD || old == SoldierOrder.HOLD_POSITION ? 48 : OrderDistances.defaults().garrisonRadius();
        String legacy = old == SoldierOrder.GUARD ? "guard" : old == SoldierOrder.PATROL ? "patrol" : "garrison";
        if (radii.contains(legacy, Tag.TAG_ANY_NUMERIC)) radius = radii.getInt(legacy);
        return read(data.getCompound(PLAYER), new GarrisonSettings(old == SoldierOrder.HOLD_POSITION,
                old != SoldierOrder.HOLD_POSITION, radius));
    }
    public static GarrisonSettings natural(Human human) {
        return read(human.getPersistentData().getCompound(NaturalState.STATE),
                new GarrisonSettings(false, true, OrderDistances.defaults().garrisonRadius()));
    }
    public static GarrisonSettings read(CompoundTag tag, GarrisonSettings fallback) {
        return new GarrisonSettings(tag.contains("stay") ? tag.getBoolean("stay") : fallback.stay(),
                tag.contains("chase_on_hate") ? tag.getBoolean("chase_on_hate") : fallback.chase(),
                tag.contains("patrol_radius", Tag.TAG_ANY_NUMERIC) ? tag.getInt("patrol_radius") : fallback.radius());
    }
    public CompoundTag writeInto(CompoundTag tag) {
        tag.putInt("schema", 1); tag.putBoolean("stay", stay); tag.putBoolean("chase_on_hate", chase);
        tag.putInt("patrol_radius", radius); return tag;
    }
    public static void set(Human human, GarrisonSettings settings) { set(human, settings, false); }
    static void set(Human human, GarrisonSettings settings, boolean preserveMovement) {
        GarrisonSettings previous = player(human);
        if (!preserveMovement && !settings.equals(previous)) HumanBehaviorControl.interruptMovement(human, "garrison settings changed");
        human.getPersistentData().put(PLAYER, settings.writeInto(human.getPersistentData().getCompound(PLAYER).copy()));
        CompoundTag radii = human.getPersistentData().getCompound(OrderDistances.RADII).copy();
        radii.remove("guard"); radii.remove("patrol"); radii.remove("garrison");
        if (radii.isEmpty()) human.getPersistentData().remove(OrderDistances.RADII);
        else human.getPersistentData().put(OrderDistances.RADII, radii);
        if (!settings.equals(previous)) {
            NaturalRoute.settingsChanged(human,false); SoldierOrder.stopMovement(human); human.guardCombatState.reset();
            if (SoldierOrder.isGarrison(human) && !OrderDistances.context(human).insideDaily(human.position()))
                human.guardCombatState.beginReturn();
        }
    }
}
