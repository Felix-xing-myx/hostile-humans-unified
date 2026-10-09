package club.someoneice.humangunner;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Production SavedData round trips without a running world or chunk loads. */
public final class GrowthPersistenceTest {
    public static void main(String[] args) {
        UUID soldier = UUID.randomUUID(), firstOwner = UUID.randomUUID(), otherOwner = UUID.randomUUID();
        GrowthRewards store = new GrowthRewards();
        store.enqueue(soldier, firstOwner, .25);
        store.enqueue(soldier, firstOwner, 8);
        CompoundTag saved = store.save(new CompoundTag());
        CompoundTag reward = saved.getList("pending", Tag.TAG_COMPOUND).getCompound(0);
        check(reward.getDouble("amount") == 8.25 && reward.getUUID("owner").equals(firstOwner), "unloaded rewards aggregate for same owner");
        CompoundTag reloaded = GrowthRewards.load(saved).save(new CompoundTag());
        check(reloaded.equals(saved), "pending rewards, sequences and epoch survive exact round trip");
        store.enqueue(soldier, otherOwner, 1);
        reward = store.save(new CompoundTag()).getList("pending", Tag.TAG_COMPOUND).getCompound(0);
        check(reward.getDouble("amount") == 1 && reward.getUUID("owner").equals(otherOwner), "ownership change cannot transfer another owner's pending experience");
        check(reward.getLong("sequence") > saved.getLong("sequence"), "delivery sequence increases");
        saved.putBoolean("enabled", false);
        GrowthRewards disabled = GrowthRewards.load(saved);
        disabled.enqueue(soldier, firstOwner, 4);
        check(disabled.save(new CompoundTag()).getList("pending", Tag.TAG_COMPOUND).getCompound(0).getDouble("amount") == 8.25,
                "disable preserves previously earned rewards but refuses new rewards");
        System.out.println("GrowthPersistenceTest: 5 checks passed");
    }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
