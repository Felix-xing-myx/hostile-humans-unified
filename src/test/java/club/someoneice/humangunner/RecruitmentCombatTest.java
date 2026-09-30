package club.someoneice.humangunner;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.Arrays;
import java.util.UUID;

public final class RecruitmentCombatTest {
    private static int checks;
    public static void main(String[] args) {
        check(RecruitmentPayment.pay(true, 216, 36, i -> { throw new AssertionError("Creative inventory read"); },
                (i, n) -> { throw new AssertionError("Creative charged"); }), "creative skips inventory entirely");
        check(RecruitmentPolicy.hasContractClearance(true, false, true),
                "creative contract bypasses badge and retaliation clearance");
        check(RecruitmentPolicy.hasContractClearance(false, true, false),
                "survival contract accepts a neutral non-retaliating human");
        check(!RecruitmentPolicy.hasContractClearance(false, false, false),
                "survival contract still requires matching badge clearance");
        check(!RecruitmentPolicy.hasContractClearance(false, true, true),
                "survival contract cannot recruit a retaliating human");
        for (int tier = 0; tier < 4; tier++) {
            int cost = RecruitmentPolicy.cost(tier);
            for (int total = 0; total <= cost + 80; total++) {
                int[] slots = new int[36];
                int left = total;
                for (int i = 0; i < slots.length && left > 0; i++) {
                    slots[i] = Math.min(left, 16); left -= slots[i];
                }
                int[] before = slots.clone();
                boolean paid = RecruitmentPayment.pay(false, cost, slots.length, i -> slots[i],
                        (i, n) -> slots[i] -= n);
                check(paid == (total >= cost), "survival/adventure balance check");
                check(Arrays.stream(slots).sum() == (paid ? total - cost : total), "exact split-stack payment");
                check(paid || Arrays.equals(before, slots), "insufficient payment atomic");
                check(Arrays.stream(slots).allMatch(n -> n >= 0), "no stack underflow");
            }
        }
        UUID owner = new UUID(0, 10), otherOwner = new UUID(0, 11);
        UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);
        UUID other = new UUID(0, 4), higherTier = new UUID(0, 5);
        RecruitmentLedger roster = new RecruitmentLedger();
        roster.hire(a, owner, 1);
        roster.hire(b, owner, 1);
        roster.hire(c, owner, 1);
        roster.hire(other, otherOwner, 1);
        roster.hire(higherTier, owner, 2);
        var initialOrder = roster.soldiers(owner);
        check(initialOrder == roster.soldiers(owner), "unchanged owner roster reuses immutable ordering");
        roster.hire(a, owner, 1);
        check(initialOrder == roster.soldiers(owner), "same owner and tier reconciliation retains ordering cache");
        check(roster.move(c, owner, -1, -1), "soldier moves earlier in the full roster");
        check(initialOrder != roster.soldiers(owner) && initialOrder.get(1).id().equals(b),
                "reorder invalidates cache without mutating a previously exported snapshot");
        check(roster.soldiers(owner).get(1).id().equals(c), "moved soldier changes displayed order");
        check(roster.move(c, owner, 1, -1), "soldier moves later in the full roster");
        check(roster.soldiers(owner).get(2).id().equals(c), "later move restores displayed order");
        check(roster.move(higherTier, owner, -1, -1)
                        && roster.soldiers(owner).get(2).id().equals(higherTier),
                "full roster can move a soldier across tiers");
        check(!roster.move(a, owner, -1, -1)
                        && !roster.move(higherTier, owner, -1, 1),
                "roster boundary and wrong tier filter are rejected");
        check(!roster.move(other, owner, 1, -1), "another owner's soldier cannot be reordered");
        check(roster.move(c, owner, -1, 1)
                        && roster.soldiers(owner).get(1).id().equals(c),
                "tier-filtered view reorders only its visible soldiers");
        check(roster.enterPeaceful() && roster.count(owner, 1) == 3,
                "Peaceful retains hired soldiers and their slots");
        check(roster.move(c, owner, 1, -1), "hired soldiers can still be reordered in Peaceful");
        CompoundTag rosterNbt = roster.save(new CompoundTag());
        RecruitmentLedger restored = RecruitmentLedger.load(rosterNbt);
        check(restored.count(owner, 1) == 3
                        && restored.soldiers(owner).get(2).id().equals(c)
                        && restored.soldiers(owner).get(1).id().equals(higherTier),
                "roster order and occupancy survive saving");
        for (Tag value : rosterNbt.getList("entries", Tag.TAG_COMPOUND)) {
            ((CompoundTag) value).remove("sort_order");
        }
        RecruitmentLedger legacy = RecruitmentLedger.load(rosterNbt);
        check(legacy.soldiers(owner).get(0).id().equals(a)
                        && legacy.soldiers(owner).get(2).id().equals(c),
                "older rosters retain their UUID-based display order");
        RecruitmentLedger mixedTiers = new RecruitmentLedger();
        UUID roamer = new UUID(0, 20), tierOne = new UUID(0, 21), tierTwo = new UUID(0, 22);
        mixedTiers.hire(roamer, owner, 0);
        mixedTiers.hire(tierOne, owner, 1);
        mixedTiers.hire(tierTwo, owner, 2);
        var stableView = mixedTiers.roster(owner);
        for (int repeat = 0; repeat < 1000; repeat++) {
            check(stableView == mixedTiers.roster(owner), "unchanged ledger reuses roster grouping");
            check(stableView.filtered(1) == mixedTiers.roster(owner).filtered(1), "tier filtering reuses immutable list");
        }
        check(stableView.count(1) == 1 && stableView.filtered(-1) == stableView.all(),
                "counts and All filter share one ledger snapshot");
        try { stableView.filtered(1).clear(); throw new AssertionError("tier list is mutable"); }
        catch (UnsupportedOperationException expected) { checks++; }
        check(mixedTiers.move(tierTwo, owner, -1, -1)
                        && mixedTiers.soldiers(owner).get(1).id().equals(tierTwo),
                "one soldier per tier can still be reordered on the All tab");
        check(!mixedTiers.move(tierTwo, owner, -1, 2),
                "single-soldier tier filter has no invisible reorder target");
        var oldOwnerOrder = mixedTiers.soldiers(owner);
        mixedTiers.hire(tierOne, otherOwner, 3);
        check(stableView.count(1) == 1 && mixedTiers.roster(owner).count(1) == 0,
                "owner transfer replaces grouping without mutating old snapshot");
        check(mixedTiers.soldiers(owner).size() == 2 && mixedTiers.count(owner, 1) == 0
                        && mixedTiers.count(otherOwner, 3) == 1 && oldOwnerOrder.size() == 3,
                "owner transfer invalidates both views and counts, preserving old snapshots");
        mixedTiers.dismiss(tierOne);
        check(mixedTiers.soldiers(otherOwner).isEmpty(), "dismissal invalidates last soldier view");
        check(mixedTiers.requestDismissal(roamer, owner) && mixedTiers.soldiers(owner).size() == 1,
                "unloaded dismissal immediately invalidates ordered cache");
        var emptyView = mixedTiers.roster(otherOwner);
        check(emptyView == mixedTiers.roster(otherOwner), "empty roster view also reused");
        mixedTiers.hire(tierOne, otherOwner, 1);
        check(emptyView != mixedTiers.roster(otherOwner) && emptyView.count(1) == 0
                        && mixedTiers.count(otherOwner, 1) == 1, "hiring invalidates empty owner view");
        java.util.Random groupingRandom = new java.util.Random(9076);
        RecruitmentLedger groupingLedger = new RecruitmentLedger();
        for (int step = 0; step < 200; step++) {
            UUID soldierId = new UUID(7, groupingRandom.nextInt(30));
            UUID soldierOwner = groupingRandom.nextBoolean() ? owner : otherOwner;
            if (groupingRandom.nextInt(4) == 0) groupingLedger.dismiss(soldierId);
            else groupingLedger.hire(soldierId, soldierOwner, groupingRandom.nextInt(4));
            for (UUID ownerId : java.util.List.of(owner, otherOwner)) {
                var grouped = groupingLedger.roster(ownerId);
                for (int tier = 0; tier < 4; tier++) {
                    final int tierFilter = tier;
                    var expectedGroup = grouped.all().stream().filter(s -> s.tier() == tierFilter).toList();
                    check(grouped.filtered(tier).equals(expectedGroup), "group preserves sorted order after roster mutations");
                    check(grouped.count(tier) == expectedGroup.size(), "cached count follows roster mutations");
                }
            }
        }
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        SoldierCombatMemory memory = new SoldierCombatMemory();
        check(!memory.timedOut(first, 0), "start target clock");
        check(!memory.timedOut(first, 599), "extended pursuit resists short kiting attempts");
        check(memory.timedOut(first, 600), "blocked target expires after 30 seconds without combat");
        memory.forget(first, 600);
        check(!memory.allowed(first, 659), "prevent immediate reacquisition");
        check(memory.allowed(second, 601), "other targets remain available");
        check(memory.allowed(first, 660), "retry after a short three-second reset");
        check(!memory.timedOut(first, 660), "new acquisition starts fresh");
        memory.hit(second, 1259);
        check(memory.timedOut(first, 1260), "unrelated damage cannot extend pursuit");
        memory.hit(first, 1260);
        check(!memory.timedOut(first, 1859), "effective hit extends combat");
        check(memory.timedOut(first, 1860), "stale combat still expires");
        memory.forget(first, 1860);
        check(!memory.allowed(first, 1861), "discarded target has a short re-lock delay");
        check(!memory.timedOut(second, 1861), "switch target starts fresh");

