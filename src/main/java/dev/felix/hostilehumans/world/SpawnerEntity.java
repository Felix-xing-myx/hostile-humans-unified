package dev.felix.hostilehumans.world;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.equipment.NaturalHumanSpawnRules;

import dev.felix.hostilehumans.config.Config;
import dev.felix.hostilehumans.entity.HumanUtil;
import dev.felix.hostilehumans.bootstrap.ModEntityType;
import java.util.ArrayList;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

public class SpawnerEntity
extends Mob {
    public SpawnerEntity(EntityType<? extends Mob> p_20966_, Level p_20967_) {
        super(p_20966_, p_20967_);
    }

    public void tick() {
        if (!this.level().isClientSide) {
            this.spawn((ServerLevel)this.level());
            this.discard();
        }
    }

    private boolean spawn(ServerLevel level) {
        if (!NaturalHumanSpawnRules.beginBattle(level, blockPosition())) return false;
        try { return spawnBattle(level); }
        finally { NaturalHumanSpawnRules.endBattle(level); }
    }
    private static void addMember(ArrayList<LivingEntity> members, LivingEntity member) {
        if (member == null) return;
        members.add(member);
        NaturalHumanSpawnRules.battleMemberAdded((ServerLevel)member.level());
    }
    private boolean spawnBattle(ServerLevel level) {
        boolean tier1Allowed = NaturalHumanSpawnRules.isTierUnlocked(level, blockPosition(), 1);
        boolean tier2Allowed = NaturalHumanSpawnRules.isTierUnlocked(level, blockPosition(), 2);
        if (!tier1Allowed && !tier2Allowed) return false;
        BlockPos blockpos = this.blockPosition();
        if (hasEnoughSpace(level, blockpos) && Config.eventType() != SpawnType.Disabled) {
            if (level.getBiome(blockpos).is(Biomes.THE_VOID)) {
                return false;
            }
            boolean currentTeam = false;
            boolean isPillagers = Config.eventType() == SpawnType.HumanVsPillager || this.random.nextFloat() < 0.6f && Config.eventType() != SpawnType.HumanVsHuman;
            String[] factions = isPillagers
                    ? new String[]{dev.felix.hostilehumans.faction.HumanFactions.chooseFaction(this.random)}
                    : dev.felix.hostilehumans.faction.HumanFactions.enemyPair(this.random);
            if (factions.length == 0) return false;
            int totalAmount = this.random.nextInt(3, 18);
            boolean bannerLeft = false;
            boolean bannerRight = false;
            ArrayList<LivingEntity> spawnedEntities = new ArrayList<>();
            for (int j = 0; j < totalAmount; ++j) {
                BlockPos pos = findSpawnPositionNear(level, blockpos, 4);
                if (pos == null) continue;
                if (isPillagers && currentTeam) {
                    if (this.random.nextBoolean()) {
                        addMember(spawnedEntities, (LivingEntity)this.getRandomPillager().spawn(level, (CompoundTag)null, null, pos, MobSpawnType.EVENT, false, false));
                    }
                    addMember(spawnedEntities, (LivingEntity)this.getRandomPillager().spawn(level, (CompoundTag)null, null, pos, MobSpawnType.EVENT, false, false));
                } else {
                    EntityType<Human> humanType = tier2Allowed && (!tier1Allowed || this.random.nextFloat() < 0.05F)
                            ? ModEntityType.HUMAN2.get() : ModEntityType.HUMAN1.get();
                    CompoundTag resident = new CompoundTag(), forge = new CompoundTag();
                    forge.putString(dev.felix.hostilehumans.faction.HumanFactions.FACTION,
                            factions[isPillagers ? 0 : currentTeam ? 0 : 1]);
                    resident.put("ForgeData", forge);
                    Human newHuman = humanType.spawn(level, resident, null, pos, MobSpawnType.EVENT, false, false);
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
                        dev.felix.hostilehumans.faction.HumanFactions.refreshTeam(newHuman);
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
        // Legacy Dungeons Mobs summon substitution is suspended.
        EntityType[] list = new EntityType[]{EntityType.PILLAGER, EntityType.PILLAGER, EntityType.EVOKER, EntityType.PILLAGER, EntityType.ILLUSIONER, EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.VINDICATOR};
        return list[(int)((float)list.length * this.random.nextFloat())];
    }

    @Nullable
    private BlockPos findSpawnPositionNear(ServerLevel level, BlockPos center, int radius) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = center.getX() + random.nextInt(radius * 2) - radius;
            int z = center.getZ() + random.nextInt(radius * 2) - radius;
            if (!level.hasChunkAt(new BlockPos(x, center.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos candidate = new BlockPos(x, y, z);
            if (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, level, candidate, EntityType.WANDERING_TRADER)
                    && NaturalHumanSpawnRules.isSafePosition(level, candidate)) return candidate;
        }
        return null;
    }

    private boolean hasEnoughSpace(ServerLevel level, BlockPos origin) {
        for (BlockPos position : BlockPos.betweenClosed(origin, origin.offset(1, 2, 1))) {
            if (!level.hasChunkAt(position) || !level.getBlockState(position).getCollisionShape(level, position).isEmpty())
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

