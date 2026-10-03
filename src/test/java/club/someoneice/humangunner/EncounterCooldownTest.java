package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Executable regression test, requiring only Java 17 (no downloaded test framework). */
public final class EncounterCooldownTest {
    public static void main(String[] args) {
        for (int badge = -1; badge <= 4; badge++) {
            for (int rank = 0; rank <= 3; rank++) {
                BadgePolicy.Relation expected = badge < rank ? BadgePolicy.Relation.HOSTILE
                        : badge == rank ? BadgePolicy.Relation.NEUTRAL : BadgePolicy.Relation.FRIENDLY;
                check(BadgePolicy.relation(badge, rank) == expected, "badge relation matrix");
            }
        }
        System.out.println("PASS: identity badge hostile/neutral/friendly matrix");
        int[] costs = {8, 24, 72, 216}, limits = {12, 10, 6, 3}, waves = {5, 4, 3, 2};
        UnifiedConfig defaultConfig = new UnifiedConfig(new JsonObject());
        checkRoamerAdmission(defaultConfig);
        NaturalSpawnProgression progression = defaultConfig.spawn().progression();
        int[] firstDays = {3, 5, 10, 20};
        for (int tier = 0; tier < 4; tier++) {
            long firstTick = (firstDays[tier] - 1L) * 24000L;
            check(!progression.allowsTier(firstTick - 1L, tier), "tier locked before its opening day");
            check(progression.allowsTier(firstTick, tier), "tier unlocked at opening day");
        }
        check(!progression.allowsBattle(4L * 24000L - 1L), "automatic battle waits for soldiers");
        check(progression.allowsBattle(4L * 24000L), "automatic battle unlocks with tier one");
        check(!new NaturalSpawnProgression(true, 10, 1, 1, 1, 1).allowsTier(9L * 24000L, 3),
                "safe days override earlier rank dates");
        check(new NaturalSpawnProgression(true, 0, 1, 1, 1, 1).allowsTier(0L, 0),
                "zero safe days allows configured day one");
        check(new NaturalSpawnProgression(false, 100, 500, 500, 500, 500).allowsTier(0L, 3),
                "disabled progression preserves previous eligibility");
        check(new NaturalSpawnProgression(true, 0, 20, 20, 1, 20).allowsBattle(0L),
                "custom tier two date can unlock battles independently");
        check(!progression.allowsTier(Long.MAX_VALUE, -1), "invalid tier rejected");
        check(NaturalSpawnProgression.dayAt(-100L) == 1L, "negative calendar clamped to day one");

        check(ProgressionClock.addClamped(23900L, 200L) == 24100L, "online batch advances across day boundary");
        check(ProgressionClock.addClamped(24100L, -24000L) == 100L, "day adjustment preserves fraction");
        check(ProgressionClock.addClamped(100L, -24000L) == 0L, "negative adjustment clamps to day one");
        check(ProgressionClock.addClamped(Long.MAX_VALUE - 100L, 200L) == Long.MAX_VALUE,
                "online clock cannot overflow");
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.CompoundTag players = new net.minecraft.nbt.CompoundTag();
        String first = java.util.UUID.randomUUID().toString(), second = java.util.UUID.randomUUID().toString();
        players.putLong(first, 48000L);
        players.putLong(second, 0L);
        players.putLong("invalid-id", 90000L);
        saved.put("players", players);
        var restored = PlayerProgressionData.load(saved).save(new net.minecraft.nbt.CompoundTag()).getCompound("players");
        check(restored.getLong(first) == 48000L && restored.getLong(second) == 0L,
                "personal online dates persist independently");
        check(!restored.contains("invalid-id"), "invalid saved player IDs ignored");
        PlayerProgressionData online = PlayerProgressionData.load(saved);
        java.util.UUID firstId = java.util.UUID.fromString(first), secondId = java.util.UUID.fromString(second);
        online.settle(firstId, 1000L);
        online.settle(firstId, 1200L);
        online.settle(secondId, 1100L);
        online.settle(secondId, 1200L);
        var counted = online.save(new net.minecraft.nbt.CompoundTag());
        check(counted.getCompound("players").getLong(first) == 48200L
                        && counted.getCompound("players").getLong(second) == 100L,
                "different login times accumulate independently");
        online = PlayerProgressionData.load(counted);
        online.settle(firstId, 50000L);
        check(online.save(new net.minecraft.nbt.CompoundTag()).getCompound("players").getLong(first) == 48200L,
                "offline interval and server restart are not counted");
        online.settle(firstId, 50200L);
        check(online.save(new net.minecraft.nbt.CompoundTag()).getCompound("players").getLong(first) == 48400L,
                "reconnected session continues saved progress");
        System.out.println("PASS: rank dates, safety override and persistent personal online clocks");
        SoldierChatBudget chatterBudget = new SoldierChatBudget();
        int simultaneous = 0;
        for (int i = 0; i < 100; i++) {
            if (chatterBudget.allow(0L, 1200, 6000, 3)) simultaneous++;
        }
        check(simultaneous == 1, "one owner cannot receive simultaneous squad chatter");
        check(!chatterBudget.allow(1199L, 1200, 6000, 3), "shared chatter minimum interval");
        check(chatterBudget.allow(1200L, 1200, 6000, 3)
                && chatterBudget.allow(2400L, 1200, 6000, 3), "configured chatter quota admitted");
        check(!chatterBudget.allow(3600L, 1200, 6000, 3), "rolling chatter maximum enforced");
        check(chatterBudget.allow(6000L, 1200, 6000, 3), "old chatter expires from rolling window");
        check(new SoldierChatBudget().allow(0L, 1200, 6000, 3), "different owners have independent budgets");
        check(!new SoldierChatBudget().allow(0L, 1200, 6000, 0), "zero quota disables ordinary chatter");
        SoldierChatBudget reportsBudget = new SoldierChatBudget();
        for (int i = 0; i < 6; i++) {
            check(reportsBudget.allow(i * 100L, 100, 1200, 6), "report quota");
        }
        check(!reportsBudget.allow(600L, 100, 1200, 6), "important reports also have a rolling maximum");
        check(reportsBudget.allow(1200L, 100, 1200, 6), "report quota recovers");
        System.out.println("PASS: shared owner chatter/report rolling limits and independent quotas");
        for (int tier = 0; tier < 4; tier++) {
            check(defaultConfig.recruitmentCost(tier).count() == costs[tier], "recruitment cost");
            check(RecruitmentPolicy.limit(tier, defaultConfig) == limits[tier], "follower cap");
            check(RecruitmentPolicy.waveSize(tier) == waves[tier], "wave size");
        }
        check(!RecruitmentPolicy.betrayed(25.01F, 100.0F), "above betrayal threshold");
        check(RecruitmentPolicy.betrayed(25.0F, 100.0F), "exact betrayal threshold");
        System.out.println("PASS: recruitment costs/caps, wave sizes and 25% betrayal boundary");
        for (int tier = 0; tier < 4; tier++) {
            for (int other = 0; other < 4; other++) {
                if (tier != other) {
                    check(!SignalPolicy.cooldownKey(false, tier).equals(SignalPolicy.cooldownKey(false, other)),
                            "support cooldown keys must be tier-specific");
                    check(!SignalPolicy.cooldownKey(true, tier).equals(SignalPolicy.cooldownKey(true, other)),
                            "hostile cooldown keys must be tier-specific");
                }
            }
            check(!SignalPolicy.cooldownKey(false, tier).equals(SignalPolicy.cooldownKey(true, tier)),
                    "support and hostile cooldowns must be separate");
        }
        check(SignalPolicy.cooldownTicks(false) == 9600L, "support cooldown");
        check(SignalPolicy.cooldownTicks(true) == 40L, "hostile cooldown");
        check(SignalPolicy.SUPPORT_LIFETIME_TICKS == 4800L, "support lifetime");
        check(SignalPolicy.minimumDistance(false) == 2 && SignalPolicy.maximumDistance(false) == 6,
                "support spawns beside owner");
        check(SignalPolicy.minimumDistance(true) == 48 && SignalPolicy.maximumDistance(true) == 60,
                "hostile signal distance");
        check(SoldierOrder.values().length == 4, "exactly four soldier orders");
        System.out.println("PASS: per-tier signal cooldowns, support lifetime/distances and soldier orders");
        check(!SpawnSafety.farEnough(96, 0), "96 block boundary excluded");
        check(!SpawnSafety.farEnough(-96, 0), "negative boundary excluded");
        check(SpawnSafety.farEnough(96.001, 0), "strictly beyond 96 allowed");
        check(!SpawnSafety.farEnough(60, 60), "diagonal within radius excluded");
        check(SpawnSafety.farEnough(70, 70), "diagonal beyond radius allowed");
        check(SpawnSafety.unlitBuffer((x,y,z) -> false), "fully dark buffer allowed");
        int[][] cells = {{0,0,0},{16,0,0},{-16,0,0},{0,16,0},{0,-16,0},{0,0,16},{0,0,-16},{9,9,9}};
        for (int[] cell : cells) {
            check(!SpawnSafety.unlitBuffer((x,y,z) -> x == cell[0] && y == cell[1] && z == cell[2]),
                    "lit cell within buffer must deny spawn");
        }
        check(SpawnSafety.unlitBuffer((x,y,z) -> x == 16 && y == 1 && z == 0), "outside spherical boundary");
        check(SpawnSafety.unlitBuffer((x,y,z) -> x == 17 && y == 0 && z == 0), "17 block dark gap");
        java.util.Set<String> sphericalCells = new java.util.HashSet<>();
        SpawnSafety.unlitColumns((x, z, minY, maxY) -> {
            for (int y = minY; y <= maxY; y++) {
                check(sphericalCells.add(x + "," + y + "," + z), "columns contain no duplicate cells");
            }
            return false;
        });
        int referenceCells = 0;
        for (int x = -16; x <= 16; x++) {
            for (int y = -16; y <= 16; y++) {
                for (int z = -16; z <= 16; z++) {
                    boolean inside = x*x + y*y + z*z <= 256;
                    check(sphericalCells.contains(x + "," + y + "," + z) == inside,
                            "precomputed columns preserve exact spherical geometry");
                    if (inside) referenceCells++;
                }
            }
        }
        check(sphericalCells.size() == referenceCells, "no extra cells outside sphere");
        for (int originX = -33; originX <= 33; originX++) {
            for (int originZ = -33; originZ <= 33; originZ++) {
                int firstX = (originX - 16) >> 4, firstZ = (originZ - 16) >> 4;
                for (int dx : new int[]{-16, 0, 16}) {
                    for (int dz : new int[]{-16, 0, 16}) {
                        int xIndex = ((originX + dx) >> 4) - firstX;
                        int zIndex = ((originZ + dz) >> 4) - firstZ;
                        check(xIndex >= 0 && xIndex < 3 && zIndex >= 0 && zIndex < 3,
                                "chunk memo indexing covers negative coordinates and boundaries");
                    }
                }
            }
        }
        System.out.println("PASS: strict 96-block horizontal distance and 16-block spherical light buffer");
        EncounterCooldown clock = new EncounterCooldown();
        Object firstSquad = new Object(), secondSquad = new Object();
        check(!clock.cooling(100), "failed/no attempts must not start cooldown");
        for (int i = 0; i < 5; i++) check(clock.join(100, firstSquad, 5), "same squad member " + i);
        check(!clock.join(100, firstSquad, 5), "sixth member must be denied");
        check(!clock.join(100, secondSquad, 5), "second squad in same tick must be denied");
        check(!clock.join(101, firstSquad, 5), "token reuse on later tick must be denied");
        check(!clock.join(2499, secondSquad, 5), "cooldown must last full 2400 ticks");
        check(clock.join(2500, secondSquad, 5), "new squad at exact cooldown expiry");
        EncounterCooldown concurrentClock = new EncounterCooldown();
        Object concurrentBatch = new Object();
        var workers = java.util.concurrent.Executors.newFixedThreadPool(8);
        var accepted = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 64; i++) {
                jobs.add(workers.submit(() -> {
                    if (concurrentClock.join(100, concurrentBatch, 5)) accepted.incrementAndGet();
                }));
            }
            for (var job : jobs) job.get();
            check(accepted.get() == 5, "concurrent admission cannot exceed the shared pack limit");
            check(concurrentClock.cooling(101), "concurrent successful admission publishes cooldown");
        } catch (Exception failure) {
            throw new AssertionError("concurrent admission regression", failure);
        } finally { workers.shutdownNow(); }
        EncounterCooldown battle = new EncounterCooldown();
        for (int i = 0; i < 36; i++) check(battle.join(200, first, Integer.MAX_VALUE), "battle member");
        check(!battle.join(200, second, 5), "battle must suppress normal squad");
        check(!clock.join(2500, first, Integer.MAX_VALUE), "normal squad must suppress battle");
        EncounterCooldown roamer = new EncounterCooldown();
        check(roamer.join(0, first, 1), "first roamer");
        check(!roamer.join(0, first, 1), "roamer entry remains solitary");
        check(!roamer.join(2400, null, 1), "missing token rejected");
        check(roamer.join(2400, second, 1), "rejection must not consume next interval");
        System.out.println("PASS: squad size, cross-entry cooldown, battle, roamer, expiry, failed admission");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void checkRoamerAdmission(UnifiedConfig config) {
        double roamer = NaturalHumanSpawnRules.admissionChance(config.spawn().admissionChance(),
                config.tier("roamer").spawnMultiplier());
        double tier1 = NaturalHumanSpawnRules.admissionChance(config.spawn().admissionChance(),
                config.tier("tier1").spawnMultiplier());
        check(roamer == .08 && roamer == tier1, "no hidden roamer 1/200 gate");
        check(NaturalHumanSpawnRules.admissionChance(.08, .005) == .0004,
                "fractional per-tier multipliers remain supported");
        check(NaturalHumanSpawnRules.admissionChance(.08, 0) == 0
                        && NaturalHumanSpawnRules.admissionChance(.08, 100) == 1,
                "disabled and saturated admission limits");
        JsonObject oldConfig = new JsonObject();
        JsonObject oldSpawning = new JsonObject();
        oldSpawning.addProperty("roamer_legacy_roll", 200);
        oldConfig.add("spawning", oldSpawning);
        UnifiedConfig retained = new UnifiedConfig(oldConfig);
        check(NaturalHumanSpawnRules.admissionChance(retained.spawn().admissionChance(),
                        retained.tier("roamer").spawnMultiplier()) == roamer,
                "retained obsolete config cannot reintroduce the roamer gate");
        int roamerWeight = 0, tier1Weight = 0, roamerEntries = 0;
        for (String resource : new String[]{
                "/data/hostile_humans/forge/biome_modifier/human_spawns.json",
                "/data/humangunner/forge/biome_modifier/human_squad_spawns.json"}) {
            try (var reader = new java.io.InputStreamReader(
                    java.util.Objects.requireNonNull(EncounterCooldownTest.class.getResourceAsStream(resource)),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                var spawners = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("spawners");
                for (var element : spawners) {
                    var row = element.getAsJsonObject();
                    String type = row.get("type").getAsString();
                    if (type.equals("hostile_humans:human_roamer")) {
                        roamerEntries++;
                        roamerWeight += row.get("weight").getAsInt();
                        check(row.get("minCount").getAsInt() == 1 && row.get("maxCount").getAsInt() == 1,
                                "roamers stay solitary");
                    } else if (type.equals("hostile_humans:human_tier1")) tier1Weight += row.get("weight").getAsInt();
                }
            } catch (java.io.IOException error) { throw new AssertionError("Cannot inspect spawn resources", error); }
        }
        check(roamerEntries == 1 && roamerWeight == 10 && tier1Weight == 3,
                "single roamer entry retains higher selection weight than tier one");
    }
}