        SoldierCombatMemory pressureMemory = new SoldierCombatMemory();
        GuardCombatState guard = new GuardCombatState();
        guard.attacked(first, 0, false);
        check(guard.retaliating(first, 399), "distant self attacker remains eligible for guard response");
        check(!guard.retaliating(second, 10), "retaliation does not authorize unrelated distant enemies");
        guard.tick(100, true, true);
        guard.retainTarget(first);
        check(guard.retainsTarget(first), "accepted chase survives vanilla target-goal handoff");
        for (int tick = 101; tick < 500; tick++) {
            guard.tick(tick, true, true);
            check(!guard.returning(), "guard may fight outside for full twenty seconds");
        }
        guard.tick(500, true, true);
        check(guard.returning(), "guard returns at exact 400-tick boundary");
        check(!guard.retainsTarget(first), "excursion expiry releases retained chase");
        guard.tick(501, false, false);
        check(guard.returning(), "entering area edge does not cancel return to post");
        guard.attacked(first, 502, true);
        check(!guard.returning(), "fresh hit interrupts return");
        guard.tick(901, true, true);
        check(!guard.returning(), "fresh hit resets full excursion duration");
        guard.tick(902, true, true);
        check(guard.returning(), "renewed combat still has a finite excursion limit");
        guard.returnedToPost();
        check(!guard.returning(), "reaching post ends return state");
        guard.tick(1000, true, true);
        guard.tick(1399, true, true);
        check(!guard.returning(), "later excursion gets its own clock");
        guard.tick(1400, true, true);
        check(guard.returning(), "later excursion expires independently");
        guard.reset();
        check(guard.attacker(0) == null && !guard.returning(), "changing orders clears transient guard state");
        pressureMemory.forget(first, 0);
        pressureMemory.threatened(first, 1);
        check(pressureMemory.allowed(first, 1), "new self attack overrides old target retry delay");
        check(!pressureMemory.timedOut(first, 0), "pressure target starts fresh");
        pressureMemory.threatened(first, 500);
        check(!pressureMemory.timedOut(first, 1099), "incoming hostile action refreshes pursuit");
        check(pressureMemory.timedOut(first, 1100), "refreshed pursuit still has a finite limit");
        System.out.println("PASS: " + checks + " recruitment, roster and combat-memory checks");
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
