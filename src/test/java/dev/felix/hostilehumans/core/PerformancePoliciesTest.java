package dev.felix.hostilehumans.core;

import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** Deterministic workload checks, not a claim about live server TPS. No game launch. */
public final class PerformancePoliciesTest {
    public static final class OptionalApiFixture {
        public int range = 3;
        public int attackRange() { return range; }
        public Object failing() { throw new IllegalStateException("fixture failure"); }
    }
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Random columnRandom = new Random(7631);
        for (int trial = 0; trial < 1000; trial++) {
            double x = columnRandom.nextDouble() * 200 - 100;
            double z = columnRandom.nextDouble() * 200 - 100;
            double angle = columnRandom.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            LinkedHashSet<BlockPos> oldColumns = new LinkedHashSet<>();
            for (int forwardIndex = 0; forwardIndex < 3; forwardIndex++) {
                double forward = .4 + forwardIndex * .35;
                for (int sideIndex = -1; sideIndex <= 1; sideIndex++) {
                    double side = sideIndex * .3;
                    oldColumns.add(new BlockPos((int)Math.floor(x + dx * forward - dz * side), 0,
                            (int)Math.floor(z + dz * forward + dx * side)));
                }
            }
            BlockPos[] columns = club.someoneice.humangunner.ShoreSeekingPolicy.forwardLandingColumns(x, z, dx, dz);
            List<BlockPos> actual = Arrays.stream(columns).filter(Objects::nonNull).toList();
            check(actual.equals(new ArrayList<>(oldColumns)), "shore columns retain every distinct old position and first-match order");
            check(new HashSet<>(actual).size() == actual.size(), "shore collision probes do not repeat a column");
            for (int i = actual.size(); i < columns.length; i++) check(columns[i] == null, "unused column slots form a trailing suffix");
        }
        check(Arrays.stream(club.someoneice.humangunner.ShoreSeekingPolicy.forwardLandingColumns(.5, .5, 1, 0))
                .filter(Objects::nonNull).count() == 2, "representative cardinal probe reduces nine repeated columns to two");
        FairWorkBudget<Integer> shared = new FairWorkBudget<>(64, 4, 2);
        int[] work = new int[200];
        int[] lastGrant = new int[200];
        Arrays.fill(lastGrant, -1);
        for (int tick = 0; tick < 160; tick++) {
            int totalGrant = 0;
            for (int actor = 0; actor < work.length; actor++) {
                if (work[actor] >= 24) continue;
                int grant = shared.claim(tick, actor, Math.min(4, 24 - work[actor]));
                check(grant >= 0 && grant <= 4, "shared budget limits each caller batch");
                check(shared.claim(tick, actor, 4) == 0, "duplicate same-tick claim cannot spend twice");
                totalGrant += grant;
                if (grant > 0) {
                    check(tick - lastGrant[actor] <= 15, "stable iteration order cannot starve late callers");
                    lastGrant[actor] = tick;
                }
                work[actor] += grant;
                if (work[actor] == 24) shared.cancel(actor);
            }
            check(totalGrant <= 64, "200 callers cannot exceed server-wide candidate budget");
        }
        for (int count : work) check(count == 24, "all callers eventually complete full search");
        FairWorkBudget<String> lifecycle = new FairWorkBudget<>(4, 4, 2);
        check(lifecycle.claim(0, "a", 4) == 4, "unused capacity is available immediately");
        check(lifecycle.claim(0, "b", 4) == 0, "exhausted caller waits without work");
        lifecycle.cancel("a");
        check(lifecycle.claim(0, "a", 4) == 0, "cancel/restart cannot bypass per-tick limit");
        check(lifecycle.claim(1, "a", 4) == 0, "older waiter is reserved before new arrival");
        check(lifecycle.claim(1, "b", 4) == 4, "older waiter receives its turn");
        lifecycle.cancel("b");
        check(lifecycle.claim(8, "c", 4) == 4, "abandoned requests expire");
        check(lifecycle.claim(0, "c", 4) == 4, "clock rewind resets reservations");
        FairWorkBudget<Integer> normal = new FairWorkBudget<>(64, 4, 2);
        FairWorkBudget<Integer> emergency = new FairWorkBudget<>(32, 4, 2);
        for (int actor = 0; actor < 200; actor++) normal.claim(0, actor, 4);
        check(emergency.claim(0, 1, 4) == 4, "offensive congestion cannot consume emergency quota");
        try { shared.claim(161, 0, -1); throw new AssertionError("negative request accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        TickValueGate<String> notifications = new TickValueGate<>();
        check(notifications.accept(0, "area-a"), "first tick-zero event accepted");
        for (int repeat = 0; repeat < 1000; repeat++) {
            check(!notifications.accept(0, "area-a"), "same-area burst coalesced");
        }
        check(notifications.accept(0, "area-b"), "movement in same tick immediately updates awareness");
        check(notifications.accept(0, "area-a"), "returning to original area is a new event");
        check(notifications.accept(1, "area-a"), "next tick cannot retain notification gate");
        check(notifications.accept(0, "area-a"), "time rewind cannot retain notification gate");
        TickRevisionMemo<Integer> memo = new TickRevisionMemo<>();
        check(!memo.isCurrent(0, 0), "empty memo cannot masquerade as tick zero");
        memo.remember(100, 2, -1);
        check(memo.isCurrent(100, 2) && memo.value() == -1, "negative badge result also cached");
        check(!memo.isCurrent(101, 2), "new tick invalidates Curios or direct stack changes");
        check(!memo.isCurrent(100, 3), "inventory mutation invalidates same-tick memo");
        check(!memo.isCurrent(99, 2), "clock rewind cannot use stale result");
        memo.remember(100, 3, 4);
        check(memo.isCurrent(100, 3) && memo.value() == 4, "updated badge wins immediately");

        OptionalApiFixture fixture = new OptionalApiFixture();
        for (int i = 0; i < 100; i++) {
            fixture.range = i;
            check(CachedReflection.call(fixture, "attackRange").equals(i), "cached method still reads live value");
            check(CachedReflection.field(fixture, "range").equals(i), "cached field still reads live value");
            check(CachedReflection.call(fixture, "missing") == null, "absent method safely cached");
            check(CachedReflection.field(fixture, "missing") == null, "absent field safely cached");
        }
        check(CachedReflection.methodEntries(OptionalApiFixture.class) == 2
                && CachedReflection.fieldEntries(OptionalApiFixture.class) == 2,
                "repeated queries cannot grow the accessor cache");
        check(CachedReflection.call(fixture, "failing") == null, "optional API invocation failure is isolated");
        check(CachedReflection.call(null, "anything") == null, "missing optional object safe");

        Set<BlockPos> visited = new HashSet<>();
        BudgetedSearch<BlockPos> terrain = new BudgetedSearch<>(BlockPos.withinManhattan(BlockPos.ZERO, 10, 3, 10).iterator());
        while (terrain.hasRemaining()) {
            AtomicInteger probes = new AtomicInteger();
            check(terrain.firstMatching(128, pos -> {
                probes.incrementAndGet();
                check(Math.abs(pos.getX()) <= 10 && Math.abs(pos.getY()) <= 3 && Math.abs(pos.getZ()) <= 10,
                        "search stays inside original volume");
                check(visited.add(pos.immutable()), "each terrain block checked only once");
                return false;
            }) == null, "empty volume returns no match");
            check(probes.get() <= 128, "terrain batch budget enforced");
        }
        check(visited.size() == 21 * 7 * 21, "complete original volume covered, including corners");
        BudgetedSearch<Integer> deferred = new BudgetedSearch<>(IntStream.range(0, 1000).boxed().iterator());
        AtomicInteger tested = new AtomicInteger();
        Integer found = null;
        while (found == null && deferred.hasRemaining()) {
            found = deferred.firstMatching(4, candidate -> { tested.incrementAndGet(); return candidate == 999; });
        }
        check(found != null && found == 999 && tested.get() == 1000, "late candidate is retained across frames");
        check(!deferred.hasRemaining(), "last candidate consumes iterator");
        BudgetedSearch<Integer> paused = new BudgetedSearch<>(List.of(1, 2).iterator());
        check(paused.firstMatching(0, value -> true) == null && paused.hasRemaining(), "zero budget never consumes work");
        try { paused.firstMatching(-1, value -> true); throw new AssertionError("negative budget accepted"); }
        catch (IllegalArgumentException expected) { checks++; }

        Random random = new Random(42);
        AtomicInteger broadQueries = new AtomicInteger();
        PileFirstSelection<Integer, Integer> committed = new PileFirstSelection<>(List.of(1, 2).iterator(), true,
                () -> { broadQueries.incrementAndGet(); return List.of(100).iterator(); }, value -> value, value -> true);
        Integer pileWinner = null;
        while (!committed.isDone()) pileWinner = committed.advance(1, Integer::doubleValue);
        check(pileWinner == 2 && broadQueries.get() == 0, "a viable committed pile wins before any remote pile query");
        PileFirstSelection<Integer, Integer> corners = new PileFirstSelection<>(List.of(2).iterator(), true,
                () -> List.of(2).iterator(), value -> value, value -> false);
        Integer cornerWinner = null;
        while (!corners.isDone()) cornerWinner = corners.advance(1, Integer::doubleValue);
        check(cornerWinner == 2, "outside-sphere box corner remains eligible in broad stage");
        PileFirstSelection<Integer, Integer> heap = new PileFirstSelection<>(IntStream.range(0, 32).boxed().iterator(), true,
                () -> IntStream.range(0, 4096).boxed().iterator(), value -> value, value -> true);
        Set<Integer> heapVisited = new HashSet<>();
        Integer heapWinner = null;
        while (!heap.isDone()) {
            AtomicInteger scores = new AtomicInteger();
            heapWinner = heap.advance(64, value -> {
                scores.incrementAndGet();
                if (value != 4095) check(heapVisited.add(value), "worthless candidate evaluated only once in one complete selection");
                return value == 4095 ? 100.0D : Double.NEGATIVE_INFINITY;
            });
            check(scores.get() <= 65, "loot batch has at most 64 new scores plus one provisional revalidation");
        }
        check(heapWinner == 4095 && heapVisited.size() == 4095,
                "large worthless heap cannot hide a useful late candidate or thrash the current selection");
        PileFirstSelection<Integer, Integer> pausedPile = new PileFirstSelection<>(List.of(1).iterator(), false,
                () -> { throw new AssertionError("unexpected broad query"); }, value -> value, value -> true);
        check(pausedPile.advance(0, value -> { throw new AssertionError("zero budget evaluated item"); }) == null
                && !pausedPile.isDone(), "paused selection preserves all candidates");
        try { pausedPile.advance(-1, Integer::doubleValue); throw new AssertionError("negative loot budget accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        for (int trial = 0; trial < 200; trial++) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < 32; i++) candidates.add(random.nextInt(10000));
            IncrementalBestSearch<Integer, Integer> search = new IncrementalBestSearch<>(candidates.iterator());
            Integer selected = null;
            int total = 0;
            while (search.hasRemaining()) {
                AtomicInteger probes = new AtomicInteger();
                selected = search.advance(4, candidate -> { probes.incrementAndGet(); return candidate; }, Integer::doubleValue);
                check(probes.get() <= 4, "best-route path probes bounded per frame");
                total += probes.get();
                check(selected != null, "partial search exposes a usable provisional route");
            }
            check(total == candidates.size(), "best-route search does not truncate late candidates");
            check(Objects.equals(selected, candidates.stream().max(Integer::compareTo).orElse(null)),
                    "incremental route selection matches exhaustive scoring");
        }
        IncrementalBestSearch<Integer, Integer> changing = new IncrementalBestSearch<>(List.of(9, 2).iterator());
        check(changing.advance(1, value -> value, Integer::doubleValue) == 9, "initial provisional best");
        check(changing.advance(1, value -> value, value -> value == 9 ? Double.NEGATIVE_INFINITY : value) == 2,
                "invalid provisional route is removed when threat or movement changes");
        IncrementalBestSearch<Integer, Integer> emptyResult = new IncrementalBestSearch<>(List.of(1, 2).iterator());
        check(emptyResult.advance(0, value -> value, Integer::doubleValue) == null && emptyResult.hasRemaining(),
                "zero best-search budget consumes nothing");
        check(emptyResult.advance(2, value -> null, Integer::doubleValue) == null && !emptyResult.hasRemaining(),
                "unreachable candidates exhaust without a false route");
        try { emptyResult.advance(-1, value -> value, Integer::doubleValue); throw new AssertionError("negative budget accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        for (int trial = 0; trial < 200; trial++) {
            List<Integer> distances = new ArrayList<>();
            for (int i = 0; i < 64; i++) distances.add(random.nextInt(1000));
            Set<Integer> reachable = new HashSet<>();
            for (int value : distances) if (random.nextBoolean()) reachable.add(value);
            Integer expected = distances.stream().filter(reachable::contains).min(Integer::compareTo).orElse(null);
            distances.sort(Integer::compareTo);
            BudgetedSearch<Integer> paths = new BudgetedSearch<>(distances.iterator());
            Integer actual = null;
            while (paths.hasRemaining() && actual == null) {
                AtomicInteger pathProbes = new AtomicInteger();
                actual = paths.firstMatching(4, candidate -> { pathProbes.incrementAndGet(); return reachable.contains(candidate); });
                check(pathProbes.get() <= 4, "lava route batch budget enforced");
            }
            check(Objects.equals(expected, actual), "ranked incremental route agrees with exhaustive nearest result");
        }
        System.out.println("PerformancePoliciesTest: " + checks + " checks passed");
        System.out.println("Terrain probe burst: 3087 -> at most 128 per check; lava path burst: 64 -> at most 4 per tick.");
    }
}
