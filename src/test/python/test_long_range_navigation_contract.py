"""Authority, bounds and deferred-work contracts; not Java/runtime or TPS verification."""
from pathlib import Path
import json
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/"src/main/java/club/someoneice/humangunner"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")

class LongRangeNavigationContract(unittest.TestCase):
    def test_explicit_ranges_do_not_mutate_senses_or_attributes(self):
        planner=source("LongRangeNavigation")
        for forbidden in ("FOLLOW_RANGE", "setAttribute", "setBaseValue", "setTarget(", "teleportTo(", "getEntity("):
            self.assertNotIn(forbidden,planner)
        data=json.loads((ROOT/"src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))["navigation_search"]
        self.assertEqual([data[k] for k in ("normal_range","extended_range","long_range")],[48,80,128])
        self.assertEqual([data[k] for k in ("normal_nodes","extended_nodes","long_nodes")],[1536,2048,4096])
        self.assertIn('"navigation_search"',source("TierConfigProjection"))

    def test_trial_is_isolated_and_uses_native_human_safety(self):
        nav=(ROOT/"src/main/java/com/craftix/hostile_humans/entity/ai/HumanNavigation.java").read_text(encoding="utf-8")
        trial=nav.split("public Path createBoundedPath",1)[1].split("@Override public BlockPos getTargetPos",1)[0]
        self.assertNotIn("super.createPath",trial)
        self.assertNotIn("this.path =",trial)
        self.assertNotIn("setMaxVisitedNodesMultiplier",trial)
        self.assertIn("new HumanNodeEvaluator()",trial)
        self.assertLess(trial.index("hasChunkAt(goal)"),trial.index("getBlockState"))
        self.assertIn("nodes / 16384.0F",trial)
        for flag in ("setCanPassDoors","setCanOpenDoors","setCanFloat","setCanWalkOverFences"):
            self.assertIn(flag,trial)
        self.assertEqual(nav.count("boolean moveTo(Path"),1)
        self.assertIn("HumanTerrainSafety.assessPath",nav)
        self.assertIn("boundedRefresh = true;hasDelayedRecomputation = false",nav)
        self.assertIn("level.getGameTime()-timeLastRecompute > 20",nav)

    def test_remote_cost_and_failure_cache_are_bounded(self):
        planner=source("LongRangeNavigation")
        self.assertIn("RouteSearchPolicy.remoteCost(nodes)",planner)
        self.assertIn("claimFull(server.getTickCount(),human.getUUID(),cost)",planner)
        self.assertIn("s.queriedTick == tick",planner)
        self.assertIn("s.failures.size()>=c.cacheEntries()",planner)
        self.assertIn("RouteSearchPolicy.retry",planner)
        self.assertIn("getWorldBorder().isWithinBounds(goal)",planner)
        self.assertIn("pool.queue.cancel(human.getUUID())",planner)
        self.assertNotIn("new Thread",planner)

    def test_consumers_defer_before_failure_accounting(self):
        for name,purpose,failed in (("PursuitNavigation","COMBAT","state.failed = true"),
                ("ValuableItemPickupGoal","LOOT","failTarget(\"没有完整"),
                ("SoldierFollowNavigation","FOLLOW","retryDelay(++state.failures)"),
                ("PerceptionInvestigationGoal","INVESTIGATION","planning=false;if(!refreshing)points++")):
            code=source(name)
            self.assertIn("LongRangeNavigation.Purpose."+purpose,code)
            self.assertLess(code.index("query.pending()"),code.index(failed))
            self.assertIn("LongRangeNavigation.accepted",code)
        follow=source("SoldierFollowNavigation")
        self.assertIn("if(replacement.canReach())state.failures=0",follow)
        self.assertIn("Scheduling wait cannot trigger teleport rescue",follow)

    def test_test_mode_and_all_cancellation_use_same_planner(self):
        service=source("NavigationDebugService")
        self.assertIn("LongRangeNavigation.Purpose.TEST",service)
        self.assertIn("Math.min(96,value.length())",service)
        self.assertIn("LongRangeNavigation.describe(human)",service)
        self.assertIn("LongRangeNavigation.cancelAll(human)",source("SoldierOrder"))
        for name in ("PursuitNavigation","ValuableItemPickupGoal","SoldierFollowNavigation","PerceptionInvestigationGoal"):
            self.assertIn("LongRangeNavigation.cancel",source(name))

    def test_moving_demand_keeps_fifo_and_investigation_tracks_actual_route(self):
        planner=source("LongRangeNavigation")
        changed=planner.split("if (changedGoal)",1)[1].split("Path current =",1)[0]
        self.assertNotIn("cancelQueue",changed)
        self.assertIn("Math.max(s.stage,initial)",changed)
        self.assertIn("if(!wasPending)s.queuedAt=-1",changed)
        investigation=source("PerceptionInvestigationGoal")
        self.assertIn("MovementContinuity.ownsRoute(h,ownPath)",investigation)
        self.assertIn("boolean lostRoute=",investigation)
        self.assertIn("if(refreshing)planning=true",investigation)
        self.assertIn("if(!refreshing)points++",investigation)
        reuse=planner.split("if (s.route != null && current",1)[1].split("// A completed",1)[0]
        self.assertIn("if (tick >= s.nextTick) s.nextTick",reuse)
        self.assertNotIn("Math.max(s.nextTick,tick",reuse)

    def test_route_reuse_keeps_watchdog_and_new_acceptance_resets_node_timeout(self):
        nav=(ROOT/"src/main/java/com/craftix/hostile_humans/entity/ai/HumanNavigation.java").read_text(encoding="utf-8")
        move=nav.split("boolean moveTo(Path",1)[1].split("@Override public boolean isStuck",1)[0]
        reuse=move.split("if (sameRoute)",1)[1].split("else accepted",1)[0]
        self.assertIn("setSpeedModifier(speed)",reuse)
        self.assertNotIn("super.moveTo",reuse)
        self.assertIn("if (this.path != previous)",move)
        self.assertIn("timeoutCachedNode = net.minecraft.core.Vec3i.ZERO",move)
        self.assertIn("timeoutTimer = 0;timeoutLimit = 0;lastTimeoutCheck",move)
        self.assertIn("trialBoundedPath = null;trialBoundedGoal = null",move)
        trial=nav.split("public Path createBoundedPath",1)[1].split("@Override public BlockPos",1)[0]
        self.assertNotIn("timeoutTimer =",trial)
        stuck=nav.split("boolean isStuck()",1)[1].split("@Override public void tick",1)[0]
        self.assertIn("path == activeBoundedPath && !path.isDone()",stuck)
        self.assertIn("super.isStuck()",stuck)
