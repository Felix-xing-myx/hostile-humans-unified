package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.bootstrap.ModEntityType;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.equipment.HumanSpawnEquipment;
import dev.felix.hostilehumans.faction.FactionContent;
import dev.felix.hostilehumans.faction.HumanFactions;
import dev.felix.hostilehumans.progression.HumanGrowth;
import dev.felix.hostilehumans.soldier.NaturalRoute;
import dev.felix.hostilehumans.soldier.NaturalState;
import dev.felix.hostilehumans.soldier.PatrolRoutes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Structure-only entity recipes. Vanilla entity and ordinary world save formats remain intact. */
public final class SettlementHumanTemplates {
    private static final String LOCAL = "hostile_humans_unified:template_human_local";
    private static final String BIRTH = "hostile_humans_unified:template_human_birth";

    private SettlementHumanTemplates() {}

    private static boolean human(String id) {
        return EntityType.byString(id).filter(type -> type == ModEntityType.HUMAN1.get()
                || type == ModEntityType.HUMAN2.get() || type == ModEntityType.HUMAN3.get()
                || type == ModEntityType.ROAMER.get()
                || FactionContent.BOSSES.stream().anyMatch(boss -> boss.type().get() == type)).isPresent();
    }

    static void prepareFile(CompoundTag template, Vec3 captureOrigin, ResourceLocation dimension) {
        var entries = template.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var saved = entry.getCompound("nbt");
            var original = saved.getCompound("ForgeData");
            if (original.getBoolean(LOCAL) || !human(saved.getString("id"))) continue;
            Vec3 origin = captureOrigin;
            if (origin == null) {
                // Old author exports kept vanilla's absolute entity Pos and its local entry pos.
                var world = saved.getList("Pos", Tag.TAG_DOUBLE);
                var local = entry.getList("pos", Tag.TAG_DOUBLE);
                if (world.size() != 3 || local.size() != 3) continue;
                origin = new Vec3(world.getDouble(0) - local.getDouble(0),
                        world.getDouble(1) - local.getDouble(1), world.getDouble(2) - local.getDouble(2));
            }
            var recipe = new CompoundTag();
            for (String key : List.of("id", "Rotation", "CustomName", "CustomNameVisible"))
                if (saved.contains(key)) recipe.put(key, saved.get(key).copy());
            var data = new CompoundTag();
            if (original.contains(HumanFactions.FACTION, Tag.TAG_STRING))
                data.putString(HumanFactions.FACTION, original.getString(HumanFactions.FACTION));
            data.putBoolean(LOCAL, true);
            var behavior = NaturalState.structureBehavior(original);
            if (behavior.active()) {
                var state = behavior.author();
                var home = behavior.origin();
                if (localize(home, origin, dimension)) {
                    data.put(NaturalState.ORIGIN, home);
                    if (state.getBoolean(PatrolRoutes.AUTHORED)
                            && !localize(state.getCompound(PatrolRoutes.ORIGIN), origin, dimension)) {
                        state.remove("route");
                        state.remove(PatrolRoutes.ORIGIN);
                        state.remove(PatrolRoutes.AUTHORED);
                    }
                    data.put(NaturalState.STATE, state);
                }
            }
            recipe.put("ForgeData", data);
            recipe.putBoolean("PersistenceRequired", true);
            entry.put("nbt", recipe);
        }
    }

    private static boolean localize(CompoundTag anchor, Vec3 origin, ResourceLocation dimension) {
        if (!anchor.contains("x", Tag.TAG_ANY_NUMERIC) || !anchor.contains("y", Tag.TAG_ANY_NUMERIC)
                || !anchor.contains("z", Tag.TAG_ANY_NUMERIC)
                || dimension != null && !anchor.getString("dimension").equals(dimension.toString())) return false;
        Vec3 local = position(anchor).subtract(origin);
        if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) return false;
        putPosition(anchor, local);
        anchor.remove("dimension");
        return true;
    }

    public static List<StructureTemplate.StructureEntityInfo> place(
            List<StructureTemplate.StructureEntityInfo> entries, BlockPos origin,
            StructurePlaceSettings settings, ResourceLocation dimension) {
        List<StructureTemplate.StructureEntityInfo> result = null;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.nbt == null || !entry.nbt.getCompound("ForgeData").getBoolean(LOCAL)) continue;
            var nbt = entry.nbt.copy();
            var data = nbt.getCompound("ForgeData");
            data.remove(LOCAL);
            data.putBoolean(BIRTH, true);
            if (data.contains(NaturalState.ORIGIN, Tag.TAG_COMPOUND))
                placeAnchor(data.getCompound(NaturalState.ORIGIN), origin, settings, dimension);
            var state = data.getCompound(NaturalState.STATE);
            if (state.contains(PatrolRoutes.ORIGIN, Tag.TAG_COMPOUND))
                placeAnchor(state.getCompound(PatrolRoutes.ORIGIN), origin, settings, dimension);
            if (result == null) result = new ArrayList<>(entries);
            result.set(i, new StructureTemplate.StructureEntityInfo(entry.pos, entry.blockPos, nbt));
        }
        return result == null ? entries : result;
    }

    private static void placeAnchor(CompoundTag anchor, BlockPos origin, StructurePlaceSettings settings,
            ResourceLocation dimension) {
        var zero = StructureTemplate.transformedVec3d(settings, Vec3.ZERO);
        var world = StructureTemplate.transformedVec3d(settings, position(anchor)).add(Vec3.atLowerCornerOf(origin));
        for (String axis : List.of("right", "forward")) {
            var basis = NaturalRoute.transform(anchor.getDouble(axis + "_x"), 0, anchor.getDouble(axis + "_z"),
                    anchor.getString("placement_mirror"), anchor.getInt("placement_rotation"));
            var rotated = StructureTemplate.transformedVec3d(settings, basis).subtract(zero);
            anchor.putDouble(axis + "_x", rotated.x);
            anchor.putDouble(axis + "_z", rotated.z);
        }
        putPosition(anchor, world);
        anchor.putString("dimension", dimension.toString());
        anchor.putInt("placement_rotation", 0);
        anchor.putString("placement_mirror", "none");
    }

    private static Vec3 position(CompoundTag tag) {
        return new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
    }

    private static void putPosition(CompoundTag tag, Vec3 value) {
        tag.putDouble("x", value.x);
        tag.putDouble("y", value.y);
        tag.putDouble("z", value.z);
    }

    public static void onJoin(Human human, ServerLevel level, boolean loadedFromDisk) {
        var data = human.getPersistentData();
        if (!data.getBoolean(BIRTH)) return;
        data.remove(BIRTH);
        if (loadedFromDisk) return;
        HumanGrowth.markBirth(human, level, MobSpawnType.STRUCTURE);
        HumanSpawnEquipment.generate(human, false);
    }
}
