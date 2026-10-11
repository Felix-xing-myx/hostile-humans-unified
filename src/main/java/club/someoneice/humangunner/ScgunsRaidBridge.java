package club.someoneice.humangunner;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import top.ribs.scguns.config.RaidConfig;
import top.ribs.scguns.config.RaidFlareConfig;
import top.ribs.scguns.entity.projectile.RaidFlareEntity;
import top.ribs.scguns.entity.raid.RaidManager;
import java.util.Map;
import java.util.WeakHashMap;

/** Player-owned native flare: native RaidManager owns waves, equipment, save and rewards. */
final class ScgunsRaidBridge {
    private static final Map<ServerLevel, Long> PENDING = new WeakHashMap<>();
    private ScgunsRaidBridge() {}
    static boolean launch(ServerPlayer player) {
        var settings = UnifiedConfig.get().scguns();
        ServerLevel level = player.serverLevel();
        var raid = RaidConfig.getRaidByRaidId(settings.raidId());
        if (!settings.flareEnabled() || raid == null || !RaidFlareConfig.hasFlareForRaid(settings.raidId())
                || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                || (raid.spawnConditions() != null && !raid.spawnConditions().validDimensions().isEmpty()
                    && !raid.spawnConditions().validDimensions().contains(level.dimension().location()))) {
            player.displayClientMessage(Component.translatable("message.hostile_humans_unified.scguns_flare.unavailable"), true);
            return false;
        }
        long now = level.getGameTime();
        if (RaidManager.hasActiveRaidInDimension(level) || now < PENDING.getOrDefault(level, 0L)) {
            player.displayClientMessage(Component.translatable("message.hostile_humans_unified.scguns_flare.busy"), true);
            return false;
        }
        RaidFlareEntity flare = new RaidFlareEntity(level, player, settings.raidId());
        flare.shoot(0, 1, 0, 1.5F, 0);
        if (!level.addFreshEntity(flare)) return false;
        PENDING.put(level, now + Math.max(200, RaidFlareConfig.getFlareData(settings.raidId()).burstDelay() + 40));
        return true;
    }
}
