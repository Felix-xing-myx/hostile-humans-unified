import unittest
from source_paths import SourcePath as Path
ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/dev/felix/hostilehumans"
def source(name): return (JAVA / (name + ".java")).read_text(encoding="utf-8")
class RecallContract(unittest.TestCase):
    def test_death_admission_precedes_irreversible_bookkeeping(self):
        death = source("HumanRuntime").split("void onLivingDeath", 1)[1]
        self.assertLess(death.index("SoldierRecallProtection.tryProtect"), death.index("InventoryTotemProtection"))
        self.assertIn("event.setCanceled(true)", death)
    def test_pay_only_after_successful_native_relocation(self):
        body = source("SoldierRecallProtection").split("void relocate", 1)[1].split("void checkpoint", 1)[0]
        self.assertLess(body.index("if (!moved)"), body.index("remainder.shrink(1)"))
        self.assertIn("paymentSlot(survivor, request.payment)", body)
        self.assertIn("SoldierOrder.GARRISON", body)
        self.assertIn("!effect.getEffect().isBeneficial()", body)
    def test_failure_replays_death_without_a_second_damage_hit(self):
        body = source("SoldierRecallProtection").split("void fail", 1)[1].split("void relocate", 1)[0]
        self.assertIn("human.die(request.damage)", body)
        self.assertIn("finally { REPLAY.remove(human); }", body)
        self.assertNotIn(".hurt(", body)
    def test_chunk_tickets_and_pending_flags_have_cleanup(self):
        body = source("SoldierRecallProtection")
        for token in ("MAX_ACTIVE = 16", "NEW_TICKETS_PER_TICK = 4", "TOTAL_TIMEOUT = 200", "LOAD_TIMEOUT = 100", "removeRegionTicket", "restoreFlags(request, human)", "ServerStoppingEvent", "EntityLeaveLevelEvent"):
            self.assertIn(token, body)
        self.assertNotIn(".getChunk(", body)
        self.assertIn("if (!valid) payment = ItemStack.EMPTY", body)
    def test_backpack_retention_includes_fallback_eviction(self):
        loot = source("HumanLootManager")
        self.assertGreaterEqual(loot.count("instanceof SoldierRecallCharmItem"), 3)
        self.assertIn("SoldierRecallProtection.pendingRescue(human)", source("HumanInventorySession"))
        self.assertIn("SoldierRecallProtection.pendingRescue(human)", source("HumanInventoryMenu"))
    def test_codec_rejects_bad_dimensions_and_coordinates(self):
        codec = source("SoldierRecallAnchor")
        for token in ("Tag.TAG_INT", "Tag.TAG_STRING", "ResourceLocation.tryParse", "29_999_984", ".immutable()"):
            self.assertIn(token, codec)
if __name__ == "__main__": unittest.main()
