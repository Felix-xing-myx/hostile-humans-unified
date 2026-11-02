"""Manual movement authority and input/menu boundaries; complements protocol/native-model tests."""
from source_paths import SourcePath as Path
import json, unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/"src/main/java/dev/felix/hostilehumans"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")
class MonitorTakeoverContract(unittest.TestCase):
 def test_manual_travel_precedes_autonomous_admission(self):
  human=(ROOT/"src/main/java/dev/felix/hostilehumans/entity/Human.java").read_text(encoding="utf-8")
  travel=human.split("public void travel(Vec3 input) {",1)[1].split("private void travelWithMovementInput",1)[0]
  self.assertLess(travel.index("HumanMonitorControl.controls(this)"),travel.index("HumanGoalExecutionBridge.bound"))
  self.assertIn("super.travel(input);",travel)
  self.assertIn("HumanMonitorControl.beforeTravel(this)",human)
  self.assertIn("HumanMonitorControl.finishTick(this)",human)
 def test_lease_never_changes_persisted_ai_or_orders(self):
  control=source("HumanMonitorControl")
  for forbidden in ("setNoAi(","setOwner", "setGameMode", "teleport", "CommandView.apply", "NaturalState.set"):
   self.assertNotIn(forbidden,control)
  for required in ("lease.token.equals(input.token())", "lease.player != player", "HumanMonitorService.target(lease.player) == human", "lease.inputs.offer(input", "lease.inputs.fresh(", "LEASES.remove(human)"):
   self.assertIn(required,control)
 def test_only_mob_ai_is_suppressed(self):
  mixin=(PKG/"mixin/HumanMonitorAiStepMixin.java").read_text(encoding="utf-8")
  self.assertIn('method = "m_6140_()V"',mixin)
  self.assertIn("instanceof Human human",mixin)
  self.assertIn("HumanMonitorControl.controls(human)",mixin)
  self.assertIn("HumanMonitorControl.controls(human)",source("HumanGoalExecutionBridge"))
  self.assertIn("HumanMonitorControl.controls(h)",source("HumanTargetCoordinator"))
 def test_hud_never_occupies_screen_or_swallows_chat_and_pause(self):
  hud=source("HumanMonitorScreen");client=source("HumanMonitorClient")
  self.assertNotIn("extends Screen",hud)
  self.assertIn("RenderGuiEvent.Post",hud)
  self.assertNotIn("new HumanMonitorScreen",client)
  self.assertNotIn("keyChat.consumeClick",client)
  self.assertIn("GLFW_KEY_BACKSPACE",client)
  self.assertIn("client.isPaused()",client)
  self.assertIn("client.screen == null && client.isWindowActive()",client)
 def test_control_lifecycle_releases_session_and_inventory_conflicts(self):
  service=source("HumanMonitorService")
  for required in ("HumanMonitorControl.release(player, session.token)","PlayerLoggedOutEvent", "EntityLeaveLevelEvent", "ServerStoppingEvent", "human == null || !dev.felix.hostilehumans.monitor.remote.MonitorRemoteView.tick(player, human)) close(player)"):
   self.assertIn(required,service)
  self.assertIn("HumanMonitorControl.controls(human)",source("HumanInventorySession"))
  self.assertIn("HumanMonitorControl.controls(human)",source("NavigationDebugService"))
 def test_partial_armor_and_vanilla_egg_are_independent(self):
  layer=source("FactionVisualLayer")
  self.assertIn("armored(entity, EquipmentSlot.CHEST)",layer)
  self.assertIn("armored(entity, EquipmentSlot.LEGS)",layer)
  self.assertIn("armor.getEquipmentSlot() == slot",layer)
  model=json.loads((ROOT/"src/main/resources/assets/hostile_humans_unified/models/item/faction_azure_tier3_boss_spawn_egg.json").read_text())
  self.assertEqual(model["textures"]["layer0"],"minecraft:item/spawn_egg")
  self.assertEqual(model["textures"]["layer1"],"minecraft:item/spawn_egg_overlay")
if __name__=="__main__":unittest.main()
