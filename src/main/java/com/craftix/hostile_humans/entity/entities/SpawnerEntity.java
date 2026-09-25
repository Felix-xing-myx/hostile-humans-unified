package com.craftix.hostile_humans.entity.entities;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.compat.DungeonMobs;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import java.util.ArrayList;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.ModList;

public class SpawnerEntity
extends Mob {
    protected SpawnerEntity(EntityType<? extends Mob> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
    }

    public void tick() {
        if (!this.level().isClientSide) {
            this.spawn((ServerLevel)this.level());
            this.discard();
        }
    }

    private boolean spawn(ServerLevel level) {
        if (!club.someoneice.humangunner.NaturalHumanSpawnRules.beginBattle(level, blockPosition())) return false;
        try { return spawnBattle(level); }
        finally { club.someoneice.humangunner.NaturalHumanSpawnRules.endBattle(level); }
    }
    private static void addMember(ArrayList<LivingEntity> members, LivingEntity member) {
        if (member == null) return;
        members.add(member);
        club.someoneice.humangunner.NaturalHumanSpawnRules.battleMemberAdded((ServerLevel)member.level());
    }
    private boolean spawnBattle(ServerLevel level) {
        BlockPos blockpos = this.blockPosition();
        if (this.hasEnoughSpace((BlockGetter)level, blockpos) && Config.eventType.get() != SpawnType.Disabled) {
            if (level.getBiome(blockpos).is(Biomes.THE_VOID)) {
                return false;
            }
            boolean currentTeam = false;
            boolean isPillagers = Config.eventType.get() == SpawnType.HumanVsPillager || this.random.nextFloat() < 0.6f && Config.eventType.get() != SpawnType.HumanVsHuman;
            int totalAmount = this.random.nextInt(3, 18);
            boolean bannerLeft = false;
            boolean bannerRight = false;
            ArrayList<LivingEntity> spawnedEntities = new ArrayList<>();
            for (int j = 0; j < totalAmount; ++j) {
                BlockPos pos = this.findSpawnPositionNear((LevelReader)level, blockpos, 4);
                if (pos == null) continue;
                if (isPillagers && currentTeam) {
                    if (this.random.nextBoolean()) {
                        addMember(spawnedEntities, (LivingEntity)this.getRandomPillager().spawn(level, (CompoundTag)null, null, pos, MobSpawnType.EVENT, false, false));
                    }
                    addMember(spawnedEntities, (LivingEntity)this.getRandomPillager().spawn(level, (CompoundTag)null, null, pos, MobSpawnType.EVENT, false, false));
                } else {
                    Human newHuman = (Human)((double)this.random.nextFloat() < 0.05 ? (EntityType)ModEntityType.HUMAN2.get() : (EntityType)ModEntityType.HUMAN1.get()).spawn(level, (CompoundTag)null, null, pos, MobSpawnType.EVENT, false, false);
                    addMember(spawnedEntities, newHuman);
                    if (newHuman != null) {
                        if (totalAmount > 5) {
                            if (currentTeam && !bannerLeft) {
                                bannerLeft = true;
                                newHuman.setBanner(HumanUtil.createSwordBanner());
                            }
                            if (!currentTeam && !bannerRight) {
                                bannerRight = true;
                                newHuman.setBanner(HumanUtil.createSwordBanner());
                            }
                        }
                        newHuman.team = currentTeam ? "team1" : "team2";
                    }
                }
                currentTeam = !currentTeam;
            }
            for (LivingEntity entity : spawnedEntities) {
                entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                        20 * (entity instanceof Human ? 15 : 10), 255, false, false, false));
            }
            return true;
        }
        return false;
    }

    EntityType getRandomPillager() {
        if (this.random.nextFloat() < 0.05f) {
            return EntityType.RAVAGER;
        }
        if (this.random.nextFloat() < 0.05f && ModList.get().isLoaded("dungeon_mobs")) {
            return DungeonMobs.getRedstoneGolem() == null ? EntityType.RAVAGER : DungeonMobs.getRedstoneGolem();
        }
        EntityType[] list = new EntityType[]{EntityType.PILLAGER, EntityType.PILLAGER, EntityType.EVOKER, EntityType.PILLAGER, EntityType.ILLUSIONER, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.VINDICATOR};
        return list[(int)((float)list.length * this.random.nextFloat())];
    }

    @Nullable
    private BlockPos findSpawnPositionNear(LevelReader levelReader, BlockPos blockPos, int radius) {
        BlockPos outPos = null;
        for (int i = 0; i < 10; ++i) {
            int k;
            int l;
            int j = blockPos.getX() + this.random.nextInt(radius * 2) - radius;
            BlockPos blockpos1 = new BlockPos(j, l = levelReader.getHeight(Heightmap.Types.WORLD_SURFACE, j, k = blockPos.getZ() + this.random.nextInt(radius * 2) - radius), k);
            if (!NaturalSpawner.isSpawnPositionOk((SpawnPlacements.Type)SpawnPlacements.Type.ON_GROUND, (LevelReader)levelReader, (BlockPos)blockpos1, (EntityType)EntityType.WANDERING_TRADER)) continue;
            outPos = blockpos1;
            break;
        }
        return outPos != null && levelReader instanceof net.minecraft.world.level.ServerLevelAccessor server
                && club.someoneice.humangunner.NaturalHumanSpawnRules.isSafePosition(server, outPos) ? outPos : null;
    }

    private boolean hasEnoughSpace(BlockGetter p_35926_, BlockPos p_35927_) {
        for (BlockPos blockpos : BlockPos.betweenClosed((BlockPos)p_35927_, (BlockPos)p_35927_.offset(1, 2, 1))) {
            if (p_35926_.getBlockState(blockpos).getCollisionShape(p_35926_, blockpos).isEmpty()) continue;
            return false;
        }
        return true;
    }

    public static enum SpawnType {
        HumanVsHuman,
        HumanVsPillager,
        Random,
        Disabled;

    }
}

