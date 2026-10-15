"""Read-only contracts for debug isolation, observer limits and server authority; not gameplay tests."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "main/java"
COMBAT = JAVA / "club/someoneice/humangunner"


def source(name):
    return (COMBAT / (name + ".java")).read_text(encoding="utf-8")


class NavigationDebugContract(unittest.TestCase):
    def test_only_authorized_humans_and_one_controller(self):
        service = source("NavigationDebugService")
        for check in ["player.isCreative() || player.hasPermissions(2)", "instanceof Human human",
                      "old.owner.equals(player.getUUID())", "human.isNoAi()", "HumanGunner.isManualInventoryOpen(human)"]:
            self.assertIn(check, service)

    def test_test_uses_real_navigation_and_shared_budget(self):
        service = source("NavigationDebugService")
        self.assertIn("BasicNavigationBudget.tryAcquire(human, 1)", service)
        self.assertIn("navigation.createFreshPath(run.destination)", service)
        self.assertIn("human.getNavigation().moveTo(path, 1)", service)
        for forbidden in ["teleportTo(", "setNoAi(", "setHealth(", "setMaxVisitedNodesMultiplier(", "setBlock("]:
            self.assertNotIn(forbidden, service)

    def test_control_is_temporary_and_cancellable(self):
        service = source("NavigationDebugService")
        for terminal in ['finish(human, "timeout")', 'finish(human, "arrived")', 'finish(human, "cancelled")']:
            self.assertIn(terminal, service)
        self.assertIn("RUNS.remove(human)", service)
        self.assertIn("player.isShiftKeyDown()", source("NavigationTestItem"))
        self.assertNotIn("getPersistentData()", service)
        self.assertIn("PlayerLoggedOutEvent", service)

    def test_observation_is_opt_in_bounded_and_never_runs_searches(self):
        service = source("NavigationDebugService")
        sampling = service.split("public static void playerTick", 1)[1].split("private static String boundedName", 1)[0]
        self.assertIn("NAVIGATION_OBSERVER_HELMET", sampling)
        self.assertIn("player.tickCount % 5", sampling)
        self.assertIn("128 * 128", service)
        self.assertNotIn("createPath(", sampling)
        self.assertIn("if (!watching(human)) return", service)
        self.assertIn("MAX_PATH = 256", source("NavigationDebugSnapshot"))
        self.assertIn("MAX_SAMPLES = 48", source("NavigationDebugSnapshot"))

    def test_client_is_display_only_and_clears_stale_sessions(self):
        client = source("NavigationDebugClient")
        for check in ["LoggingOut", "entity.getUUID().equals(snapshot.entityUuid())", "2_000_000_000L", "dimension().location()"]:
            self.assertIn(check, client)
        self.assertNotIn("createPath(", client)
        self.assertNotIn("HumanFallPolicy", client)
        self.assertIn("PLAY_TO_CLIENT", source("NavigationDebugNetwork"))
        self.assertNotIn("PLAY_TO_SERVER", source("NavigationDebugNetwork"))

    def test_geometry_traces_do_not_change_search_quotas(self):
        self.assertIn("FairWorkBudget<>(32, 4, 40)", source("BasicNavigationBudget"))
        optional = source("OptionalPathBudget")
        for budget in ["FairWorkBudget<>(64, 4, 2)", "FairWorkBudget<>(32, 4, 2)", "FairWorkBudget<>(8, 1, 3)"]:
            self.assertIn(budget, optional)
        self.assertIn("cause unknown", (ROOT / "main/resources/assets/hostile_humans_unified/lang/en_us.json").read_text(encoding="utf-8"))

    def test_render_loop_never_allocates_native_buffers(self):
        client = source("NavigationDebugClient")
        world = client.split("public static void world", 1)[1].split("private static void label", 1)[0]
        self.assertNotIn("new BufferBuilder", world)
        self.assertNotIn("MultiBufferSource.immediate", world)
        self.assertEqual(client.count("new BufferBuilder("), 1)
        self.assertIn("NativeBuffers.BUFFERS", world)
        self.assertIn("buffers.endBatch(DebugLines.TYPE)", world)
        self.assertIn("finally", world)
        self.assertIn(".subtract(camera)", world)
        self.assertNotIn("poses.translate(-camera", world)

    def test_multi_focus_does_not_cancel_other_tests(self):
        service = source("NavigationDebugService")
        goal = service.split("private static final class TestGoal", 1)[1]
        self.assertIn("!observed(player, human)", goal)
        self.assertNotIn("selected(player) != human", goal)
        self.assertIn("session.selection.add(human.getUUID())", service)
        self.assertIn("session.selection.remove(human.getUUID())", service)
        self.assertIn("MAX_ENTITIES = 8", source("NavigationDebugFrame"))
        self.assertIn("SENT_SAMPLES = 16", source("NavigationDebugFrame"))
        self.assertIn("count(in, NavigationDebugFrame.MAX_ENTITIES)", source("NavigationDebugFrameCodec"))
        self.assertIn("Non-finite navigation geometry", source("NavigationDebugFrameCodec"))

    def test_debug_tools_have_models_and_matching_translations(self):
        assets = ROOT / "main/resources/assets/hostile_humans_unified"
        zh = json.loads((assets / "lang/zh_cn.json").read_text(encoding="utf-8"))
        en = json.loads((assets / "lang/en_us.json").read_text(encoding="utf-8"))
        self.assertEqual({k for k in zh if k.startswith("navigation_debug.")}, {k for k in en if k.startswith("navigation_debug.")})
        for item in ["navigation_test_pointer", "navigation_observer_helmet"]:
            model = json.loads((assets / "models/item" / (item + ".json")).read_text(encoding="utf-8"))
            self.assertTrue(model["textures"]["layer0"].startswith("minecraft:item/"))
            self.assertIn("item.hostile_humans_unified." + item, zh)


if __name__ == "__main__":
    unittest.main()
