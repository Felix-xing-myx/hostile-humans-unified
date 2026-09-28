package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanServerData;
import com.craftix.hostile_humans.entity.entities.Human;
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

/** Removes wild Humans in Peaceful while keeping hired soldiers non-combatant. */
public final class PeacefulHumanPolicy {
    private static final String GENERATION = HumanGunner.MOD_ID + ":peaceful_generation";
    private static final Set<Human> ASYNC_NEW_JOINS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PeacefulHumanPolicy() {
    }

    static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        MinecraftServer server = event.getServer();
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        if (server.overworld().getDifficulty() != Difficulty.PEACEFUL) {
            ledger.leavePeaceful();
            return;
        }
        if (!ledger.enterPeaceful()) {
            // Also repair old wild index entries after loading a Peaceful save.
            if (server.overworld().getGameTime() % 200 == 0) {
                HumanServerData stale = HumanServerData.get();
                if (stale != null) stale.clearWildHumans();
            }
            return;
        }
        HiredHumanRecall.cancelAll(server);
        HumanServerData data = HumanServerData.get();
        for (ServerLevel level : server.getAllLevels()) {
            ArrayList<Human> loaded = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Human human) loaded.add(human);
            }
            for (Human human : loaded) {
                reconcileLoaded(server, human);
                if (human.hasOwner()) {
                    markRetained(human, ledger);
                    suppressCombat(human);
                    if (data != null && human.isAlive()) data.updateOrRegisterHumanMob(human);
                }
                else purge(server, human);
            }
        }
        if (data != null) data.clearWildHumans();
    }

    static boolean onJoin(EntityJoinLevelEvent event, Human human) {
        if (!(event.getLevel() instanceof ServerLevel level)) return true;
        MinecraftServer server = level.getServer();
        if (!server.isSameThread()) {
            // World generation may publish a join off-thread. Never access SavedData
            // there; the first server tick reconciles before this Human's AI runs.
            if (!event.loadedFromDisk()) ASYNC_NEW_JOINS.add(human);
            return true;
        }
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        if (!event.loadedFromDisk()) {
            human.getPersistentData().putLong(GENERATION, ledger.peacefulGeneration());
        }
        reconcileLoaded(server, human);
        if (!human.hasOwner() && (level.getDifficulty() == Difficulty.PEACEFUL
                || (event.loadedFromDisk()
                && human.getPersistentData().getLong(GENERATION) < ledger.peacefulGeneration()))) {
            purge(server, human);
            event.setCanceled(true);
            return false;
        }
        if (human.hasOwner()) markRetained(human, ledger);
        if (level.getDifficulty() == Difficulty.PEACEFUL) suppressCombat(human);
        return true;
    }

    /** Called before AI so a late difficulty change or dismissal cannot attack once. */
    public static boolean beforeHumanTick(Human human) {
        if (!(human.level() instanceof ServerLevel level)) return false;
        MinecraftServer server = level.getServer();
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        boolean newlyJoinedAsync = ASYNC_NEW_JOINS.remove(human);
        if (newlyJoinedAsync) {
            human.getPersistentData().putLong(GENERATION, ledger.peacefulGeneration());
        }
        if (ledger.pendingDismissalOwner(human.getUUID()) != null
                || human.tickCount % 20 == 0) reconcileLoaded(server, human);
        if (!human.hasOwner() && (level.getDifficulty() == Difficulty.PEACEFUL
                || human.getPersistentData().getLong(GENERATION) < ledger.peacefulGeneration())) {
            purge(server, human);
            return true;
        }
        if (human.hasOwner()) markRetained(human, ledger);
        if (level.getDifficulty() == Difficulty.PEACEFUL) suppressCombat(human);
        return false;
    }

    static void reconcileLoaded(MinecraftServer server, Human human) {
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
                : IdentityBadgeAccess.humanRank(human);
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
        if (data != null) data.humanGunner$removeHuman(human.getUUID());
        human.discard();
    }

    private static void suppressCombat(Human human) {
        if (human.getTarget() != null || human.getLastHurtByMob() != null
                || human.getLastHurtMob() != null || human.getPersistentAngerTarget() != null) {
            human.forgetSoldierTarget();
        }
    }

    private static void markRetained(Human human, RecruitmentLedger ledger) {
        if (human.getPersistentData().getLong(GENERATION) < ledger.peacefulGeneration()) {
            human.getPersistentData().putLong(GENERATION, ledger.peacefulGeneration());
        }
    }
}
