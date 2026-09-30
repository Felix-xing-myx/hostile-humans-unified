package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;

/** Natural-spawn admission shared by all Hostile Humans encounter entries. */
public final class NaturalHumanSpawnRules {
    private static final Map<ServerLevel, EncounterCooldown> CLOCKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, Boolean> NATURAL_MEMBERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, Object> ACTIVE_BATTLES = new WeakHashMap<>();
    private static final int PACK_DECISION_RADIUS = 16;
    private static final Map<ServerLevelAccessor, Map<EntityType<?>, PackDecision>> PACK_DECISIONS =
            new WeakHashMap<>();

    private NaturalHumanSpawnRules() {
    }

    /**
     * Tier squads are selected by biome weight, then admitted at eight percent,
     * further reduced by nearby human density. One cached decision is shared by the nearby members of
     * the same vanilla pack so a successful 2-5 member squad is not thinned to
     * eight percent member-by-member.
     */
    public static <T extends Mob> boolean checkSquadSpawn(
            EntityType<T> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random
    ) {
        if (!isNaturalAttempt(spawnType)) {
            return Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
        }
        if (!UnifiedConfig.get().spawn().enabled() || !isTierUnlocked(level, tier(type))) return false;
        if (!isUnlitLand(level, pos) || !Mob.checkMobSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        return packDecision(type, level, pos, random, false);
    }

    /**
     * The original roamer entry already carried a 1/200 roll. Preserve it,
     * remove the old daylight-only requirement, and admit eight percent of
     * those otherwise valid attempts, with additional local density suppression.
     * Large battles keep their original predicate and use a separate post-filter.
     */
    public static <T extends Mob> boolean checkLegacySpawn(
            EntityType<T> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random
    ) {
        if (!isNaturalAttempt(spawnType)) {
            return Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
        }
        if (!UnifiedConfig.get().spawn().enabled() || !isTierUnlocked(level, tier(type))) return false;
        if (!isUnlitLand(level, pos) || !Mob.checkMobSpawnRules(type, level, spawnType, pos, random)) {
            return false;
        }
        return packDecision(type, level, pos, random, true);
    }

    private static boolean packDecision(
            EntityType<?> type,
            ServerLevelAccessor level,
            BlockPos pos,
            RandomSource random,
            boolean includeLegacyRoll
    ) {
        var config = UnifiedConfig.get().spawn();
        long gameTime = level.getLevel().getGameTime();
        PackDecision previous;
        synchronized (PACK_DECISIONS) {
            var byType = PACK_DECISIONS.get(level.getLevel());
            previous = byType == null ? null : byType.get(type);
        }
        if (samePack(previous, gameTime, pos)) return previous.allowed && isSafePosition(level, pos);
        if (clock(level).cooling(gameTime)) return false;
        // No world/entity/light query may hold the cross-dimension cache lock.
        // Failed rolls remain cheap; racing pack attempts use the first published
        // complete decision and still validate their own final position.
        boolean allowed = (!includeLegacyRoll || random.nextInt(config.legacyRoll()) == 0)
                && random.nextDouble() < config.admissionChance() * tierMultiplier(type)
                && passesDensityCheck(level, pos, random) && isSafePosition(level, pos);
        synchronized (PACK_DECISIONS) {
            var byType = PACK_DECISIONS.computeIfAbsent(level.getLevel(), ignored -> new HashMap<>());
            previous = byType.get(type);
            if (!samePack(previous, gameTime, pos)) {
                byType.put(type, new PackDecision(gameTime, pos.immutable(), allowed));
                return allowed;
            }
        }
        return previous.allowed && isSafePosition(level, pos);
    }

    private static boolean samePack(PackDecision decision, long time, BlockPos pos) {
        return decision != null && decision.gameTime == time
                && decision.origin.distManhattan(pos) <= PACK_DECISION_RADIUS;
    }

    /** Called only after the original large-battle predicate has succeeded. */
    public static boolean checkBattleAdmission(ServerLevelAccessor level, MobSpawnType spawnType,
                                                BlockPos pos, RandomSource random) {
        if (!isNaturalAttempt(spawnType)) return true;
        var config = UnifiedConfig.get().spawn();
        if (!config.enabled() || !isBattleUnlocked(level)) return false;
        long now = level.getLevel().getGameTime();
        if (clock(level).cooling(now)) return false;
        boolean allowed = random.nextDouble() < config.battleChance()
                && passesDensityCheck(level, pos, random) && isSafePosition(level, pos);
        return allowed;
    }

    public static void trackNaturalMember(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Human && isNaturalAttempt(event.getSpawnType())) {
            NATURAL_MEMBERS.put(event.getEntity(), true);
        }
    }

    /** Commit only when a natural member reaches the world insertion gate. */
    public static void onNaturalMemberJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || NATURAL_MEMBERS.remove(mob) == null
                || !(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        PackDecision decision;
        synchronized (PACK_DECISIONS) {
            Map<EntityType<?>, PackDecision> decisions = PACK_DECISIONS.get(level);
            decision = decisions == null ? null : decisions.get(mob.getType());
        }
        if (!isTierUnlocked(level, tier(mob.getType()))
                || decision == null || !decision.allowed || decision.gameTime != now
                || decision.origin.distManhattan(mob.blockPosition()) > PACK_DECISION_RADIUS
                || !isSafePosition(level, mob.blockPosition())
                || !clock(level).join(now, decision, mob.getType() == ModEntityType.ROAMER.get() ? 1 : 5)) {
            event.setCanceled(true);
            return;
        }
    }

