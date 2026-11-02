package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.*;

import java.io.*;

public final class TaskLedgerTest {
    private static int checks;

    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    private static CompoundTag row(String source) {
        var row = new CompoundTag();
        row.putInt("schema", 1);
        row.putString("source", source);
        row.putString("status", "READY");
        row.putLong("copper_reward", 999);
        return row;
    }

    public static void main(String[] args) throws Exception {
        var account = new CompoundTag();
        String id = "test:same_id";
        TaskLedger.instances(account, true)
                .put(TaskLedger.key(Currency.AZURE, id), row("creative_test"));
        require(
                TaskLedger.read(account, Currency.AZURE, id, false) == null,
                "formal never finds a test definition with the same ID");
        TaskLedger.instances(account, false).put(TaskLedger.key(Currency.AZURE, id), row("formal"));
        TaskLedger.read(account, Currency.AZURE, id, false).putLong("copper_reward", 10);
        require(
                TaskLedger.read(account, Currency.AZURE, id, true).getLong("copper_reward") == 999,
                "formal definition independent of accepted test values");
        TaskLedger.history(account, Currency.AZURE, true).putBoolean("atonement_one", true);
        require(
                !TaskLedger.history(account, Currency.AZURE, false).getBoolean("atonement_one"),
                "test completion history cannot satisfy formal atonement prerequisites");
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(account, new DataOutputStream(bytes));
        var saved = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        require(
                TaskLedger.read(saved, Currency.AZURE, id, false).getLong("copper_reward") == 10
                        && TaskLedger.read(saved, Currency.AZURE, id, true).getLong("copper_reward")
                                == 999,
                "actual saved NBT retains isolated sources");
        var wrong = row("creative_test");
        TaskLedger.instances(account, false).put(TaskLedger.key(Currency.ASHEN, id), wrong);
        var before = account.copy();
        try {
            TaskLedger.read(account, Currency.ASHEN, id, false);
            throw new AssertionError("mismatched source accepted");
        } catch (IllegalArgumentException expected) {
        }
        require(account.equals(before), "mismatched-source payload retained and cannot be claimed");
        account.putString("test_tasks", "future");
        before = account.copy();
        try {
            TaskLedger.instances(account, true);
            throw new AssertionError("malformed test ledger overwritten");
        } catch (IllegalArgumentException expected) {
        }
        require(account.equals(before), "unknown namespace preserved");
        require(
                TaskLedger.read(account, Currency.AZURE, id, false).getLong("copper_reward") == 10,
                "malformed test namespace does not poison formal namespace");
        System.out.println(
                "TaskLedgerTest: "
                        + checks
                        + " actual NBT provenance/history/isolation checks passed");
    }
}
