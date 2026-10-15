package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/** Shared, persisted position reports; never grants combat or visual knowledge. */
public final class BeaconReportingService {
    static final String MEMBER_DIMENSION = "hostile_humans_unified:beacon_dimension";
    private static final Map<Human, Long> RECEIVED = new WeakHashMap<>();
    private BeaconReportingService() {}

    /** Diagnostic read only: no channel creation, expiry cleanup, sampling or delivery. */
    public static List<PerceptionDebugSnapshot.Row> debugRows(Human human) {
        List<PerceptionDebugSnapshot.Row> rows = new ArrayList<>();
        if (!(human.level() instanceof ServerLevel level)) {
            row(rows, "接收状态", "仅服务端可用");
            return List.copyOf(rows);
        }
        CompoundTag member = human.getPersistentData();
        boolean controlled = human.hasOwner() || member.contains(HumanRelations.HIRED_TIER)
                || HumanRelations.effectiveOwner(human) != null;
        if (!member.hasUUID(HumanRelations.FORCED_HOSTILE_TARGET)) {
            row(rows, "接收状态", controlled ? "受控：不接收追捕报告" : "无有效信标追捕身份");
            return List.copyOf(rows);
        }
        String memberDimension = member.contains(MEMBER_DIMENSION)
                ? member.getString(MEMBER_DIMENSION) : dimension(level);
        // Unlike computeIfAbsent, get never creates or dirties a missing SavedData.
        Store store = level.getServer().overworld().getDataStorage().get(Store::load,
                "hostile_humans_unified_beacon_reports");
        UUID target = member.getUUID(HumanRelations.FORCED_HOSTILE_TARGET);
        Channel channel = store == null ? null : store.channels.get(new Key(target, memberDimension));
        long now = clock(level);
        if (channel != null) {
            row(rows, "共享发报剩余", time(Math.max(0, channel.until - now)));
            row(rows, "最后成功使用距今", time(Math.max(0, now - channel.lastUse)));
            row(rows, "下次发报", now >= channel.until ? "发报期已结束"
                    : now >= channel.next ? "已到期，等待有效成员取样" : time(channel.next - now));
            row(rows, "最后报告年龄", channel.sequence == 0 ? "尚无报告"
                    : time(Math.max(0, now - channel.reportAt)));
            row(rows, "最后报告剩余", channel.sequence == 0 ? "尚无报告"
                    : time(BeaconReportPolicy.remaining(now, channel.reportUntil)));
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        String reason;
        double range = config().number("beacon.receiver_range");
        if (controlled) reason = "受控：追捕资格已退出";
        else if (!human.isAlive()) reason = "人类已失效";
        else if (!dimension(level).equals(memberDimension)) reason = "成员已离开信标使用维度";
        else if (channel == null) reason = "无共享通道：等待成功使用信标";
        else if (player == null) reason = "被追捕玩家离线";
        else if (player.serverLevel() != level) reason = "被追捕玩家在其他维度";
        else if (!valid(level, player)) reason = "玩家死亡、创造或旁观模式";
        else if (!human.canAttack(player)) reason = "当前关系或规则不允许攻击";
        else if (human.distanceToSqr(player) > range * range)
            reason = "超出同维度接收范围（" + config().integer("beacon.receiver_range") + "格）";
        else if (BeaconReportPolicy.remaining(now, channel.reportUntil) == 0)
            reason = now >= channel.until ? "发报期与最后报告均已结束" : "无有效报告，等待周期取样";
        else if (channel.sequence <= RECEIVED.getOrDefault(human, 0L)) reason = "本份报告已接收，等待下一序列";
        else reason = "已有有效报告，等待正常接收";
        row(rows, "接收状态", reason);
        return List.copyOf(rows);
    }

    private static void row(List<PerceptionDebugSnapshot.Row> rows, String label, String value) {
        rows.add(new PerceptionDebugSnapshot.Row("beacon", label, value));
    }
    private static String time(long ticks) {
        return String.format(Locale.ROOT, "%.1f秒（%dtick）", ticks / 20.0, ticks);
    }

    /** Only called after at least one hostile wave member was successfully spawned. */
    public static void used(ServerLevel level, ServerPlayer player) {
        Store store = store(level);
        long now = clock(level);
        store.cleanup(now);
        Key key = new Key(player.getUUID(), dimension(level));
        Channel channel = store.channels.computeIfAbsent(key, ignored -> new Channel());
        channel.next = BeaconReportPolicy.nextAfterUse(now, channel.until, channel.next);
        channel.lastUse = now;
        channel.until = BeaconReportPolicy.renewedUntil(now, config().integer("beacon.duration_ticks"));
        update(level, player, channel, store, now);
        store.setDirty();
    }

    /** Called for loaded humans only. One channel samples once, all members share its snapshot. */
    public static void tick(Human human) {
        if (!(human.level() instanceof ServerLevel level) || !human.isAlive()) return;
        CompoundTag member = human.getPersistentData();
        if (!member.hasUUID(HumanRelations.FORCED_HOSTILE_TARGET)) return;
        // A source marker survives hiring; the actual pursuit identity must not.
        if (human.hasOwner() || member.contains(HumanRelations.HIRED_TIER)
                || HumanRelations.effectiveOwner(human) != null) {
            member.remove(HumanRelations.FORCED_HOSTILE_TARGET);
            RECEIVED.remove(human);
            return;
        }
        if (!member.contains(MEMBER_DIMENSION)) {
            // Legacy entities have a forced UUID, but no separate use-dimension record.
            member.putString(MEMBER_DIMENSION, dimension(level));
        }
        if (!dimension(level).equals(member.getString(MEMBER_DIMENSION))) return;
        Store store = store(level);
        long now = clock(level);
        store.cleanup(now);
        UUID target = member.getUUID(HumanRelations.FORCED_HOSTILE_TARGET);
        Channel channel = store.channels.get(new Key(target, dimension(level)));
        if (channel == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(target);
        if (!valid(level, player) || !human.canAttack(player)) return;
        update(level, player, channel, store, now);
        double range = config().number("beacon.receiver_range");
        if (human.distanceToSqr(player) > range * range
                || !BeaconReportPolicy.shouldReceive(channel.sequence,
                        RECEIVED.getOrDefault(human, 0L), now, channel.reportUntil)) return;
        // Remaining TTL preserves the sample's original deadline for late joiners.
        HumanPerception.notice(human, "beacon", channel.report, target,
                BeaconReportPolicy.remaining(now, channel.reportUntil));
        RECEIVED.put(human, channel.sequence);
    }

    private static void update(ServerLevel level, ServerPlayer player, Channel channel, Store store, long now) {
        if (!BeaconReportPolicy.due(now, channel.until, channel.next)) return;
        channel.next = BeaconReportPolicy.nextAfterSample(now, channel.next,
                config().integer("beacon.interval_ticks"));
        if (valid(level, player)) {
            double grid = config().number("beacon.grid_size");
            channel.report = new Vec3(BeaconReportPolicy.gridCenter(player.getX(), grid),
                    BeaconReportPolicy.gridCenter(player.getY(), grid),
                    BeaconReportPolicy.gridCenter(player.getZ(), grid));
            channel.reportUntil = now + config().integer("beacon.report_ticks");
            channel.reportAt = now;
            channel.sequence = ++store.sequence;
        }
        store.setDirty();
    }

    private static boolean valid(ServerLevel level, ServerPlayer player) {
        return player != null && player.serverLevel() == level && player.isAlive()
                && !player.isSpectator() && !player.isCreative();
    }
    private static PerceptionConfig config() { return PerceptionConfig.get(); }
    private static String dimension(ServerLevel level) { return level.dimension().location().toString(); }
    private static long clock(ServerLevel level) { return level.getServer().overworld().getGameTime(); }
    private static Store store(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                Store::load, Store::new, "hostile_humans_unified_beacon_reports");
    }

    private record Key(UUID target, String dimension) {}
    private static final class Channel {
        long lastUse, until, next, sequence, reportAt, reportUntil;
        Vec3 report = Vec3.ZERO;
    }
    private static final class Store extends SavedData {
        final Map<Key, Channel> channels = new HashMap<>();
        long sequence;
        long cleanupAt;
        void cleanup(long now) {
            if (now < cleanupAt) return;
            cleanupAt = now + config().integer("beacon.report_ticks");
            if (channels.values().removeIf(c -> now >= c.until && now >= c.reportUntil)) setDirty();
        }
        static Store load(CompoundTag root) {
            Store store = new Store();
            store.sequence = root.getLong("sequence");
            for (Tag value : root.getList("channels", Tag.TAG_COMPOUND)) {
                CompoundTag tag = (CompoundTag)value;
                if (!tag.hasUUID("target") || tag.getString("dimension").isEmpty()) continue;
                Channel c = new Channel();
                c.lastUse = tag.getLong("last_use");
                c.until = tag.getLong("until");
                c.next = tag.getLong("next");
                c.sequence = tag.getLong("sequence");
                c.reportAt = tag.getLong("report_at");
                c.reportUntil = tag.getLong("report_until");
                double x = tag.getDouble("x"), y = tag.getDouble("y"), z = tag.getDouble("z");
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
                c.report = new Vec3(x, y, z);
                store.channels.put(new Key(tag.getUUID("target"), tag.getString("dimension")), c);
                store.sequence = Math.max(store.sequence, c.sequence);
            }
            return store;
        }
        @Override public CompoundTag save(CompoundTag root) {
            root.putLong("sequence", sequence);
            ListTag list = new ListTag();
            channels.forEach((key, c) -> {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("target", key.target());
                tag.putString("dimension", key.dimension());
                tag.putLong("last_use", c.lastUse);
                tag.putLong("until", c.until);
                tag.putLong("next", c.next);
                tag.putLong("sequence", c.sequence);
                tag.putLong("report_at", c.reportAt);
                tag.putLong("report_until", c.reportUntil);
                tag.putDouble("x", c.report.x);
                tag.putDouble("y", c.report.y);
                tag.putDouble("z", c.report.z);
                list.add(tag);
            });
            root.put("channels", list);
            return root;
        }
    }
}
