package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.faction.StandingEffects;
import dev.felix.hostilehumans.soldier.HiredHumanRecall;
import dev.felix.hostilehumans.soldier.RecruitmentLedger;

import dev.felix.hostilehumans.entity.HumanServerData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Keeps Peaceful non-combatant; only an explicit cleanup removes unowned humans. */
public final class PeacefulHumanPolicy {
    private static final String GENERATION = HumanRuntime.MOD_ID + ":wild_cleanup_generation";
    private static final Set<Human> ASYNC_NEW_JOINS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PeacefulHumanPolicy() {
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = event.getServer();
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        if (server.overworld().getDifficulty() != Difficulty.PEACEFUL) {
            ledger.leavePeaceful();
            return;
        }
        if (!ledger.enterPeaceful()) return;
        HiredHumanRecall.cancelAll(server);
        for (ServerLevel level : server.getAllLevels())
            for (Entity entity : level.getAllEntities())
                if (entity instanceof Human human) suppressCombat(human);
    }

    public static boolean onJoin(EntityJoinLevelEvent event, Human human) {
        if (!(event.getLevel() instanceof ServerLevel level)) return true;
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            // World generation may publish off-thread. Reconcile before this human's first AI tick.
            if (!event.loadedFromDisk()) ASYNC_NEW_JOINS.add(human);
            return true;
        }
        if (reconcilePresence(level, human, !event.loadedFromDisk(), true)) {
            event.setCanceled(true);
            return false;
        }
        return true;
    }

    /** Called before AI so a late difficulty change or dismissal cannot attack once. */
    public static boolean beforeHumanTick(Human human) {
        if (!(human.level() instanceof ServerLevel level)) return false;
        boolean fresh = ASYNC_NEW_JOINS.remove(human);
        return reconcilePresence(level, human, fresh, fresh);
    }

    private static boolean reconcilePresence(ServerLevel level, Human human, boolean fresh, boolean joining) {
        var server = level.getServer();
        var ledger = RecruitmentLedger.get(server);
        long generation = ledger.wildCleanupGeneration();
        long previous = human.getPersistentData().getLong(GENERATION);
        // Mark hired humans before processing any pending dismissal. They were protected at cleanup.
        if (fresh || human.hasOwner()) {
            if (previous != generation) human.getPersistentData().putLong(GENERATION, generation);
        } else if (previous < generation) {
            purge(server, human);
            return true;
        }
        if (joining || ledger.pendingDismissalOwner(human.getUUID()) != null || human.tickCount % 20 == 0)
            reconcileLoaded(server, human);
        if (level.getDifficulty() == Difficulty.PEACEFUL) suppressCombat(human);
        return false;
    }

    public static int clearUnhired(MinecraftServer server) {
        var ledger = RecruitmentLedger.get(server);
        ledger.beginWildCleanup();
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            var removals = new ArrayList<Human>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Human human) || human.isRemoved()) continue;
                if (human.hasOwner()) human.getPersistentData().putLong(GENERATION, ledger.wildCleanupGeneration());
                else removals.add(human);
            }
            for (Human human : removals) {
                if (human.isRemoved() || human.hasOwner()) continue;
                purge(server, human);
                count++;
            }
        }
        return count;
    }

    public static void reconcileLoaded(MinecraftServer server, Human human) {
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        // A killed entity remains loaded during its death animation. Never let a
        // roster audit re-hire it after LivingDeathEvent has released its slot.
        if (!human.isAlive()) {
            ledger.dismiss(human.getUUID());
            return;
        }
        UUID pendingOwner = ledger.pendingDismissalOwner(human.getUUID());
        if (pendingOwner != null) {
            if (pendingOwner.equals(human.getOwnerUUID())) {
                HumanRelations.dismiss(server, human);
            }
            ledger.finishDismissal(human.getUUID());
        }
        if (!human.hasOwner()) {
            ledger.dismiss(human.getUUID());
            return;
        }
        UUID owner = human.getOwnerUUID();
        int tier = human.getPersistentData().contains(HumanRelations.HIRED_TIER)
                ? human.getPersistentData().getInt(HumanRelations.HIRED_TIER)
                : StandingEffects.humanRank(human);
        if (!ledger.isOwnedRecord(human.getUUID(), owner)
                || ledger.tier(human.getUUID()) != tier) {
            ledger.hire(human.getUUID(), owner, tier);
        }
    }

    private static void purge(MinecraftServer server, Human human) {
        human.setTarget(null);
        RecruitmentLedger.get(server).dismiss(human.getUUID());
        RecruitmentLedger.get(server).finishDismissal(human.getUUID());
        HiredHumanRecall.cancelPending(server, human.getUUID());
        HumanServerData data = HumanServerData.get();
        if (data != null) data.hhu$removeHuman(human.getUUID());
        human.discard();
    }

    private static void suppressCombat(Human human) {
        if (human.getTarget() != null || human.getLastHurtByMob() != null
                || human.getLastHurtMob() != null || human.getPersistentAngerTarget() != null) {
            human.forgetSoldierTarget();
        }
    }
}
