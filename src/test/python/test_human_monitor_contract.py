"""Read-only security and integration contracts; do not substitute for Java or gameplay acceptance."""
from source_paths import SourcePath as Path
import json
import unittest

MAIN = Path(__file__).resolve().parents[2] / "main"
PKG = MAIN / "java/dev/felix/hostilehumans"
RENDER = MAIN / "java/dev/felix/hostilehumans/client/render"

def source(name):
    return (PKG / (name + ".java")).read_text(encoding="utf-8")

class HumanMonitorContract(unittest.TestCase):
    def test_viewing_does_not_change_ownership_mode_or_position(self):
        service = source("HumanMonitorService")
        opening = service.split("public static void interact", 1)[1].split("static void request", 1)[0]
        for forbidden in ("setOwner", "setGameMode", "teleport", "setTarget", "setNoAi", "NaturalState.set", "CommandView.apply"):
            self.assertNotIn(forbidden, opening)
        for check in ("MonitorAccessPolicy.allows(player, human)", "player.isSpectator()", "session.target.equals(human.getUUID())", "getEntity(session.target)", "session.dimension", "session.deadline"):
            self.assertIn(check, service)
        self.assertIn("!session.token.equals(token)", service)
        policy = source("MonitorAccessPolicy")
        self.assertIn("player.isCreative() || HumanRelations.isOwnedBy(human, player)", policy)
        self.assertNotIn("128 * 128", service)

    def test_item_and_display_resource_are_registered(self):
        self.assertIn('ITEMS.register("human_monitor"', source("ContentRegistry"))
        self.assertIn("HumanMonitorNetwork.register()", source("HumanRuntime"))
        model = json.loads((MAIN / "resources/assets/hostile_humans_unified/models/item/human_monitor.json").read_text())
        self.assertEqual(model["parent"], "minecraft:item/generated")
        for locale in ("zh_cn", "en_us"):
            lang = json.loads((MAIN / f"resources/assets/hostile_humans_unified/lang/{locale}.json").read_text(encoding="utf-8"))
            self.assertIn("item.hostile_humans_unified.human_monitor", lang)
            self.assertIn("human_monitor.exit", lang)

    def test_shared_presentation_requires_actual_attached_camera(self):
        shared = (RENDER / "HumanCameraView.java").read_text(encoding="utf-8")
        self.assertIn("client.getCameraEntity() == human", shared)
        self.assertIn("client.player.isSpectator() || HumanMonitorClient.viewing(human)", shared)
        for name in ("HumanSpectatorView", "HumanSpectatorCamera", "SpectatorAudioListener"):
            self.assertIn("HumanCameraView.attached(", (RENDER / (name + ".java")).read_text(encoding="utf-8"))

    def test_real_inventory_keeps_original_permissions_and_edit_lifecycle(self):
        menu = source("HumanInventoryMenu")
        self.assertIn("HumanMonitorService.canAccess(player, human)", menu)
        self.assertIn("HumanRelations.isOwnedBy(human, player)", menu)
        self.assertIn("HumanInventorySession.begin", menu)
        self.assertIn("HumanInventorySession.end", menu)
        session = source("HumanInventorySession")
        self.assertIn("human.setNoAi(state.previousNoAi())", session)
        self.assertIn("restoreAfterLoad", session)
        self.assertIn("HumanInventorySession.restoreAfterLoad(human)", source("HumanRuntime"))

    def test_monitor_is_a_temporary_debug_subscription(self):
        service = source("NavigationDebugService")
        monitor = service.split("if (HumanMonitorService.active(player)) {", 2)[2].split("if (!allowed(player)", 1)[0]
        self.assertIn("debugTarget(player)", monitor)
        for forbidden in ("SELECTIONS.put", "selection.add", "RUNS.put", "NAVIGATION_OBSERVER_HELMET", "createPath"):
            self.assertNotIn(forbidden, monitor)
        self.assertIn("HumanMonitorClient.debugging()", source("NavigationDebugClient"))

    def test_mouse_screen_inventory_key_and_exit_restore(self):
        screen = source("HumanMonitorScreen")
        self.assertIn("keyInventory.consumeClick", source("HumanMonitorClient"))
        self.assertNotIn("renderBackground(", screen)
        self.assertNotIn("passEvents = true", screen)
        client = source("HumanMonitorClient")
        for invariant in ("previousCamera", "previousType", "CLOSED.contains(snapshot.token())", "NavigationLegendScreen", "5_000_000_000L"):
            self.assertIn(invariant, client)
        self.assertNotIn("setGameMode", client)

class MonitorCleanupContract(unittest.TestCase):
    def test_exit_clears_transient_frame_on_both_sides(self):
        client = source("HumanMonitorClient").split("static void close(boolean notifyServer)", 1)[1]
        self.assertIn("NavigationDebugClient.accept(NavigationDebugFrame.EMPTY)", client)
        server = source("HumanMonitorService").split("static void close(ServerPlayer player)", 1)[1]
        self.assertIn("NavigationDebugNetwork.send(player, NavigationDebugFrame.EMPTY)", server)

    def test_monitor_hides_hud_without_disabling_world_geometry(self):
        client = source("NavigationDebugClient")
        hud = client.split("void hud(RenderGuiEvent.Post event)", 1)[1].split("private static int compactDetail", 1)[0]
        self.assertIn("if (HumanMonitorClient.active()) return", hud)
        world = client.split("void world(RenderLevelStageEvent event)", 1)[1].split("private static void perception(", 1)[0]
        self.assertNotIn("if (HumanMonitorClient.active()) return", world)
        label = client.split("private static void label(", 1)[1].split("private static void box(", 1)[0]
        self.assertIn("if (HumanMonitorClient.active()) return", label)

    def test_leaving_releases_live_slot_before_persisting(self):
        service = source("NavigationDebugService")
        leaving = service.split("void knownDeath(EntityLeaveLevelEvent event)", 1)[1].split("void playerTick", 1)[0]
        self.assertIn("affected = active", leaving)
        self.assertLess(leaving.index("affected.selection.remove"), leaving.index("save(player, affected)"))
        self.assertIn("!server.isRunning()", leaving)
        self.assertNotIn("RemovalReason.KILLED", leaving)

if __name__ == "__main__":
    unittest.main()
