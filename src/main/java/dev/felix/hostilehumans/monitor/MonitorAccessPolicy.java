package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanRelations;
import net.minecraft.world.entity.player.Player;

/** One permission rule for item, roster, live viewing and monitored inventory access. */
public final class MonitorAccessPolicy {
    private MonitorAccessPolicy() { }
    public static boolean allows(Player player, Human human) {
        return player.isAlive() && !player.isSpectator() && human.isAlive() && !human.isRemoved()
                && player.level() == human.level() && (player.isCreative() || HumanRelations.isOwnedBy(human, player));
    }
    public static boolean transfers(Player player) { return player.isCreative(); }
}
