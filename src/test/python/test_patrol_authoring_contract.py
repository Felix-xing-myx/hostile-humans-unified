"""Static integration/authority checks complement executable Java policy/codec regressions."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/dev/felix/hostilehumans"

def source(path):
    return (JAVA / path).read_text(encoding="utf-8")

class PatrolAuthoringContract(unittest.TestCase):
    def test_registered_tools_have_models_and_survival_recipe(self):
        registry = source("bootstrap/ContentRegistry.java")
        for item in ("patrol_command_baton", "creative_soldier_controller"):
            self.assertEqual(registry.count('ITEMS.register("' + item + '"'), 1)
            model = ROOT / "src/main/resources/assets/hostile_humans_unified/models/item" / (item + ".json")
            self.assertTrue(json.loads(model.read_text(encoding="utf-8"))["textures"]["layer0"].startswith("minecraft:item/"))
        recipe = json.loads((ROOT / "src/main/resources/data/hostile_humans_unified/recipes/patrol_command_baton.json").read_text(encoding="utf-8"))
        self.assertEqual(recipe["result"]["item"], "hostile_humans_unified:patrol_command_baton")
        self.assertFalse((ROOT / "src/main/resources/data/hostile_humans_unified/recipes/creative_soldier_controller.json").exists())

    def test_server_draft_has_cleanup_without_item_nbt_or_chunk_tickets(self):
        editor = source("soldier/PatrolRouteEditor.java")
        self.assertIn("Map<ServerPlayer,Draft>", editor)
        self.assertNotIn("getOrCreateTag", editor)
        self.assertNotIn("getChunk(", editor)
        self.assertIn("getMainHandItem()", editor)
        self.assertIn("getOffhandItem()", editor)
        for event in ("PlayerLoggedOutEvent", "PlayerChangedDimensionEvent", "PlayerEvent.Clone", "ServerStoppedEvent"):
            self.assertIn(event, editor)
        self.assertIn("getLevel().isClientSide()", editor)
        self.assertIn("PatrolRoutePolicy.mayEdit(p.isCreative()", editor)
        self.assertIn("d.revision!=PatrolRoutes.revision(h)", editor)

    def test_left_click_air_and_blocks_never_depend_on_damage(self):
        self.assertIn("e.isAttack()", source("client/ui/PatrolEditorClient.java"))
        self.assertIn("PatrolToolNetwork.clearDraft()", source("client/ui/PatrolEditorClient.java"))
        server = source("soldier/PatrolRouteEditor.java")
        self.assertIn("AttackEntityEvent", server)
        self.assertIn("LeftClickBlock", server)
        self.assertIn("e.setCanceled(true)", server)
        self.assertNotIn("h.hurt(", server)
        self.assertIn("onItemUseFirst", source("soldier/PatrolCommandBatonItem.java"))

    def test_network_never_accepts_client_route_positions(self):
        network = source("soldier/PatrolToolNetwork.java")
        request = network.split("private record Request(", 1)[1].split("    static void encode(Snapshot", 1)[0]
        self.assertNotIn("readBlockPos", request)
        self.assertNotIn("List<Row>", request)
        for guard in ("s.token().equals(request.token)", "s.human().equals(request.human)", "s.entityId()!=request.entityId", "CommandRateLimit.allow(p)"):
            self.assertIn(guard, request)
        self.assertIn("count>256", network)
        self.assertIn("b.readableBytes()!=0", network)
        self.assertIn("expectedRevision", source("soldier/PatrolRoutes.java"))

    def test_dynamic_anchor_cannot_translate_the_route_or_expand_combat(self):
        route = source("soldier/NaturalRoute.java")
        store = source("soldier/PatrolRoutes.java")
        self.assertIn("routeOrigin.add(", route)
        self.assertIn("author.getCompound(PatrolRoutes.ORIGIN)", route)
        self.assertIn("PatrolRoutes.reached(h,c,point.position())", route)
        self.assertIn("MovementContinuity.ownsRoute(h,a.path)", route)
        self.assertIn("h.getTarget()!=null", route)
        self.assertIn("HumanMonitorControl.controls(h)", route)
        self.assertIn("HumanInventorySession.isEditing(h)", route)
        self.assertIn("BasicNavigationBudget.tryAcquire(h,1)", route)
        self.assertIn("state.putLong(\"retry_at\",now+OrderDistances.defaults().routeFailureBackoffTicks())", route)
        self.assertIn("progressPosition.distanceToSqr", route)
        self.assertIn("PatrolRoutes.playerCenter(human,anchor)", source("soldier/OrderDistances.java"))
        self.assertIn("BlockPos.containing(exact).equals(BlockPos.containing(fallback))", store)

    def test_wait_ui_and_creative_control_use_server_entrypoints(self):
        self.assertIn("PatrolToolNetwork.requestOpen", source("client/ui/SoldierCommandScreen.java"))
        self.assertIn("PatrolToolNetwork.setWait", source("client/ui/PatrolWaitScreen.java"))
        controller = source("soldier/CreativeSoldierControllerItem.java")
        self.assertIn("PatrolToolNetwork.openController(server,h)",controller)
        self.assertIn("PatrolControlService.mayControl(server,h)",controller)
        service=source("soldier/PatrolControlService.java")
        for guard in ("p.isCreative()", "HumanRelations.effectiveOwner(h)==null", "NaturalState.suspendForController", "NaturalState.restoreForController"):
            self.assertIn(guard,service)
        self.assertIn("suspendForController", source("monitor/HumanMonitorService.java"))
        self.assertIn("keyHotbarSlots", source("monitor/client/HumanMonitorScreen.java"))
        for language in ("zh_cn", "en_us"):
            data=json.loads((ROOT / "src/main/resources/assets/hostile_humans_unified/lang" / (language+".json")).read_text(encoding="utf-8"))
            self.assertEqual(data["human_monitor.keyline.modes.hired"].count("%s"),5)
            self.assertEqual(data["human_monitor.keyline.modes.wild"].count("%s"),4)

if __name__ == "__main__":
    unittest.main()
