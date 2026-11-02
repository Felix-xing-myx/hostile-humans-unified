"""Server-owned growth display and current command subscription contracts; static checks only."""
import json
import re
import unittest
from source_paths import SourcePath as Path
ROOT=Path(__file__).resolve().parents[3]
JAVA=ROOT/'src/main/java/dev/felix/hostilehumans'
def source(name):return (JAVA/(name+'.java')).read_text(encoding='utf-8-sig')
NETWORK=source('HumanCommandNetwork');SCREEN=source('SoldierCommandScreen');PANEL=source('CommandPanelScreen');SNAPSHOT=source('GrowthSnapshot')
def compact(code):
 return re.sub(r"\s+", "", code)

def java_block(code, marker):
 """Read the real balanced Java body, ignoring braces in strings/comments."""
 pattern=r"\s*".join(re.escape(token) for token in re.findall(r"\w+|[^\w\s]",marker))
 match=re.search(pattern,code)
 if match is None:raise AssertionError("Missing Java contract marker: "+marker)
 start=code.index("{",match.end());depth=0
 tokens=re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/|[{}]',code[start:],re.S)
 for token in tokens:
  value=token.group()
  if value=="{":depth+=1
  elif value=="}":
   depth-=1
   if depth==0:return code[start:start+token.end()]
 raise AssertionError("Unclosed Java block: "+marker)

class GrowthUiContractTest(unittest.TestCase):
 def test_snapshot_wire_order_and_client_safe_dependencies(self):
  expected=['Boolean']+['VarInt']*5+['Double']*4
  self.assertEqual(re.findall(r'buffer\.write(\w+)\(',SNAPSHOT),expected)
  self.assertEqual(re.findall(r'buffer\.read(\w+)\(',SNAPSHOT),expected)
  for forbidden in ('UnifiedConfig','HumanGrowth','net.minecraft.server','net.minecraft.client'):self.assertNotIn(forbidden,SNAPSHOT)
 def test_growth_packets_are_server_to_client_only(self):
  registration=NETWORK.split('public static void register()',1)[1].split('static void openFor',1)[0]
  self.assertIn('Snapshot.class,Snapshot::encode,Snapshot::decode,Snapshot::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)',registration)
  self.assertNotRegex(NETWORK,r'sendToServer\(new \w*Growth')
  self.assertIn('p.growth.encode(b)',NETWORK);self.assertIn('GrowthSnapshot.decode(b)',NETWORK)
 def test_only_server_produces_snapshots(self):
  self.assertIn('HumanGrowth.snapshot(h)',NETWORK)
  for code in (SCREEN,PANEL):
   for forbidden in ('UnifiedConfig','HumanGrowth','getPersistentData'):self.assertNotIn(forbidden,code)
 def test_subscription_revalidates_identity_and_access(self):
  for guard in ('!p.isAlive()','dimension().equals(s.dimension())','getEntity(s.entityId())','s.human().equals(h.getUUID())','owned(p,h)','p.distanceToSqr(h)<=4096'):self.assertIn(guard,NETWORK)
  for guard in ('human.isAlive()','human.hasOwner()','player.getUUID().equals(human.getOwnerUUID())'):self.assertIn(guard,NETWORK)
 def test_tick_is_bounded_and_subscribed(self):
  self.assertIn('server.getTickCount()%20!=0',NETWORK)
  self.assertIn('server.getPlayerList().getPlayer(p.getUUID())!=p',NETWORK)
  self.assertIn('iterator.remove()',NETWORK);self.assertIn('send(p,fresh,h,false)',NETWORK)
 def test_old_updates_and_closes_cannot_change_new_panel(self):
  for identity in ('s.sessionToken.equals(token)','s.humanId.equals(human)','s.entityId==entityId'):self.assertIn(identity,SCREEN)
  self.assertIn('s.token().equals(p.token)',NETWORK)
  self.assertIn('HumanCommandNetwork.closeScreen(sessionToken)',SCREEN)
 def test_disabled_growth_hides_block_and_layout_space(self):
  build=java_block(PANEL,'protected UiLayout.Node buildContent(')
  block=java_block(build,'if(growth!=null&&growth.enabled())')
  self.assertIn('new PanelMeter(',block)
  self.assertEqual(block.count('new PanelMeter('),2)
  self.assertEqual(build.count('new PanelMeter('),2)
  self.assertTrue(compact(build[build.index(block)+len(block):]).startswith('nodes.add(UiLayout.row('))
  # Disabled/null growth contributes no leaf, placeholder or fixed spacer to the tree.
  outside=build.replace(block,'',1)
  for value in ('growth.level()', 'growth.stageProgress()', 'growth.experiencePercent()', 'new PanelMeter('):self.assertNotIn(value,outside)
  self.assertIn('returnUiLayout.column(9,nodes)',compact(build))
  layout=compact(java_block(source('UiLayout'),'public static Node column('))
  self.assertIn('for(Noden:children)h+=n.measure(w)',layout)
  self.assertIn('Math.max(0,children.size()-1)*gap',layout)
  self.assertIn('n.place(newUiRect(b.x(),y,b.width(),h))',layout)
 def test_experience_meters_are_read_only(self):
  block=java_block(PANEL,'if(growth!=null&&growth.enabled())')
  for forbidden in ('dragging','submit(','sendToServer','setProgress'):self.assertNotIn(forbidden,block)
  for key in ('stageProgress()','stageRequired()','experienceProgress()','experienceRequired()'):self.assertIn(key,block)
  self.assertIn('growth.experiencePercent()-growth.experienceMinPercent()',compact(block))
  self.assertIn('growth.experienceCapPercent()-growth.experienceMinPercent()',compact(block))
  meter=source('PanelMeter')
  self.assertIn('extends AbstractWidget',meter);self.assertIn('active=false',compact(meter))
  for forbidden in ('OnPress','submit(','sendToServer','mouseClicked','mouseDragged','mouseReleased'):self.assertNotIn(forbidden,meter)
  paint=compact(java_block(meter,'protected void renderWidget('))
  self.assertIn('UiDraw.meter(',paint);self.assertIn('value.getAsDouble(),maximum.getAsDouble()',paint)
 def test_every_edit_including_dismiss_requires_session(self):
  request=NETWORK.split('private record Request',1)[1].split('private record Close',1)[0]
  for guard in ('s.token().equals(p.token)','s.entityId()!=p.entityId','s.human().equals(p.human)','CommandRateLimit.allow(player)','humanForSession(player,s)'):self.assertIn(guard,request)
  self.assertLess(request.index('s.token().equals(p.token)'),request.index('HumanRelations.dismiss'))
 def test_growth_translations_are_parameter_compatible(self):
  lang=ROOT/'src/main/resources/assets/hostile_humans_unified/lang'
  zh=json.loads((lang/'zh_cn.json').read_text(encoding='utf-8'));en=json.loads((lang/'en_us.json').read_text(encoding='utf-8'))
  keys=[key for key in zh if key.startswith('screen.hostile_humans_unified.soldier.growth.')]
  self.assertGreaterEqual(len(keys),6)
  for key in keys:self.assertIn(key,en);self.assertEqual(re.findall(r'%[sd]',zh[key]),re.findall(r'%[sd]',en[key]))
if __name__=='__main__':unittest.main()
