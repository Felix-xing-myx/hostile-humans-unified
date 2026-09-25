package com.craftix.hostile_humans.entity.spawner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import com.craftix.hostile_humans.entity.entities.SpawnerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public class SpawnHandler {
    protected SpawnHandler() {
    }

    public static void registerSpawnPlacements(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SpawnPlacements.register((EntityType)((EntityType)ModEntityType.ROAMER.get()), (SpawnPlacements.Type)SpawnPlacements.Type.ON_GROUND, (Heightmap.Types)Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SpawnHandler::checkHumanSpawnRules);
            SpawnPlacements.register((EntityType)((EntityType)ModEntityType.SPAWNER_ENTITY.get()), (SpawnPlacements.Type)SpawnPlacements.Type.ON_GROUND, (Heightmap.Types)Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SpawnHandler::checkSpawnerEntityRules);
        });
    }

    public static boolean checkSpawnerEntityRules(EntityType<SpawnerEntity> p_33018_, ServerLevelAccessor p_33019_, MobSpawnType p_33020_, BlockPos p_33021_, RandomSource random) {
        if (!club.someoneice.humangunner.NaturalHumanSpawnRules.checkBattleAdmission(
                p_33019_, p_33020_, p_33021_, random)) return false;

        if (random.nextInt(200) != 0) {
            return false;
        }
        return SpawnHandler.isBrightEnoughToSpawn(p_33019_, p_33021_, random) && Mob.checkMobSpawnRules(p_33018_, (LevelAccessor)p_33019_, (MobSpawnType)p_33020_, (BlockPos)p_33021_, (RandomSource)random);
    }

    public static boolean checkHumanSpawnRules(EntityType<? extends Human> p_33018_, ServerLevelAccessor p_33019_, MobSpawnType p_33020_, BlockPos p_33021_, RandomSource random) {
        return club.someoneice.humangunner.NaturalHumanSpawnRules.checkLegacySpawn(
                p_33018_, p_33019_, p_33020_, p_33021_, random);
    }

    public static boolean isBrightEnoughToSpawn(ServerLevelAccessor p_33009_, BlockPos p_33010_, RandomSource p_33011_) {
        return p_33009_.getBrightness(LightLayer.SKY, p_33010_) > 10;
    }
}

