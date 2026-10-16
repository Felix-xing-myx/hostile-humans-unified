"""Acoustic failure filtering and optional native event wiring; not live game acceptance."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]/"src/main/java/club/someoneice/humangunner"
def source(name):return (ROOT/(name+".java")).read_text(encoding="utf-8")
class SoundInvestigationContract(unittest.TestCase):
 def test_filter_before_alert_and_hearing_ray(self):
  p=source("HumanPerception");self.assertLess(p.index("SoundInvestigationMemory.blocked(h,kind,position)"),p.index('alert(h,s,"有效线索："'))
  a=source("HumanAwareness");self.assertLess(a.index("SoundInvestigationMemory.blocked(h,n.kind,n.position)"),a.index("HearingRelevance.evaluate("))
  self.assertIn("!SoundInvestigationMemory.blocked(h,a.kind,a.position)",p)
 def test_pending_and_restriction_are_not_failure(self):
  s=source("PerceptionInvestigationGoal");self.assertIn('query.pending()',s);self.assertIn('Status.RESTRICTED',s)
  self.assertNotIn('SoundInvestigationMemory.failed',s[s.index('if(query.pending())'):s.index('Path candidate=query.path()')])
  self.assertIn('if(!floorRestricted)SoundInvestigationMemory.failed',s)
 def test_three_native_server_adapters(self):
  self.assertIn('LogicalSide.SERVER',source('TaczIntegration'));self.assertIn('!event.isCanceled()',source('TaczIntegration'))
  for name,namespace in [('ScgunsIntegration','top.ribs.scguns'),('JegIntegration','ttv.migami.jeg')]:
   s=source(name);self.assertIn(namespace+'.event.GunFireEvent.Post',s);self.assertIn('if(!event.isClient())HumanAwareness.gunshot',s)
   self.assertIn('HumanAwareness.gunshot(human)',s)
 def test_bounded_local_memory_and_debug(self):
  s=source('SoundInvestigationMemory');self.assertIn('WeakHashMap',s);self.assertIn('SoldierOrder.get(h)',s)
  self.assertIn('声音排除区',source('HumanPerception'))
if __name__=='__main__':unittest.main()
