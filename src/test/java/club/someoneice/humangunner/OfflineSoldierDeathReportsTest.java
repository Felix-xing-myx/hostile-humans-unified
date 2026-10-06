package club.someoneice.humangunner;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Persistence and recipient isolation without starting a server. */
public final class OfflineSoldierDeathReportsTest {
    private static int assertions;

    public static void main(String[] args) {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var store = new OfflineSoldierDeathReports.Store();
        var drowned = death(owner, "海边士兵", "death.attack.drown");
        store.add(drowned);
        store.add(drowned);
        store.add(death(other, "另一位玩家的士兵", "death.attack.fall"));
        check(store.peek(owner).deaths().size() == 1, "duplicate UUID reported once");
        check(store.peek(other).deaths().size() == 1, "different owners have separate inboxes");
        check(store.peek(UUID.randomUUID()).deaths().isEmpty(), "unrelated recipient sees no reports");

        var restored = OfflineSoldierDeathReports.Store.load(store.save(new CompoundTag()));
        check(restored.peek(owner).deaths().equals(store.peek(owner).deaths()), "death details survive save/reload");
        check(Component.Serializer.toJson(Component.Serializer.fromJson(drowned.causeJson())).equals(drowned.causeJson()),
                "localized cause remains a structured component");
        restored.clear(owner);
        check(restored.peek(owner).deaths().isEmpty(), "delivery acknowledgement clears recipient");
        check(restored.peek(other).deaths().size() == 1, "acknowledgement cannot erase another owner's report");
        var afterAck = OfflineSoldierDeathReports.Store.load(restored.save(new CompoundTag()));
        check(afterAck.peek(owner).deaths().isEmpty(), "cleared reports stay cleared after restart");
        check(afterAck.peek(other).deaths().size() == 1, "other owner's reports persist after restart");

        for (int i = 0; i < 40; i++) store.add(death(owner, "Soldier " + i, "death.attack.generic"));
        check(store.peek(owner).deaths().size() == OfflineSoldierDeathReports.MAX_REPORTS, "detail storage has bounded size");
        check(store.peek(owner).overflow() == 9, "excess deaths retain an exact count");
        var bounded = OfflineSoldierDeathReports.Store.load(store.save(new CompoundTag()));
        check(bounded.peek(owner).deaths().size() == 32 && bounded.peek(owner).overflow() == 9,
                "bounded details and overflow survive restart");
        bounded.clear(owner);
        check(bounded.peek(owner).overflow() == 0, "acknowledgement also clears overflow");
        CompoundTag invalid = new CompoundTag();
        invalid.putString("Owners", "not a list");
        check(OfflineSoldierDeathReports.Store.load(invalid).peek(owner).deaths().isEmpty(), "invalid list is safely ignored");

        CompoundTag persistent = new CompoundTag();
        OfflineSoldierDeathReports.saveCandidate(persistent, drowned);
        check(OfflineSoldierDeathReports.onCandidateLeave(persistent, drowned.human(),
                Entity.RemovalReason.UNLOADED_TO_CHUNK) == null, "unload never confirms a death");
        check(persistent.contains(OfflineSoldierDeathReports.CANDIDATE), "unload retains candidate in entity NBT");
        check(OfflineSoldierDeathReports.onCandidateLeave(persistent, drowned.human(),
                Entity.RemovalReason.CHANGED_DIMENSION) == null, "dimension transfer never confirms a death");
        CompoundTag reloadedEntity = persistent.copy();
        check(OfflineSoldierDeathReports.onCandidateLeave(reloadedEntity, drowned.human(),
                Entity.RemovalReason.KILLED).equals(drowned), "entity NBT alone recovers a confirmed death after reload");
        check(!reloadedEntity.contains(OfflineSoldierDeathReports.CANDIDATE), "KILLED consumes candidate");
        check(OfflineSoldierDeathReports.onCandidateLeave(reloadedEntity, drowned.human(),
                Entity.RemovalReason.KILLED) == null, "repeated KILLED cannot redeliver candidate");
        check(!OfflineSoldierDeathReports.clearRevivedCandidate(persistent, 0, false), "death animation retains candidate");
        check(!OfflineSoldierDeathReports.clearRevivedCandidate(persistent, 5, false), "positive health alone is insufficient");
        check(OfflineSoldierDeathReports.clearRevivedCandidate(persistent, 5, true), "revival clears stale candidate");
        check(OfflineSoldierDeathReports.onCandidateLeave(persistent, drowned.human(),
                Entity.RemovalReason.KILLED) == null, "revived candidate cannot create later false notice");
        OfflineSoldierDeathReports.saveCandidate(persistent, drowned);
        check(OfflineSoldierDeathReports.onCandidateLeave(persistent, drowned.human(),
                Entity.RemovalReason.DISCARDED) == null && !persistent.contains(OfflineSoldierDeathReports.CANDIDATE),
                "discard consumes evidence without claiming death");
        OfflineSoldierDeathReports.saveCandidate(persistent, drowned);
        check(OfflineSoldierDeathReports.onCandidateLeave(persistent, UUID.randomUUID(),
                Entity.RemovalReason.KILLED) == null, "foreign UUID cannot reuse candidate");
        System.out.println("OfflineSoldierDeathReportsTest passed " + assertions + " assertions");
    }

    private static OfflineSoldierDeathReports.Death death(UUID owner, String name, String key) {
        return new OfflineSoldierDeathReports.Death(UUID.randomUUID(), owner, name,
                Component.Serializer.toJson(Component.translatable(key, Component.literal(name))),
                "minecraft:overworld", -21, 64, 18);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
