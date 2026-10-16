"""Population census and budget-switch authority contracts; not runtime load benchmarks."""
from pathlib import Path
import json,unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/"src/main/java/club/someoneice/humangunner"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")
class AdaptiveActivityContract(unittest.TestCase):
    def test_heartbeat_precedes_early_returns_and_diagnostics_do_not_sample(self):
        tick=source("HumanGunner").split("private static void onLivingTick",1)[1]
        self.assertLess(tick.index("AdaptiveActivityBudget.touch(human)"),tick.index("initializeHumanIfReady"))
        module=source("AdaptiveActivityBudget")
        self.assertIn("Map<UUID,Integer> present",module)
        self.assertNotIn("getEntities",module)
        diagnostic=module.split("public static String describe",1)[1]
        self.assertNotIn("touch(",diagnostic)
    def test_mode_changes_preserve_native_budget_state(self):
        fair=(ROOT/"src/main/java/dev/felix/hostilehumans/core/FairWorkBudget.java").read_text(encoding="utf-8")
        setter=fair.split("public void setTickCapacity",1)[1].split("public int claim",1)[0]
        self.assertNotIn("clear()",setter)
        self.assertNotIn("remaining =",setter)
        self.assertIn("remaining = nextTickCapacity",fair)
        for name in ("BasicNavigationBudget","OptionalPathBudget","LongRangeNavigation"):
            self.assertIn("setTickCapacity(AdaptiveActivityBudget.navigationCapacity",source(name))
        self.assertNotIn("pools.emergency().setTickCapacity",source("OptionalPathBudget"))
        self.assertNotIn("pools.continuity().setTickCapacity",source("OptionalPathBudget"))
    def test_shared_config_and_no_entity_retention(self):
        data=json.loads((ROOT/"src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))["activity_budget"]
        self.assertEqual([data[k] for k in ("sparse_humans","crowded_humans","battle_humans")],[16,64,128])
        self.assertIn('"activity_budget"',source("TierConfigProjection"))
        self.assertIn('ActivityBudgetConfig.parse(object(root,"activity_budget"))',source("UnifiedConfig"))
        module=source("AdaptiveActivityBudget")
        self.assertIn("if(s.lower!=desired)",module)
        self.assertIn("tick-s.lowerSince>=c.downgradeTicks()",module)
