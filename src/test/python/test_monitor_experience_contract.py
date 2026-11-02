"""Control, editing and remote-view cross-module safety; complements executable wire and native bytecode tests."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
def code(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
class MonitorExperienceContract(unittest.TestCase):
 def test_inventory_pause_is_not_release(self):
  control=code('monitor/HumanMonitorControl')
  pause=control.split('static boolean pauseForInventory',1)[1].split('public static void inventoryClosed',1)[0]
  self.assertIn('lease.inventoryPaused = true',pause)
  self.assertNotIn('LEASES.remove',pause)
  self.assertIn('resetInput(human, lease)',pause)
  close=control.split('public static void inventoryClosed',1)[1].split('private static void resetInput',1)[0]
  self.assertIn('HumanMonitorService.refresh(player)',close)
  editing=code('inventory/HumanInventorySession')
  self.assertIn('HumanMonitorControl.pausedForInventory(human, editor)',editing)
  self.assertIn('!HumanMonitorControl.controls(human)) HumanManagedLoadout.reconcile',editing)
 def test_restricted_inventory_has_no_cursor_or_cross_player_path(self):
  menu=code('inventory/HumanInventoryMenu')
  click=menu.split('public void clicked(',1)[1].split('private void addHumanSlot',1)[0]
  self.assertIn('if (!internalOnly) { super.clicked',click)
  self.assertIn('type != net.minecraft.world.inventory.ClickType.PICKUP',click)
  self.assertIn('index >= HUMAN_SLOT_COUNT',click)
  self.assertIn('!getCarried().isEmpty()',click)
  self.assertNotIn('setCarried',click)
  self.assertIn('if (internalOnly) return ItemStack.EMPTY',menu)
  self.assertIn('mayPickup(Player player) { return !internalOnly',menu)
 def test_server_owns_selection_and_real_exchange(self):
  control=code('monitor/HumanMonitorControl')
  self.assertIn('lease.generation != generation',control)
  self.assertIn('HumanInventorySession.isEditing(human)',control)
  loadout=code('monitor/combat/MonitorLoadout')
  self.assertIn('expectedRevision != revision',loadout)
  self.assertIn('HumanEquipmentExchange.locate',loadout)
  self.assertIn('HumanEquipmentExchange.request',loadout)
  for forbidden in ['setItemSlot(','.shrink(','.hurt(']:self.assertNotIn(forbidden,loadout)
  exchange=code('inventory/HumanEquipmentExchange')
  self.assertIn('Need empty(EquipmentSlot destination)',exchange)
  self.assertIn('if (need.empty()) { sources.add(-1)',exchange)
 def test_manual_layout_has_no_autonomous_offhand_restore(self):
  dual=code('behavior/combat/melee/NpcDualWield')
  controlled=dual.split('private static boolean prepareControlledPair',1)[1].split('public static boolean layoutReady',1)[0]
  self.assertNotIn('HumanEquipmentExchange.request',controlled)
  self.assertNotIn('getInventoryItem',controlled)
  shield=code('behavior/combat/defense/ShieldDefenseController')
  self.assertIn('requestedOwner != Owner.MANUAL && !RecoverySupplies.restoreShieldAfterRecovery',shield)
  self.assertIn('ControlledEquipmentPolicy.defenseAllowed',shield)
 def test_binding_and_roster_use_current_authorization(self):
  service=code('monitor/HumanMonitorService')
  bind=service.split('public static void interact',1)[1].split('public static void open(',1)[0]
  self.assertIn('MonitorTargetBinding.bind',bind)
  self.assertNotIn('SESSIONS.put',bind)
  self.assertIn('isOwnedRecord(target, player.getUUID())',service)
  self.assertIn('MonitorRemoteView.bootstrap',service)
  self.assertIn('MonitorAccessPolicy.allows(player, human)',service)
 def test_remote_views_are_renewed_and_closed(self):
  service=code('monitor/HumanMonitorService')
  self.assertIn('MonitorRemoteView.start(player, human)',service)
  self.assertIn('MonitorRemoteView.tick(player, human)',service)
  self.assertIn('MonitorRemoteView.stop(player)',service)
  self.assertNotIn('128 * 128',service)
  self.assertIn('getEntity(session.target)',service)
 def test_food_bar_is_server_owned_and_not_medical_pool(self):
  recovery=code('behavior/combat/recovery/RecoverySupplies')
  getter=recovery.split('public static float pendingFoodHealing',1)[1].split('public static',1)[0]
  self.assertIn('PENDING_HEAL',getter)
  self.assertNotIn('ScgunsMedical',getter)
  self.assertIn('RecoverySupplies.pendingFoodHealing(human)',code('monitor/HumanMonitorService'))
  self.assertIn('state.pendingFood()',code('monitor/client/HumanMonitorScreen'))
if __name__=='__main__':unittest.main()