    public static boolean beginBattle(ServerLevel level, BlockPos pos) {
        if (!UnifiedConfig.get().spawn().enabled() || !isBattleUnlocked(level)
                || clock(level).cooling(level.getGameTime())
                || ACTIVE_BATTLES.containsKey(level) || !isSafePosition(level, pos)) return false;
        ACTIVE_BATTLES.put(level, new Object());
        return true;
    }

    public static void battleMemberAdded(ServerLevel level) {
        if (ACTIVE_BATTLES.containsKey(level)) {
            clock(level).join(level.getGameTime(), ACTIVE_BATTLES.get(level), Integer.MAX_VALUE);
        }
    }

    public static void endBattle(ServerLevel level) {
        ACTIVE_BATTLES.remove(level);
    }

    private static EncounterCooldown clock(ServerLevelAccessor level) {
        return CLOCKS.computeIfAbsent(level.getLevel(), ignored -> new EncounterCooldown(UnifiedConfig.get().spawn().cooldownTicks()));
    }

    private static double tierMultiplier(EntityType<?> type) {
        String key = switch (tier(type)) {
            case 3 -> "tier3";
            case 2 -> "tier2";
            case 1 -> "tier1";
            default -> "roamer";
        };
        return UnifiedConfig.get().tier(key).spawnMultiplier();
    }

    private static int tier(EntityType<?> type) {
        return type == HumanGunnerRegistries.TIER_THREE_HUMAN.get() ? 3
                : type == ModEntityType.HUMAN2.get() ? 2
                : type == ModEntityType.HUMAN1.get() ? 1 : 0;
    }

    public static boolean isTierUnlocked(ServerLevelAccessor level, int tier) {
        var progression = UnifiedConfig.get().spawn().progression();
        return !progression.enabled() || progression.allowsTier(
                WorldProgressionData.time(level.getLevel().getServer()), tier);
    }

    private static boolean isBattleUnlocked(ServerLevelAccessor level) {
        var progression = UnifiedConfig.get().spawn().progression();
        return !progression.enabled() || progression.allowsBattle(
                WorldProgressionData.time(level.getLevel().getServer()));
    }

    /** Check every member's final position; sky light does not count as block light. */
    public static boolean isSafePosition(ServerLevelAccessor level, BlockPos pos) {
        boolean hasPlayer = false;
        for (var player : level.getLevel().players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            hasPlayer = true;
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            if (!SpawnSafety.farEnough(dx, dz)) return false;
        }
        if (!hasPlayer) return false;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        // A 33-block span touches at most three chunks per axis. Resolve their
        // loaded status once per check, not once per block in the sphere.
        byte[] loaded = new byte[9];
        int firstChunkX = (pos.getX() - 16) >> 4;
        int firstChunkZ = (pos.getZ() - 16) >> 4;
        return SpawnSafety.unlitColumns((dx, dz, minDy, maxDy) -> {
            int minY = Math.max(pos.getY() + minDy, level.getMinBuildHeight());
            int maxY = Math.min(pos.getY() + maxDy, level.getMaxBuildHeight() - 1);
            if (minY > maxY) return false;
            int x = pos.getX() + dx, z = pos.getZ() + dz;
            int chunkX = x >> 4, chunkZ = z >> 4;
            int index = (chunkX - firstChunkX) * 3 + chunkZ - firstChunkZ;
            if (loaded[index] == 0) loaded[index] = (byte) (level.hasChunk(chunkX, chunkZ) ? 1 : 2);
            // Unknown chunks remain forbidden; never load them for admission.
            if (loaded[index] == 2) return true;
            for (int y = minY; y <= maxY; y++) {
                probe.set(x, y, z);
                if (level.getBrightness(LightLayer.BLOCK, probe) > 0) return true;
            }
            return false;
        });
    }

    private static boolean passesDensityCheck(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        // Query loaded entities only; no chunk loading and no scan of unrelated monsters.
        // Human includes roamers and all three tiers, regardless of their faction.
        var config = UnifiedConfig.get().spawn();
        AABB area = new AABB(pos).inflate(config.horizontalRadius(), config.verticalRadius(),
                config.horizontalRadius());
        int nearby = level.getLevel().getEntitiesOfClass(Human.class, area, Human::isAlive).size();
        return nearby == 0 || random.nextDouble() < densityMultiplier(nearby);
    }

    static double densityMultiplier(int nearby) {
        return 1.0D / (1.0D + Math.max(0, nearby) / UnifiedConfig.get().spawn().densityDivisor());
    }

    private static boolean isNaturalAttempt(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION;
    }

    private static boolean isUnlitLand(ServerLevelAccessor level, BlockPos pos) {
        BlockPos floor = pos.below();
        return level.getBrightness(LightLayer.BLOCK, pos) == 0
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    private static final class PackDecision {
        final long gameTime;
        final BlockPos origin;
        final boolean allowed;

        PackDecision(long gameTime, BlockPos origin, boolean allowed) {
            this.gameTime = gameTime;
            this.origin = origin;
            this.allowed = allowed;
        }
    }
}
