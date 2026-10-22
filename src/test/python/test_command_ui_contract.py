"""Static wire, access, server ownership and resource guards for the command UI."""
import json
import re
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
JAVA=ROOT/'src/main/java/club/someoneice/humangunner'
def source(name):return (JAVA/(name+'.java')).read_text(encoding='utf-8-sig')
class CommandUiContract(unittest.TestCase):
 def test_protocols_and_three_mode_adapter(self):
  self.assertIn('PROTOCOL="9"',source('HumanCommandNetwork'));self.assertIn('PROTOCOL = "7"',source('SoldierRosterNetwork'))
  self.assertIn('List.of(SoldierOrder.FOLLOW,SoldierOrder.BODYGUARD,SoldierOrder.GARRISON)',source('CommandView'))
  for ui in ('SoldierCommandScreen','CommandPanelScreen','SoldierRosterScreen'):self.assertNotIn('SoldierOrder.values()',source(ui))
 def test_single_requests_bind_all_identity_before_any_write(self):
  code=source('HumanCommandNetwork').split('private record Request',1)[1].split('private record Close',1)[0]
  for guard in ('s.token().equals(p.token)','s.entityId()!=p.entityId','s.human().equals(p.human)','humanForSession(player,s)'):self.assertLess(code.index(guard),code.index('CommandView.apply'))
  self.assertIn('invalidate(player,p.token)',code)
 def test_client_only_submits_and_displays_accepted_state(self):
  panel=source('CommandPanelScreen');click=panel.split('public boolean mouseClicked',1)[1]
  self.assertNotIn('state=',click);self.assertNotIn('state.order()=',click)
  release=panel.split('public boolean mouseReleased',1)[1].split('public boolean mouseScrolled',1)[0]
  self.assertIn('if(mixed(field)||draftDistance!=previous)submit',release)
  self.assertIn('field==CommandView.Field.RADIUS?CommandView.radius(draftDistance):CommandView.follow(draftDistance)',release)
  self.assertIn('CommandView.stay(mixed(CommandView.Field.STAY)||!state.garrison().stay())',panel)
  self.assertIn('CommandView.chase(mixed(CommandView.Field.CHASE)||!state.garrison().chase())',panel)
  for forbidden in ('getPersistentData','UnifiedConfig','HumanGrowth'):self.assertNotIn(forbidden,panel)
  self.assertIn('s.state=view',source('SoldierCommandScreen'))
 def test_batch_rechecks_actual_held_type_binding_and_session(self):
  code=source('SquadCommandNetwork');valid=code.split('private static boolean valid',1)[1].split('private static CommandView',1)[0]
  self.assertIn('holdsToken(p,number)',valid);self.assertIn('SquadCommands.valid(p,token,number)',valid)
  self.assertIn('stack.getItem() instanceof SquadCommandTokenItem&&SquadCommands.boundSquad(p,stack)==number',code)
  request=code.split('private record Request',1)[1]
  self.assertLess(request.index('!valid(player,p.token,p.number)'),request.index('SquadCommands.apply'))
  for counter in ('applied()','unloaded()','unauthorized()','invalid()'):self.assertIn(counter,request)
  self.assertIn('if(!view.result().getString().isEmpty())',source('SquadCommandScreen'))
 def test_roster_actions_use_the_existing_session_and_data_authority(self):
  code=source('SoldierRosterNetwork');batch=code.split('private record SquadRequest',1)[1]
  self.assertLess(batch.index('session.token.equals(p.token)'),batch.index('data.assign'))
  for boundary in ('data.assign','data.rename','instanceof SquadCommandTokenItem','SquadCommands.bind'):self.assertIn(boundary,batch)
  self.assertIn('size>64',batch);self.assertIn('java.util.LinkedHashSet',batch)
  for field in ('int squadFilter','int squad','int entityId'):self.assertIn(field,code)
 def test_bounded_packet_values_and_no_growth_client_authority(self):
  code=source('CommandView')
  for guard in ('mask==0||(mask&~511)!=0','value<8||value>96','ORDERS.contains(update.order())','(mask&~255)!=0'):self.assertIn(guard,code)
  self.assertIn('(mask&256)!=0?b.readEnum(CombatPreference.class):null',code)
  self.assertIn('patchedSettings(GarrisonSettings.player(human),update)',code)
  self.assertNotIn('Growth',code.split('public static SquadCommands.Update readUpdate',1)[1])
 def test_natural_tool_calls_permission_checked_existing_entry(self):
  self.assertIn('NaturalStateCommand.useDebugTool(serverPlayer,human,player.isShiftKeyDown())',source('NaturalStateToolItem'))
  self.assertIn('if(!player.hasPermissions(2))return false',source('NaturalStateCommand'))
 def test_snapshot_whitelist_preserves_new_distances(self):
  code=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/data/HumanData.java').read_text(encoding='utf-8')
  method=code.split('public CompoundTag getPersistedCommandData',1)[1].split('public UUID getOwnerUUID',1)[0]
  for key in ('order_radii','order_garrison','growth','faction'):self.assertIn('"'+key+'"',method)
  self.assertNotIn('put("ForgeData"',method)
 def test_reanchor_is_explicit_and_context_reason_is_server_owned(self):
  view=source('CommandView');self.assertIn('OrderDistances.context(human).reason()',view)
  for ui in ('SoldierCommandScreen','SquadCommandScreen'):self.assertIn('protected void reanchor()',source(ui))
  single=source('HumanCommandNetwork');self.assertIn('if(p.action==2){SoldierOrder.set(h,SoldierOrder.GARRISON)',single)
  squad=source('SquadCommandNetwork');self.assertIn('SquadCommands.reanchor(player,p.token,p.number)',squad)
  self.assertIn('hasContextWarning()?contextReason()',source('SoldierCommandScreen'))
  whitelist=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/data/HumanData.java').read_text(encoding='utf-8')
  self.assertIn('"order_anchor_dimension"',whitelist);self.assertNotIn('"specialty"',whitelist)
 def test_translations_and_item_models_exist_without_recipes(self):
  assets=ROOT/'src/main/resources/assets/hostile_humans_unified';zh=json.loads((assets/'lang/zh_cn.json').read_text(encoding='utf-8'));en=json.loads((assets/'lang/en_us.json').read_text(encoding='utf-8'))
  keys=[key for key in zh if key.startswith('screen.hostile_humans_unified.command.') or key.startswith('message.hostile_humans_unified.squad.')]
  self.assertGreater(len(keys),40)
  for key in keys:self.assertIn(key,en);self.assertEqual(len(re.findall(r'%s',zh[key])),len(re.findall(r'%s',en[key])))
  for item in ('squad_command_token','natural_state_tool'):
   self.assertIn('item.hostile_humans_unified.'+item,zh);json.loads((assets/'models/item'/(item+'.json')).read_text())
   self.assertFalse(list((ROOT/'src/main/resources/data').rglob(item+'.json')))
if __name__=='__main__':unittest.main()
