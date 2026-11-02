"""Control/knowledge and movement/strike boundaries, without starting a game or compiler."""
from source_paths import SourcePath as Path
import unittest
ROOT=Path(__file__).resolve().parents[3]/"src/main/java"
PKG=ROOT/"dev/felix/hostilehumans"
RENDER=ROOT/"dev/felix/hostilehumans/client/render"
def read(path):return path.read_text(encoding="utf-8")
class MonitorShieldCoordination(unittest.TestCase):
 def test_accumulated_angles_are_canonicalized_before_network_validation(self):
  network=read(PKG/"HumanMonitorNetwork.java")
  angles=network.split("record Control(",1)[1]
  constructor=angles.split("static void encode",1)[0]
  self.assertIn("Mth.wrapDegrees(yaw)",constructor)
  self.assertIn("Mth.clamp(pitch, -90, 90)",constructor)
  self.assertNotIn("Math.abs(input.yaw) > 180",angles)
  self.assertIn("!Float.isFinite(input.yaw)",angles)
  client=read(PKG/"HumanMonitorClient.java")
  self.assertIn("Mth.wrapDegrees(human.getYHeadRot())",client)
 def test_local_camera_is_not_driven_by_remote_pose_interpolation(self):
  client=read(PKG/"HumanMonitorClient.java")
  self.assertIn("ViewportEvent.ComputeCameraAngles",client)
  self.assertIn("event.setYaw(controlYaw); event.setPitch(controlPitch);",client)
  self.assertNotIn("previewLook",client)
  self.assertNotIn("RenderTickEvent",client)
  self.assertNotIn("human.setYRot",client)
 def test_arm_pose_is_scoped_and_restores_all_interpolation_fields(self):
  renderer=read(RENDER/"HumanRenderer.java");presentation=read(RENDER/"HumanSpectatorPresentation.java")
  self.assertIn("HumanSpectatorPresentation.withControlView",renderer)
  pose=presentation.split("static void withControlView",1)[1].split("static boolean showOffhandItem",1)[0]
  self.assertIn("takingOver(human)",pose)
  self.assertIn("finally",pose)
  for field in ("human.yRotO", "human.xRotO", "human.yHeadRotO", "human.yBodyRotO"):
   self.assertIn(field,pose.split("finally",1)[1])
 def test_manual_sensing_keeps_normal_sampling_memory_and_budget(self):
  perception=read(PKG/"HumanPerception.java")
  gate=perception.split('h.isAlert=now<s.alertUntil',1)[1].split('if(s.focus!=null)',1)[0]
  self.assertNotIn("HumanMonitorControl.controls(h)",gate)
  for call in ('PerceptionBudget.claim(h,"rays"', 't.evidence.visible(', 'memory.dynamic_ticks', 'memory.familiar_ticks'):
   self.assertIn(call,perception)
  self.assertIn("if (!HumanMonitorControl.controls(h)) reactToClue",perception)
  self.assertIn("if (HumanMonitorControl.controls(h)) return;",perception.split("void finishLook",1)[1].split("private static void alert",1)[0])
  self.assertIn("HumanMonitorControl.controls(h)",read(PKG/"HumanTargetCoordinator.java"))
 def test_upper_melee_mode_uses_movement_not_strike_gate(self):
  human=read(ROOT/"dev/felix/hostilehumans/entity/Human.java")
  mode=human.split("private static boolean hhu$isMeleeMode",1)[1].split("private void bootstrap",1)[0]
  self.assertIn("hhu$canMeleeMove()",mode)
  self.assertNotIn("hhu$canMeleeAttack()",mode)
  movement=human.split("public boolean hhu$canMeleeMove()",1)[1].split("public boolean hhu$canMeleeAttack()",1)[0]
  self.assertIn("permitsMeleeMovement()",movement)
  self.assertIn("blocksMeleeAttack()",human.split("public boolean hhu$canMeleeAttack()",1)[1].split("/**",1)[0])
 def test_latest_input_is_drained_by_simulation_not_packet_handler(self):
  control=read(PKG/"HumanMonitorControl.java")
  handler=control.split("static void input(",1)[1].split("private static void consumeInput",1)[0]
  self.assertIn("lease.inputs.offer",handler)
  self.assertNotIn("lease.forward =",handler)
  self.assertNotIn("lease.accepted ==",control)
  self.assertIn("consumeInput(lease)",control.split("public static void beforeTravel",1)[1])
if __name__=="__main__":unittest.main()
