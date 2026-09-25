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

/** Natural-spawn admission shared by all Hostile Humans encounter entries. */
public final class NaturalHumanSpawnRules {
    private static final Map<ServerLevel, EncounterCooldown> CLOCKS = new WeakHashMap<>();
    private static final Map<Mob, Boolean> NATURAL_MEMBERS = new WeakHashMap<>();
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
        if (!config.enabled()) return false;
        long gameTime = level.getLevel().getGameTime();
        synchronized (PACK_DECISIONS) {
            Map<EntityType<?>, PackDecision> byType = PACK_DECISIONS.computeIfAbsent(level.getLevel(), ignored -> new HashMap<>());
            PackDecision previous = byType.get(type);
            if (previous != null
                    && previous.gameTime == gameTime
                    && previous.origin.distManhattan(pos) <= PACK_DECISION_RADIUS) {
                return previous.allowed && isSafePosition(level, pos);
            }

            if (clock(level).cooling(gameTime)) {
                return false;
            }

            // Roll the base chance before querying entities: failed attempts are cheap.
            // Cache the complete decision so a squad does not suppress its own members.
            boolean allowed = (!includeLegacyRoll || random.nextInt(config.legacyRoll()) == 0)
                    && random.nextDouble() < config.admissionChance() * tierMultiplier(type)
                    && passesDensityCheck(level, pos, random)
                    && isSafePosition(level, pos);
            byType.put(type, new PackDecision(gameTime, pos.immutable(), allowed));
            return allowed;
        }
    }

    /** Called only after the original large-battle predicate has succeeded. */
    public static boolean checkBattleAdmission(ServerLevelAccessor level, MobSpawnType spawnType,
                                                BlockPos pos, RandomSource random) {
        if (!isNaturalAttempt(spawnType)) return true;
        var config = UnifiedConfig.get().spawn();
        if (!config.enabled()) return false;
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
        Map<EntityType<?>, PackDecision> decisions = PACK_DECISIONS.get(level);
        PackDecision decision = decisions == null ? null : decisions.get(mob.getType());
        if (decision == null || !decision.allowed || decision.gameTime != now
                || decision.origin.distManhattan(mob.blockPosition()) > PACK_DECISION_RADIUS
                || !isSafePosition(level, mob.blockPosition())
                || !clock(level).join(now, decision, mob.getType() == ModEntityType.ROAMER.get() ? 1 : 5)) {
            event.setCanceled(true);
            return;
        }
    }

    public static boolean beginBattle(ServerLevel level, BlockPos pos) {
        if (clock(level).cooling(level.getGameTime())
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
        String key = type == HumanGunnerRegistries.TIER_THREE_HUMAN.get() ? "tier3"
                : type == ModEntityType.HUMAN2.get() ? "tier2"
                : type == ModEntityType.HUMAN1.get() ? "tier1" : "roamer";
        return UnifiedConfig.get().tier(key).spawnMultiplier();
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
        return SpawnSafety.unlitBuffer((dx, dy, dz) -> {
            probe.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            if (level.isOutsideBuildHeight(probe)) return false;
            // Unknown chunks must not be loaded just to admit a spawn.
            return !level.hasChunk(probe.getX() >> 4, probe.getZ() >> 4)
                    || level.getBrightness(LightLayer.BLOCK, probe) > 0;
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
