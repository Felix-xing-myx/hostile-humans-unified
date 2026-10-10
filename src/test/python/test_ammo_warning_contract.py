"""Static ammo-report integration checks; does not simulate gameplay."""
import json
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"
WARNING = (PKG / "HiredHumanAmmoWarning.java").read_text(encoding="utf-8")
DIALOGUE = (PKG / "SoldierDialogue.java").read_text(encoding="utf-8")
DISPATCH = (PKG / "SoldierMessageDispatcher.java").read_text(encoding="utf-8")

class AmmoWarningContract(unittest.TestCase):
    def test_formal_server_owner_and_finite_ammo_only(self):
        for condition in ("human.level().isClientSide", "!human.hasOwner()", "!human.isAlive()",
                          "!HumanRelations.isOwnedBy(human, owner)", "!UnifiedConfig.get().ammoConsumptionEnabled()",
                          "HumanInventorySession.isEditing(human)"):
            self.assertIn(condition, WARNING)
        self.assertIn("HiredHumanAmmoWarning.tick(human, owner)", DIALOGUE)
        self.assertIn("% 100 != 0", DIALOGUE)

    def test_shortage_accounts_for_loaded_and_stowed_weapons(self):
        for condition in ("slot = -2", "data.getInventoryItemsSize()", "data.getInventoryItem(slot)",
                          "guns.hasLoadedAmmo(stack) || magazines >= cost", "guns.isReloading(human)",
                          "HumanAmmo.magazineCost(stack)", "bow && arrows == 0", "gun && !usableGun"):
            self.assertIn(condition, WARNING)

    def test_warning_latches_only_after_success_and_rearms_on_supplies(self):
        send = WARNING.index("if (!SoldierMessageDispatcher.tryStatus")
        latch = WARNING.index("putInt(WARNED, exhausted)")
        self.assertLess(send, latch)
        self.assertIn("if (!restocked) return false", WARNING)
        self.assertIn("arrows > 0", WARNING)
        self.assertIn("magazines >= Math.max(1", WARNING)
        self.assertIn("getInt(WARNED)", WARNING)
        self.assertEqual(DIALOGUE.count("HiredHumanAmmoWarning.reset(human)"), 2)

    def test_ammo_reports_share_budget_and_do_not_batch_or_queue_duplicates(self):
        status = DISPATCH.split("static boolean tryStatus", 1)[1].split("static void report", 1)[0]
        for condition in ("!inbox.pending.isEmpty()", "inbox.reports.allow", "settings.reportIntervalTicks()",
                          "settings.reportMaxPerMinute()", "SoldierCommandFeedback.say", "return true"):
            self.assertIn(condition, status)
        self.assertNotIn("pending.put", status)
        self.assertNotIn("inbox.chatter.allow", status)
        for lang in ("en_us", "zh_cn"):
            labels = json.loads((ROOT / "src/main/resources/assets/hostile_humans_unified/lang" / (lang + ".json")).read_text(encoding="utf-8"))
            for kind in ("arrows", "magazines", "both"):
                self.assertIn("dialogue.hostile_humans_unified.ammo_empty." + kind, labels)

if __name__ == "__main__": unittest.main()
