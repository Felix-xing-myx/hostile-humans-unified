"""Entry-point contracts only; Java policy and in-world detours require separate execution."""
from pathlib import Path
import unittest
PKG=Path(__file__).resolve().parents[3]/"src/main/java/club/someoneice/humangunner"

class VisualLootRoutingContract(unittest.TestCase):
    def test_visual_evidence_precedes_selection_and_all_collection(self):
        manager=(PKG/"HumanLootManager.java").read_text(encoding="utf-8")
        nearby=manager.split("ItemEntity advance(int budget",1)[1].split("private Iterator",1)[0]
        self.assertLess(nearby.index("HumanLootVision.inspect"),nearby.index("score(item, capacity)"))
        self.assertIn("return Double.NaN",nearby)
        collect=manager.split("static boolean collect(Human human, ItemEntity entity)",1)[1].split("ItemStack ground",1)[0]
        self.assertIn("HumanLootVision.Sight.VISIBLE",collect)
        vision=(PKG/"HumanLootVision.java").read_text(encoding="utf-8")
        for guard in ("withinLootVision",'PerceptionBudget.claim(h,"rays"',"ClipContext.Block.COLLIDER","RAYS_PER_HUMAN_TICK = 4"):
            self.assertIn(guard,vision)
        resupply=(PKG/"CombatResupply.java").read_text(encoding="utf-8")
        self.assertIn("Sight.DEFERRED) return",resupply)
        self.assertIn("Sight.VISIBLE) continue",resupply)

    def test_detour_is_not_cancelled_by_raw_distance_or_partial_path(self):
        goal=(PKG/"ValuableItemPickupGoal.java").read_text(encoding="utf-8")
        continuation=goal.split("boolean canContinueToUse()",1)[1].split("public void start",1)[0]
        self.assertNotIn("distanceToSqr(targetItem)",continuation)
        self.assertNotIn("bestTargetDistance",goal)
        self.assertIn("progress.stalled",goal)
        path=goal.split("private void moveToItem()",1)[1].split("private void failTarget",1)[0]
        self.assertIn("path.canReach()",goal)
        self.assertIn("HumanTerrainSafety.safePath",goal)
        self.assertIn("LongRangeNavigation.search(human,goal,LongRangeNavigation.Purpose.LOOT",path)
        self.assertIn("if (query.pending())",path)
        self.assertNotIn("createPath(targetItem",path)
        self.assertIn("waitingForPath = true",path)
        self.assertIn("nav.moveTo(previous",path)
        self.assertIn("failTarget",path)

    def test_path_failures_are_separate_from_value_and_stack_edits(self):
        goal=(PKG/"ValuableItemPickupGoal.java").read_text(encoding="utf-8")
        failed=goal.split("private boolean isFailed",1)[1].split("private int sceneHash",1)[0]
        self.assertNotIn("getCount()",failed)
        self.assertNotIn("inventoryRevision",failed)
        self.assertIn("LootRetryPolicy",failed)
        self.assertIn("other.position.distanceToSqr(item.position())",failed)
        tick=goal.split("public void tick()",1)[1].split("public void stop()",1)[0]
        self.assertIn("knownItemPosition",tick)
        self.assertIn("HumanAttention.observeLoot",tick)
        self.assertIn("if (sight == HumanLootVision.Sight.VISIBLE) human.approachWaterLoot",tick)
        cleanup=goal.split("private void releasePickupRoute",1)[1].split("private void advanceTarget",1)[0]
        self.assertIn("ownRoute",cleanup)
        self.assertIn("if (ownRoute) human.getNavigation().stop()",cleanup)

    def test_deferred_supply_resumes_and_previous_routes_are_revalidated(self):
        resupply=(PKG/"CombatResupply.java").read_text(encoding="utf-8")
        self.assertIn("List<UUID> candidates",resupply)
        self.assertIn("!state.pending && Math.floorMod",resupply)
        self.assertIn("state.candidates.get(state.cursor)",resupply)
        self.assertLess(resupply.index("Sight.DEFERRED) return"),resupply.index("state.cursor++;\n            if (sight"))
        self.assertIn('PerceptionBudget.claim(human, "queries"',resupply)
        goal=(PKG/"ValuableItemPickupGoal.java").read_text(encoding="utf-8")
        self.assertIn("nav.getPath() != previous && usableLootPath(previous)",goal)
        self.assertIn("if (usableLootPath(current)",goal)
        fallback=goal.split("if (targetItem.isInWater()",1)[1].split("failTarget",1)[0]
        self.assertIn("Sight.DEFERRED",fallback)
        self.assertIn("waitingForPath = true",fallback)
